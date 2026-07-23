"""Routes for parsing a resume document into structured JSON via an LLM."""

from __future__ import annotations

import json
import logging

import httpx
from fastapi import APIRouter, Header, HTTPException
from fastapi.concurrency import run_in_threadpool
from pydantic import ValidationError

from file_reader.resume_reader import read_resume
from llm_client import get_llm_client
from response import GlobalRestResponse

from .models import ParseResumeRequest, Resume
from .prompts import render_resume_extractor_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s"


@router.post("/parse-resume", response_model=GlobalRestResponse[Resume])
async def parse_resume(
    body: ParseResumeRequest,
    lynq_request_uuid: str = Header(alias="lynq-request-uuid"),
    user_id: str = Header(alias="user-id"),
) -> GlobalRestResponse[Resume]:
    """Download a resume, extract its text, and structure it into JSON.

    Fetches the document from the presigned URL, extracts its plain text, sends
    it to the configured LLM, and returns the parsed CV wrapped in the standard
    ``GlobalRestResponse`` envelope.
    """
    log.info(
        "message= Started parse-resume, " + _LOG_CONTEXT,
        user_id,
    )

    # read_resume performs blocking network + document I/O; keep it off the
    # event loop so concurrent requests are not stalled.
    try:
        resume_text = await run_in_threadpool(read_resume, body.pre_signed_url)
    except ValueError as exc:
        log.error(
            "message= Error when executing parse-resume, unsupported document, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 - surface any download/parse failure
        log.error(
            "message= Error when executing parse-resume, could not read resume, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail=f"Could not read resume: {exc}"
        ) from exc

    client = get_llm_client()
    prompt = render_resume_extractor_prompt(client.provider, resume_text=resume_text)

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing parse-resume, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    resume = _parse_llm_output(raw, user_id)

    log.info(
        "message= Finished parse-resume, " + _LOG_CONTEXT,
        user_id,
    )
    return GlobalRestResponse(data=resume)


def _parse_llm_output(raw: str, user_id: str) -> Resume:
    """Parse and validate the model's JSON completion into a ``Resume``.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected resume schema.
    """
    try:
        return Resume.model_validate(json.loads(raw))
    except (json.JSONDecodeError, ValidationError) as exc:
        log.error(
            "message= Error when executing parse-resume, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc