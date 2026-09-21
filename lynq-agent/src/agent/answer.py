from __future__ import annotations

import json
import logging
import re
from typing import Any

from pydantic import BaseModel, Field, ValidationError

log = logging.getLogger(__name__)

_JSON_BLOCK = re.compile(r"\{.*\}", re.DOTALL)
_FENCE = re.compile(r"^```[a-zA-Z]*\s*|\s*```$")


class TurnAnswer(BaseModel):
    reply: str = Field(
        description="What you did and what you could not do, written to the candidate"
    )
    warnings: list[str] = Field(
        default_factory=list,
        description="What the candidate asked for that the resume does not back",
    )


def unwrap(text: str) -> TurnAnswer:
    stripped = _FENCE.sub("", (text or "").strip())
    block = _JSON_BLOCK.search(stripped)
    if block is not None:
        try:
            return TurnAnswer.model_validate(json.loads(block.group(0)))
        except (ValidationError, json.JSONDecodeError):
            log.warning("message= The model answered a JSON that is not a TurnAnswer")
    return TurnAnswer(reply=stripped)


def from_result(result: dict[str, Any]) -> TurnAnswer:
    structured = result.get("structured_response")
    if isinstance(structured, TurnAnswer):
        return structured
    if isinstance(structured, dict):
        try:
            return TurnAnswer.model_validate(structured)
        except ValidationError:
            log.warning("message= The structured response did not validate")

    log.warning("message= The model did not answer with a TurnAnswer tool call")
    return unwrap(_last_text(result))


def _last_text(result: dict[str, Any]) -> str:
    for message in reversed(result.get("messages") or []):
        content = getattr(message, "content", None)
        if isinstance(content, str) and content.strip():
            return content
        if isinstance(content, list):
            texts = [
                part.get("text")
                for part in content
                if isinstance(part, dict) and part.get("text")
            ]
            if texts:
                return "\n".join(texts)
    return ""
