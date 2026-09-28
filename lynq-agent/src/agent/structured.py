from __future__ import annotations

import logging
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

from agent.answer import text_of, unwrap
from llm.errors import retryable

log = logging.getLogger(__name__)

Answer = TypeVar("Answer", bound=BaseModel)


async def ask(
    model: Any,
    schema: type[Answer],
    messages: list,
    *,
    callbacks: list,
    retries: int,
) -> Answer:
    """One model call that answers with `schema`. The schema is bound as the only
    tool the model may call; when a model breaks that protocol and answers in text
    — Ollama does, now and then — the JSON is unwrapped from the text. A Bedrock
    error the service owns (throttling, a model that produced an invalid tool
    sequence) is retried; anything else is not."""
    structured = model.with_structured_output(schema, include_raw=True)
    attempts = 0
    while True:
        attempts += 1
        try:
            result = await structured.ainvoke(messages, config={"callbacks": callbacks})
        except Exception as exc:
            if attempts <= retries and retryable(exc):
                log.warning(
                    "message= The model failed and is retried, attempt=%s, %s",
                    attempts,
                    exc,
                )
                continue
            raise
        return _parsed(result, schema)


def _parsed(result: Any, schema: type[Answer]) -> Answer:
    raw = result.get("raw") if isinstance(result, dict) else result
    parsed = result.get("parsed") if isinstance(result, dict) else None

    if isinstance(parsed, schema):
        return parsed
    if isinstance(parsed, dict):
        try:
            return schema.model_validate(parsed)
        except ValidationError:
            log.warning("message= The structured answer did not validate as %s", schema.__name__)

    log.warning("message= The model did not answer with the %s tool", schema.__name__)
    return unwrap(text_of(raw), schema)
