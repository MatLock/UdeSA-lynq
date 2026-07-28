"""Routes for auto-detecting the main language of a text (e.g. a resume)."""

from __future__ import annotations

import json
import logging
from typing import Annotated

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import ValidationError

from llm_client import get_llm_client
from model.language_detection import (
    LanguageDetectionRequest,
    LanguageDetectionResponse,
)
from prompt.language_detection import render_language_detection_prompt
from response import GlobalRestResponse

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s"


@router.post(
    "/detect-language",
    responses={
        502: {"description": "The upstream LLM request failed or returned malformed output."},
    },
)
async def detect_language(
    body: LanguageDetectionRequest,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    user_id: Annotated[str, Header(alias="user-id")],
) -> GlobalRestResponse[LanguageDetectionResponse]:
    """Detect the main language of the given text.

    The LLM classifies the text into one of the supported ``Language`` values
    (mirrors lynq-app-backend). Returns the detected language wrapped in the
    standard ``GlobalRestResponse`` envelope.
    """
    log.info("message= Started detect-language, " + _LOG_CONTEXT, user_id)

    client = get_llm_client()
    prompt = render_language_detection_prompt(client.provider, text=body.text)

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing detect-language, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    detected = _parse_llm_output(raw, user_id)

    log.info(
        "message= Finished detect-language, " + _LOG_CONTEXT + ", language=%s",
        user_id,
        detected.language.value,
    )
    return GlobalRestResponse(data=detected)


def _parse_llm_output(raw: str, user_id: str) -> LanguageDetectionResponse:
    """Parse and validate the model's JSON completion.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected ``{"language": "<CODE>"}`` shape.
    """
    try:
        return LanguageDetectionResponse.model_validate(json.loads(raw))
    except (json.JSONDecodeError, ValidationError) as exc:
        log.error(
            "message= Error when executing detect-language, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc
