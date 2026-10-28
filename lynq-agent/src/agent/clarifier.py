from __future__ import annotations

import logging
import time

from langchain_core.messages import HumanMessage

from agent.context import CLARIFY_SPAN, TurnContext
from agent.intent import intent_model_span, recent_exchange, text_of
from agent.state import TurnState
from config import get_settings
from llm.factory import build_model
from prompt.clarify import reference, render

log = logging.getLogger(__name__)

NOTHING_ASKED = "The clarifying agent answered nothing"


def _span(
    context: TurnContext,
    state: TurnState,
    provider: str,
    started_at: float,
    output: str,
    usage: dict,
    error: str | None = None,
) -> None:
    state.spans.append(
        intent_model_span(
            context,
            state.next_step(),
            CLARIFY_SPAN,
            reference(provider),
            started_at,
            output,
            usage,
            error,
        )
    )


async def clarify(
    context: TurnContext, state: TurnState, provider: str, model=None
) -> str:
    prompt = render(provider, recent_exchange(context), context.message, context.language)
    started_at = time.monotonic()

    try:
        clarify_model = model if model is not None else build_model(
            model=get_settings().intent_model
        )
        answer = await clarify_model.ainvoke([HumanMessage(prompt)])
    except Exception as exc:
        _span(context, state, provider, started_at, "", {}, error=str(exc))
        raise

    question = text_of(answer).strip()
    usage = getattr(answer, "usage_metadata", None) or {}
    if not question:
        _span(context, state, provider, started_at, "", usage, error=NOTHING_ASKED)
        raise ValueError(NOTHING_ASKED)

    _span(context, state, provider, started_at, question, usage)
    log.info(
        "message= The candidate was asked what they meant, conversationId=%s",
        context.conversation_id,
    )
    return question
