from __future__ import annotations

import logging
from itertools import dropwhile

from langchain_core.messages import AIMessage, HumanMessage

from agent.callbacks import TraceCollector
from agent.context import Intent, TurnContext, TurnOutcome
from agent.graph import RECURSION_LIMIT, TURN_GRAPH, PromptReference
from agent.state import build_turn_state
from config import BEDROCK, OLLAMA, get_settings
from db.models import MessageRole
from llm.factory import build_model
from prompt.notice import render as no_change_notice
from prompt.rejection import render as rejection_notice

log = logging.getLogger(__name__)


def template_provider(provider: str) -> str:
    return BEDROCK if provider == BEDROCK else OLLAMA


def turn_messages(context: TurnContext) -> list:
    """The exchange as the model reads it: the last messages, trimmed to start on
    a user turn — Bedrock rejects a conversation that opens on an assistant one —
    and ending on the message of this turn."""
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
    context: TurnContext, model=None, intent_model=None, judge_model=None
) -> TurnOutcome:
    """One turn through the graph: the intent agent reads the message, then
    either the advising agent answers or the editing agent proposes, the judge
    decides part by part and the code applies what it approved, with one
    correction pass when something is rejected. A scripted `model` serves the
    judge too, so a test scripts both answers in order."""
    settings = get_settings()
    state = build_turn_state(context)
    prompt = PromptReference()
    collector = TraceCollector(state, prompt, context.resume_version_id)

    result = await TURN_GRAPH.ainvoke(
        {
            "context": context,
            "state": state,
            "provider": template_provider(settings.llm_provider),
            "model": model if model is not None else build_model(),
            "intent_model": intent_model,
            "judge_model": judge_model if judge_model is not None else (
                None if model is not None else build_model(model=settings.judge_model)
            ),
            "callbacks": [collector],
            "retries": settings.model_retries,
            "messages": turn_messages(context),
            "prompt": prompt,
        },
        config={"recursion_limit": RECURSION_LIMIT},
    )

    intent = result["intent"]
    if intent == Intent.ADVISE:
        advice = result["advice"]
        reply, warnings = advice.reply, list(advice.warnings)
        recommendations = [r.model_dump() for r in advice.recommendations]
    else:
        proposal = result["proposal"]
        reply, warnings = proposal.reply, list(proposal.warnings)
        recommendations = []
        # The reply is the model's; the document is the guard's. When they differ,
        # the candidate is told which parts stayed out, so the chat never promises
        # what the resume beside it does not say.
        if result.get("rejections"):
            warnings.append(rejection_notice(context.language, result["rejections"]))
        if not state.changes:
            warnings.append(no_change_notice(context.language))

    log.info(
        "message= Turn finished, conversationId=%s, intent=%s, steps=%s, edits=%s, "
        "warnings=%s",
        context.conversation_id,
        intent,
        state.steps,
        len(state.changes),
        len(warnings),
    )
    return TurnOutcome(
        reply=reply,
        resume=state.serialize_resume(),
        changes=state.changes,
        warnings=warnings,
        spans=state.spans,
        intent=intent,
        recommendations=recommendations,
        confirmed=state.confirmed,
    )
