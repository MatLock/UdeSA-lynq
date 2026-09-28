from __future__ import annotations

import logging
from itertools import dropwhile

from langchain_core.messages import AIMessage, HumanMessage

from agent.advisor import advise
from agent.callbacks import TraceCollector
from agent.context import Intent, TurnContext, TurnOutcome
from agent.editor import edit
from agent.intent import classify
from agent.state import build_turn_state
from config import BEDROCK, OLLAMA, get_settings
from db.models import MessageRole
from llm.factory import build_model
from prompt.notice import render as no_change_notice
from prompt.tailor import ADVISE, EDIT, reference

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


async def run_turn(context: TurnContext, model=None, intent_model=None) -> TurnOutcome:
    """One turn: the intent agent reads the message, then either the advising
    agent answers or the editing agent proposes and the guard applies."""
    settings = get_settings()
    provider = template_provider(settings.llm_provider)
    intent = await classify(context, provider, intent_model)
    state = build_turn_state(context)
    family = ADVISE if intent == Intent.ADVISE else EDIT
    collector = TraceCollector(state, reference(family), context.resume_version_id)
    call = dict(
        provider=provider,
        callbacks=[collector],
        retries=settings.model_retries,
        messages=turn_messages(context),
    )
    agent_model = model if model is not None else build_model()

    if intent == Intent.ADVISE:
        advice = await advise(context, state, agent_model, **call)
        reply, warnings = advice.reply, list(advice.warnings)
        recommendations = [r.model_dump() for r in advice.recommendations]
    else:
        proposal, _ = await edit(context, state, agent_model, **call)
        reply, warnings = proposal.reply, list(proposal.warnings)
        recommendations = []
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
    )
