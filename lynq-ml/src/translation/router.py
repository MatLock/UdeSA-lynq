"""Routes for translating a structured resume into a target language via an LLM."""

from __future__ import annotations

import json
import logging

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import ValidationError

from llm_client import get_llm_client
from resume_extractor.models import Resume
from response import GlobalRestResponse

from .models import TranslateRequest
from .prompts import render_translation_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, language=%s"


@router.post("/translate", response_model=GlobalRestResponse[Resume])
async def translate(
    body: TranslateRequest,
    lynq_request_uuid: str = Header(alias="lynq-request-uuid"),
    user_id: str = Header(alias="user-id"),
) -> GlobalRestResponse[Resume]:
    """Translate every value of a resume JSON into the requested language.

    The ``language`` field is validated against the ``Language`` enum (mirrors
    lynq-app-backend); any unsupported language fails validation with a 400.
    Returns the translated resume wrapped in the standard ``GlobalRestResponse``
    envelope.
    """
    log.info(
        "message= Started translate, " + _LOG_CONTEXT,
        user_id,
        body.language.value,
    )

    client = get_llm_client()
    resume_json = json.dumps(body.resume.model_dump(), ensure_ascii=False, indent=2)
    prompt = render_translation_prompt(
        client.provider, resume_json=resume_json, language=body.language
    )

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing translate, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            body.language.value,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    translated = _parse_llm_output(raw, user_id, body.language.value)

    log.info(
        "message= Finished translate, " + _LOG_CONTEXT,
        user_id,
        body.language.value,
    )
    return GlobalRestResponse(data=translated)


def _parse_llm_output(raw: str, user_id: str, language: str) -> Resume:
    """Parse and validate the model's JSON completion into a ``Resume``.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected resume schema.
    """
    try:
        return Resume.model_validate(json.loads(raw))
    except (json.JSONDecodeError, ValidationError) as exc:
        log.error(
            "message= Error when executing translate, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            language,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc
