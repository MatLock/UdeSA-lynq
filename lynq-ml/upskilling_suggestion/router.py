"""Routes for suggesting upskilling courses from a job/candidate gap."""

from __future__ import annotations

import asyncio
import json
import logging

import httpx
from fastapi import APIRouter, Header, HTTPException

from llm_client import get_llm_client
from response import GlobalRestResponse
from udemy_client import get_course_provider

from .models import QuerySuggestion, UpskillingRequest, UpskillingResponse
from .prompts import render_upskilling_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, company_id=%s"


@router.post(
    "/upskilling_suggestion",
    response_model=GlobalRestResponse[UpskillingResponse],
)
async def upskilling_suggestion(
    body: UpskillingRequest,
    lynq_request_uuid: str = Header(alias="lynq-request-uuid"),
    user_id: str = Header(alias="user-id"),
    company_id: str = Header(alias="company-id"),
) -> GlobalRestResponse[UpskillingResponse]:
    """Assess a candidate against a job and return upskilling course links.

    The LLM produces a recruiter verdict plus 0-5 search queries for the
    candidate's missing competencies; each query is then resolved to Udemy
    courses. A perfect match yields the fixed outcome and no suggestions.
    """
    log.info(
        "message= Started upskilling-suggestion, " + _LOG_CONTEXT,
        user_id,
        company_id,
    )

    client = get_llm_client()
    input_json = json.dumps(body.model_dump(), ensure_ascii=False, indent=2)
    prompt = render_upskilling_prompt(client.provider, input_json=input_json)

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing upskilling-suggestion, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    outcome, queries = _parse_llm_output(raw, user_id, company_id)
    suggestions = await _collect_courses(queries, user_id, company_id)

    log.info(
        "message= Finished upskilling-suggestion, " + _LOG_CONTEXT + ", query_count=%s",
        user_id,
        company_id,
        len(queries),
    )
    return GlobalRestResponse(
        data=UpskillingResponse(outcome=outcome, suggestions=suggestions)
    )


def _parse_llm_output(raw: str, user_id: str, company_id: str) -> tuple[str, list[str]]:
    """Parse and validate the model's JSON completion.

    Returns:
        A ``(outcome, search_queries)`` pair.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected ``{"outcome": str, "search_queries": [str]}`` shape.
    """
    try:
        parsed = json.loads(raw)
        outcome = parsed["outcome"]
        queries = parsed["search_queries"]
        if not isinstance(outcome, str):
            raise TypeError("outcome is not a string")
        if not isinstance(queries, list) or not all(isinstance(q, str) for q in queries):
            raise TypeError("search_queries is not a list of strings")
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        log.error(
            "message= Error when executing upskilling-suggestion, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc
    return outcome, queries


async def _collect_courses(
    queries: list[str], user_id: str, company_id: str
) -> list[QuerySuggestion]:
    """Resolve each search query to Udemy courses, concurrently.

    Returns an empty list when there are no queries (perfect match) — the Udemy
    client is not even constructed in that case, so the endpoint works without
    Udemy credentials for a perfect match.

    Raises:
        HTTPException: 502 if any Udemy request fails.
    """
    if not queries:
        return []

    provider = get_course_provider()
    try:
        results = await asyncio.gather(
            *(provider.search_courses(query) for query in queries)
        )
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing upskilling-suggestion, course search failed, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail=f"Course search failed: {exc}"
        ) from exc

    return [
        QuerySuggestion(query=query, courses=courses)
        for query, courses in zip(queries, results)
    ]
