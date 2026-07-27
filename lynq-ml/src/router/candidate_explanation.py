"""Routes for explaining why a candidate should (or should not) be hired."""

from __future__ import annotations

import json
import logging
import re
from typing import Annotated

import httpx
from fastapi import APIRouter, Header, HTTPException

from llm_client import get_llm_client
from response import GlobalRestResponse

from model.candidate_explanation import (
    CandidateExplanationRequest,
    CandidateExplanationResponse,
)
from prompt.candidate_explanation import render_candidate_explanation_prompt

log = logging.getLogger(__name__)

router = APIRouter()

# The lynq-request-uuid is rendered by the log formatter (MDC-style), so it is
# not repeated here — only the request-specific identifiers the format omits.
_LOG_CONTEXT = "user_id=%s, company_id=%s"

# The recommendation is a language-independent machine key: the client keys both
# its localized label and the badge color on it, so it must stay one of these
# three literals regardless of the evaluation's language. The model is asked to
# emit them verbatim, but for a non-English evaluation it sometimes returns a
# human-readable label instead ("Not recommended", "No se recomienda"); map the
# known variants back to the canonical key so the client can always localize it.
_CANONICAL_RECOMMENDATIONS = frozenset({"hire", "no_hire", "maybe"})
_RECOMMENDATION_ALIASES = {
    # English human-readable labels.
    "recommended": "hire",
    "recommended_to_hire": "hire",
    "not_recommended": "no_hire",
    "consider_with_caution": "maybe",
    # Spanish human-readable labels.
    "se_recomienda_contratar": "hire",
    "recomendado": "hire",
    "contratar": "hire",
    "no_se_recomienda": "no_hire",
    "no_recomendado": "no_hire",
    "considerar_con_cautela": "maybe",
}


def _normalize_recommendation(recommendation: str) -> str:
    """Snap the model's recommendation to a canonical ``hire|no_hire|maybe`` key.

    Slugifies the value (lowercase, spaces/hyphens to underscores) and, when it
    is not already canonical, resolves it through the alias table. Unknown values
    are returned as their slug so the client falls back to showing them verbatim
    rather than losing the recommendation entirely.
    """
    slug = re.sub(r"[\s-]+", "_", recommendation.strip().lower())
    if slug in _CANONICAL_RECOMMENDATIONS:
        return slug
    return _RECOMMENDATION_ALIASES.get(slug, slug)


@router.post(
    "/candidate-explanation",
    responses={
        502: {"description": "The upstream LLM request failed or returned malformed output."},
    },
)
async def candidate_explanation(
    body: CandidateExplanationRequest,
    lynq_request_uuid: Annotated[str, Header(alias="lynq-request-uuid")],
    user_id: Annotated[str, Header(alias="user-id")],
    company_id: Annotated[str, Header(alias="company-id")],
) -> GlobalRestResponse[CandidateExplanationResponse]:
    """Assess a candidate against a job and explain the hiring decision.

    Takes the same ``{"job": ..., "candidate": ...}`` payload as the
    upskilling-suggestion endpoint and returns a recruiter recommendation with
    the reasons for and against hiring the candidate.
    """
    log.info(
        "message= Started candidate-explanation, " + _LOG_CONTEXT,
        user_id,
        company_id,
    )

    client = get_llm_client()
    input_json = json.dumps(body.model_dump(), ensure_ascii=False, indent=2)
    prompt = render_candidate_explanation_prompt(client.provider, input_json=input_json)

    try:
        raw = await client.generate(prompt)
    except httpx.HTTPError as exc:
        log.error(
            "message= Error when executing candidate-explanation, LLM request failed, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    explanation = _parse_llm_output(raw, user_id, company_id)

    log.info(
        "message= Finished candidate-explanation, " + _LOG_CONTEXT,
        user_id,
        company_id,
    )
    return GlobalRestResponse(data=explanation)


def _parse_llm_output(
    raw: str, user_id: str, company_id: str
) -> CandidateExplanationResponse:
    """Parse and validate the model's JSON completion.

    Returns:
        The populated ``CandidateExplanationResponse``.

    Raises:
        HTTPException: 502 if the output is not JSON or does not match the
            expected ``{"recommendation": str, "explanation": str,
            "strengths": [str], "concerns": [str]}`` shape.
    """
    try:
        parsed = json.loads(raw)
        recommendation = parsed["recommendation"]
        explanation = parsed["explanation"]
        strengths = parsed["strengths"]
        concerns = parsed["concerns"]
        if not isinstance(recommendation, str):
            raise TypeError("recommendation is not a string")
        if not isinstance(explanation, str):
            raise TypeError("explanation is not a string")
        if not isinstance(strengths, list) or not all(
            isinstance(s, str) for s in strengths
        ):
            raise TypeError("strengths is not a list of strings")
        if not isinstance(concerns, list) or not all(
            isinstance(c, str) for c in concerns
        ):
            raise TypeError("concerns is not a list of strings")
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        log.error(
            "message= Error when executing candidate-explanation, LLM returned malformed output, "
            + _LOG_CONTEXT,
            user_id,
            company_id,
            exc_info=exc,
        )
        raise HTTPException(
            status_code=502, detail="LLM returned malformed output"
        ) from exc
    return CandidateExplanationResponse(
        recommendation=_normalize_recommendation(recommendation),
        explanation=explanation,
        strengths=strengths,
        concerns=concerns,
    )
