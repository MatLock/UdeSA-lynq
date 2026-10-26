from __future__ import annotations

import json
import logging
import time

from langchain_core.messages import HumanMessage

from agent.context import CLARIFY_SPAN, SpanRecord, TurnContext, span_cost
from agent.intent import recent_exchange, text_of
from agent.state import TurnState
from config import get_settings
from db.models import SpanKind
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
    settings = get_settings()
    record = SpanRecord(
        step=state.next_step(),
        kind=SpanKind.ERROR if error is not None else SpanKind.LLM,
        name=CLARIFY_SPAN,
        input=json.dumps(
            {
                "message": context.message,
                "prompt": reference(provider),
                "model": settings.intent_model,
            },
            ensure_ascii=False,
        ),
        output=output,
        prompt_tokens=usage.get("input_tokens"),
        completion_tokens=usage.get("output_tokens"),
        latency_ms=int((time.monotonic() - started_at) * 1000),
        error=error,
    )
    if record.prompt_tokens or record.completion_tokens:
        record.cost_usd = span_cost(
            record, settings.intent_input_price_per_1m, settings.intent_output_price_per_1m
        )
    state.spans.append(record)


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
