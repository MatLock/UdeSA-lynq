from __future__ import annotations

import logging

from langchain.agents import create_agent
from langchain.agents.middleware import ModelRetryMiddleware

from agent.callbacks import TraceCollector
from agent.context import Intent, SpanRecord, TurnContext, TurnOutcome
from agent.intent import classify
from agent.turn import template_provider, turn_messages
from config import get_settings
from db.models import SpanKind
from evaluation.arms.react.answer import TurnAnswer, from_result
from evaluation.arms.react.prompt import reference, render
from evaluation.arms.react.scope import SPAN_NAME, SPAN_REASON, enforce
from evaluation.arms.react.state import DEFAULT_MAX_EDITS, build_turn_state, use_turn_state
from evaluation.arms.react.tools import EDIT_TOOLS, find_evidence
from llm.errors import retryable
from llm.factory import build_model
from prompt.notice import render as no_change_notice

log = logging.getLogger(__name__)

RECURSION_HEADROOM = 6


def recursion_limit(max_steps: int) -> int:
    return 2 * max_steps + RECURSION_HEADROOM


def retry_middleware() -> ModelRetryMiddleware:
    return ModelRetryMiddleware(
        max_retries=get_settings().model_retries,
        retry_on=retryable,
        on_failure="error",
    )


def tools_for(intent: str) -> list:
    if intent == Intent.ADVISE:
        return [find_evidence]
    return [find_evidence, *EDIT_TOOLS]


def build_agent(system_prompt: str, intent: str, model=None):
    return create_agent(
        model=model if model is not None else build_model(),
        tools=tools_for(intent),
        system_prompt=system_prompt,
        response_format=TurnAnswer,
        middleware=[retry_middleware()],
    )


def react_intent(intent: str) -> str:
    """The ReAct prompt knew two intents. The shared intent agent now answers a
    third, `unknown`, which this design never had a branch for: it falls back to
    `edit`, the default the loop always had when the intent could not be read."""
    return Intent.ADVISE if intent == Intent.ADVISE else Intent.EDIT


async def run_turn(
    context: TurnContext,
    model=None,
    intent_model=None,
    max_steps: int | None = None,
    max_edits: int = DEFAULT_MAX_EDITS,
) -> TurnOutcome:
    """One turn of the ReAct baseline: the intent agent reads the message, then
    one agent loops over the tools until it calls `TurnAnswer` or runs out of
    steps. The tools write into the resume as they are called; nothing is
    judged after the fact."""
    settings = get_settings()
    provider = template_provider(settings.llm_provider)
    intent = react_intent(await classify(context, provider, intent_model))
    steps = max_steps if max_steps is not None else settings.max_steps
    state = build_turn_state(context, max_steps=steps, max_edits=max_edits)

    system_prompt = render(
        provider,
        job=context.job_snapshot,
        resume=state.resume,
        language=context.language,
        resume_language=context.resume_language,
        max_steps=steps,
        max_edits=max_edits,
        turns_left=context.turns_left,
        intent=intent,
    )
    collector = TraceCollector(state, reference(provider), context.resume_version_id)
    agent = build_agent(system_prompt, intent, model)

    with use_turn_state(state):
        result = await agent.ainvoke(
            {"messages": turn_messages(context)},
            config={
                "callbacks": [collector],
                "recursion_limit": recursion_limit(steps),
            },
        )

    raw_answer = from_result(result)
    answer = enforce(raw_answer, system_prompt, context.language)
    if answer is not raw_answer:
        state.spans.append(
            SpanRecord(
                step=state.steps,
                kind=SpanKind.ERROR,
                name=SPAN_NAME,
                output=answer.reply,
                error=SPAN_REASON,
            )
        )
    elif intent == Intent.EDIT and not state.changes:
        answer = answer.model_copy(
            update={"warnings": [*answer.warnings, no_change_notice(context.language)]}
        )
    log.info(
        "message= ReAct turn finished, conversationId=%s, intent=%s, steps=%s, edits=%s",
        context.conversation_id,
        intent,
        state.steps,
        len(state.changes),
    )
    return TurnOutcome(
        reply=answer.reply,
        resume=state.serialize_resume(),
        changes=state.changes,
        warnings=answer.warnings,
        spans=state.spans,
        intent=intent,
    )
