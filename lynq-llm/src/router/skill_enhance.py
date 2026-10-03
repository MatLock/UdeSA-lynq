"""Routes for extracting key skills from a job posting via an LLM."""

from __future__ import annotations

import json
import logging
from typing import Annotated, Optional

from fastapi import APIRouter, Depends, Header, HTTPException

from llm_client import LLMError, get_llm_client
from response import GlobalRestResponse
from security import CallerPrincipal, require_internal_caller

from model.skill_enhance import SkillEnhanceRequest, SkillEnhanceResponse
from prompt.skill_enhance import render_key_extractor_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# lynq-feeders scrapes postings on a schedule and has no user to speak for, so
# it reaches the same extraction through the internal router, which the shared
# lynq-internal-token guards instead of an access token.
internal_router = APIRouter(dependencies=[Depends(require_internal_caller)])

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, company_id=%s"

_RESPONSES = {
    502: {"description": "The upstream LLM request failed or returned malformed output."},
}


@router.post("/skill-enhance", responses=_RESPONSES)
async def skill_enhance(
    body: SkillEnhanceRequest,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    principal: CallerPrincipal,
    company_id: Annotated[Optional[str], Header(alias="company-id")] = None,
) -> GlobalRestResponse[SkillEnhanceResponse]:
    """Extract 5-15 key technical skills from a job posting."""
    return await _skill_enhance(body, principal.id, company_id)


@internal_router.post("/skill-enhance", responses=_RESPONSES)
async def skill_enhance_internally(
    body: SkillEnhanceRequest,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    user_id: Annotated[Optional[str], Header(alias="user-id")] = None,
    company_id: Annotated[Optional[str], Header(alias="company-id")] = None,
) -> GlobalRestResponse[SkillEnhanceResponse]:
    """Extract the same skills for a caller that holds the internal token.

    The ``user-id`` is log context only here: nothing on this route is scoped to
    a user, and the token — not the header — is what let the caller in.
    """
    return await _skill_enhance(body, user_id, company_id)


async def _skill_enhance(
    body: SkillEnhanceRequest, user_id: Optional[str], company_id: Optional[str]
) -> GlobalRestResponse[SkillEnhanceResponse]:
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
    except LLMError as exc:
        log.error(
            "message= Error when executing skill-enhance, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    try:
        completion = json.loads(raw)
        skills = completion["skills"]
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

    similarity_tags = _read_similarity_tags(completion, user_id, company_id)

    log.info(
        "message= Finished skill-enhance, "
        + _LOG_CONTEXT
        + ", skill_count=%s, similarity_tag_count=%s",
        user_id,
        company_id,
        len(skills),
        len(similarity_tags),
    )
    return GlobalRestResponse(
        data=SkillEnhanceResponse(skills=skills, similarity_tags=similarity_tags)
    )


def _read_similarity_tags(
    completion: dict, user_id: Optional[str], company_id: Optional[str]
) -> list[str]:
    """Read the generalized capability tags out of the model's completion.

    Unlike ``skills``, they are best-effort: they widen how a posting is matched
    against resumes, so a model that omits them — or returns them in the wrong
    shape — degrades the match instead of failing the request.
    """
    similarity_tags = completion.get("similarity_tags", [])
    if isinstance(similarity_tags, list) and all(
        isinstance(tag, str) for tag in similarity_tags
    ):
        return similarity_tags

    log.warning(
        "message= LLM returned malformed similarity_tags on skill-enhance, "
        "ignoring them, " + _LOG_CONTEXT,
        user_id,
        company_id,
    )
    return []
