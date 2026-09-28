from __future__ import annotations

import logging
from itertools import dropwhile

from langchain.agents import create_agent
from langchain.agents.middleware import ModelRetryMiddleware
from langchain_core.messages import AIMessage, HumanMessage

from agent.answer import TurnAnswer, from_result
from agent.callbacks import TraceCollector
from agent.context import (
    Intent,
    SpanRecord,
    TurnContext,
    TurnOutcome,
    build_turn_state,
    use_turn_state,
)
from agent.intent import classify
from agent.scope import SPAN_NAME, SPAN_REASON, enforce
from agent.tools import EDIT_TOOLS, find_evidence
from config import BEDROCK, OLLAMA, get_settings
from db.models import MessageRole, SpanKind
from llm.errors import retryable
from llm.factory import build_model
from prompt.notice import render as no_change_notice
from prompt.resume_tailor import reference, render

log = logging.getLogger(__name__)

RECURSION_HEADROOM = 6


def template_provider(provider: str) -> str:
    return BEDROCK if provider == BEDROCK else OLLAMA


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


def turn_messages(context: TurnContext) -> list:
    history = list(context.history)
    if history and history[-1] == (MessageRole.USER, context.message):
        history = history[:-1]

    history = list(dropwhile(lambda entry: entry[0] != MessageRole.USER, history))

    messages = [
        HumanMessage(content) if role == MessageRole.USER else AIMessage(content)
        for role, content in history
    ]
    messages.append(HumanMessage(context.message))
    return messages


async def run_turn(
    context: TurnContext, model=None, intent_model=None
) -> TurnOutcome:
    settings = get_settings()
    provider = template_provider(settings.llm_provider)
    intent = await classify(context, provider, intent_model)
    state = build_turn_state(context)

    system_prompt = render(
        provider,
        job=context.job_snapshot,
        resume=state.resume,
        language=context.language,
        resume_language=context.resume_language,
        max_steps=context.max_steps,
        max_edits=context.max_edits,
        turns_left=context.turns_left,
        intent=intent,
    )
    collector = TraceCollector(
        state, reference(provider), context.resume_version_id
    )
    agent = build_agent(system_prompt, intent, model)

    with use_turn_state(state):
        result = await agent.ainvoke(
            {"messages": turn_messages(context)},
            config={
                "callbacks": [collector],
                "recursion_limit": recursion_limit(context.max_steps),
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
        "message= Turn finished, conversationId=%s, intent=%s, steps=%s, edits=%s, "
        "warnings=%s",
        context.conversation_id,
        intent,
        state.steps,
        len(state.changes),
        len(answer.warnings),
    )
    return TurnOutcome(
        reply=answer.reply,
        resume=state.serialize_resume(),
        changes=state.changes,
        warnings=answer.warnings,
        spans=state.spans,
        intent=intent,
    )
