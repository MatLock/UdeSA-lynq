"""Routes for extracting a resume's skills into buckets via an LLM."""

from __future__ import annotations

import json
import logging
from typing import Annotated

from fastapi import APIRouter, Header, HTTPException
from pydantic import ValidationError

from llm_client import LLMError, get_llm_client
from response import GlobalRestResponse

from model.resume_extractor import Resume
from model.skill_extraction import SkillExtractionResponse
from prompt.skill_extraction import render_skill_extraction_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s"


@router.post(
    "/resume/skill-extraction",
    responses={
        502: {"description": "The upstream LLM request failed or returned malformed output."},
    },
)
async def extract_resume_skills(
    body: Resume,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    user_id: Annotated[str, Header(alias="user-id")],
    language: str | None = None,
) -> GlobalRestResponse[SkillExtractionResponse]:
    """Consolidate all of a resume's skills into technical/tools/soft buckets.

    Sends the structured resume to the configured LLM and returns the buckets
    wrapped in the standard ``GlobalRestResponse`` envelope. The soft skills are
    written in the ``language`` query parameter (the caller's UI language,
    English when omitted) — a resume drafted in one language must not force the
    skills into it. Technical and tool names are always kept verbatim.

    Alongside the buckets the model returns ``similarity_tags``: the same skills
    generalized into transferable capabilities ("Asynchronous Messaging" rather
    than Kafka or RabbitMQ), always in English so this resume can be matched
    against postings written in another language.
    """
    log.info(
        "message= Started resume skill-extraction, " + _LOG_CONTEXT,
        user_id,
    )

    client = get_llm_client()
    prompt = render_skill_extraction_prompt(
        client.provider, resume_json=body.model_dump_json(), language=language
    )

    try:
        raw = await client.generate(prompt)
    except LLMError as exc:
        log.error(
            "message= Error when executing resume skill-extraction, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    skills = _parse_llm_output(raw, user_id)

    log.info(
        "message= Finished resume skill-extraction, "
        + _LOG_CONTEXT
        + ", skill_count=%s, tool_count=%s, soft_count=%s, similarity_tag_count=%s",
        user_id,
        len(skills.skills),
        len(skills.tools),
        len(skills.soft),
        len(skills.similarity_tags),
    )
    return GlobalRestResponse(data=skills)


def _parse_llm_output(raw: str, user_id: str) -> SkillExtractionResponse:
    """Parse and validate the model's JSON completion into buckets.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected ``{ skills, tools, soft, similarity_tags }`` schema. Every field is
            defaulted, so a completion missing one still validates.
    """
    try:
        return SkillExtractionResponse.model_validate(json.loads(raw))
    except (json.JSONDecodeError, ValidationError) as exc:
        log.error(
            "message= Error when executing resume skill-extraction, "
            "LLM returned malformed output, " + _LOG_CONTEXT,
            user_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc
