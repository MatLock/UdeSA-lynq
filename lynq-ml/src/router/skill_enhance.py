"""Routes for extracting key skills from a job posting via an LLM."""

from __future__ import annotations

import json
import logging

import httpx
from fastapi import APIRouter, Header, HTTPException

from llm_client import get_llm_client
from response import GlobalRestResponse

from model.skill_enhance import SkillEnhanceRequest, SkillEnhanceResponse
from prompt.skill_enhance import render_key_extractor_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, company_id=%s"


@router.post("/skill-enhance", response_model=GlobalRestResponse[SkillEnhanceResponse])
async def skill_enhance(
    body: SkillEnhanceRequest,
    lynq_request_uuid: str = Header(alias="lynq-request-uuid"),
    user_id: str = Header(alias="user-id"),
    company_id: str = Header(alias="company-id"),
) -> GlobalRestResponse[SkillEnhanceResponse]:
    """Extract 5-15 key technical skills from a job posting."""
    log.info(
        "message= Started skill-enhance, " + _LOG_CONTEXT,
        user_id,
        company_id,
    )

    client = get_llm_client()
    prompt = render_key_extractor_prompt(
        client.provider,
        job_title=body.title,
        work_type=body.work_type.value,
        job_description=body.description,
    )

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing skill-enhance, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    try:
        skills = json.loads(raw)["skills"]
        if not isinstance(skills, list) or not all(isinstance(s, str) for s in skills):
            raise TypeError("skills is not a list of strings")
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        log.error(
            "message= Error when executing skill-enhance, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail="LLM returned malformed output") from exc

    log.info(
        "message= Finished skill-enhance, " + _LOG_CONTEXT + ", skill_count=%s",
        user_id,
        company_id,
        len(skills),
    )
    return GlobalRestResponse(data=SkillEnhanceResponse(skills=skills))