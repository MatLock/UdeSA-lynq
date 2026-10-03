"""Request/response schemas for the candidate-explanation endpoint."""

from __future__ import annotations

from pydantic import BaseModel

# Reuse the exact request payload of the upskilling-suggestion endpoint so both
# endpoints accept an identical ``{"job": ..., "candidate": ...}`` structure.
from model.upskilling_suggestion import (  # noqa: F401  (re-exported for callers)
    CandidateSpec,
    JobSpec,
    UpskillingRequest as CandidateExplanationRequest,
)


class CandidateExplanationResponse(BaseModel):
    """A hiring recommendation for the candidate against the job.

    ``recommendation`` is a short verdict label (e.g. "hire", "no_hire",
    "maybe"); ``explanation`` justifies it in prose, and the two lists break the
    reasoning down into the points for and against hiring the candidate.
    """

    recommendation: str
    explanation: str
    strengths: list[str]
    concerns: list[str]
