from __future__ import annotations

import json
import logging
import re
import time
from itertools import dropwhile

from langchain_core.messages import HumanMessage

from agent.context import (
    DEFAULT_INTENT,
    INTENT_SPAN,
    Intent,
    SpanRecord,
    TurnContext,
)
from db.models import MessageRole, SpanKind
from llm.factory import build_model
from prompt.intent import reference, render

log = logging.getLogger(__name__)

EXCHANGE_MESSAGES = 4

_WORD = re.compile(rf"\b({Intent.ADVISE}|{Intent.EDIT})\b", re.IGNORECASE)


def recent_exchange(context: TurnContext) -> list[tuple[str, str]]:
    history = list(context.history)
    if history and history[-1] == (MessageRole.USER, context.message):
        history = history[:-1]

    history = list(dropwhile(lambda entry: entry[0] != MessageRole.ASSISTANT, history))
    return history[-EXCHANGE_MESSAGES:]


def read(answer: str) -> str | None:
    found = _WORD.search(answer or "")
    return found.group(1).lower() if found is not None else None


def _text_of(message) -> str:
    content = getattr(message, "content", message)
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return " ".join(
            part.get("text", "")
            for part in content
            if isinstance(part, dict)
        )
    return str(content)


def _span(
    context: TurnContext,
    provider: str,
    started_at: float,
    output: str,
    usage: dict,
    error: str | None = None,
) -> None:
    context.spans.append(
        SpanRecord(
            step=0,
            kind=SpanKind.ERROR if error is not None else SpanKind.LLM,
            name=INTENT_SPAN,
            input=json.dumps(
                {"message": context.message, "prompt": reference(provider)},
                ensure_ascii=False,
            ),
            output=output,
            prompt_tokens=usage.get("input_tokens"),
            completion_tokens=usage.get("output_tokens"),
            latency_ms=int((time.monotonic() - started_at) * 1000),
            error=error,
        )
    )


async def classify(context: TurnContext, provider: str, model=None) -> str:
    prompt = render(provider, recent_exchange(context), context.message)
    started_at = time.monotonic()

    try:
        answer = await (model if model is not None else build_model()).ainvoke(
            [HumanMessage(prompt)]
        )
    except Exception as exc:
        log.warning(
            "message= The intent step failed, falling back to %s, conversationId=%s, %s",
            DEFAULT_INTENT,
            context.conversation_id,
            exc,
        )
        _span(context, provider, started_at, DEFAULT_INTENT, {}, error=str(exc))
        return DEFAULT_INTENT

    text = _text_of(answer)
    intent = read(text)
    if intent is None:
        log.warning(
            "message= The intent step answered %r, falling back to %s, "
            "conversationId=%s",
            text[:80],
            DEFAULT_INTENT,
            context.conversation_id,
        )
        intent = DEFAULT_INTENT

    _span(
        context,
        provider,
        started_at,
        intent,
        getattr(answer, "usage_metadata", None) or {},
    )
    log.info(
        "message= Intent read, conversationId=%s, intent=%s",
        context.conversation_id,
        intent,
    )
    return intent
