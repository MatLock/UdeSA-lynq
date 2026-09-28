from __future__ import annotations

import json
import logging
import re
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

log = logging.getLogger(__name__)

_JSON_BLOCK = re.compile(r"\{.*\}", re.DOTALL)
_FENCE = re.compile(r"(?:^```[a-zA-Z]*\s*)|(?:\s*```$)")

Answer = TypeVar("Answer", bound=BaseModel)


def text_of(message: Any) -> str:
    content = getattr(message, "content", message)
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(
            part.get("text")
            for part in content
            if isinstance(part, dict) and part.get("text")
        )
    return "" if content is None else str(content)


def unwrap(text: str, schema: type[Answer]) -> Answer:
    """A model that answers in text instead of the schema often pastes the JSON it
    was asked for. Unwrap it, or the candidate reads raw JSON in the chat; plain
    prose becomes the reply as it is."""
    stripped = _FENCE.sub("", (text or "").strip())
    block = _JSON_BLOCK.search(stripped)
    if block is not None:
        try:
            return schema.model_validate(json.loads(block.group(0)))
        except (ValidationError, json.JSONDecodeError):
            log.warning("message= The model answered a JSON that is not a %s", schema.__name__)
    return schema(reply=stripped)
