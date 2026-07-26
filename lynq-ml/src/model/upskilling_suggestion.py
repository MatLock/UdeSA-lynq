"""Request/response schemas for the upskilling-suggestion endpoint."""

from __future__ import annotations

from pydantic import BaseModel

from udemy_client import Course


class JobSpec(BaseModel):
    """The job the candidate is being evaluated against."""

    description: str
    skills: list[str]


class CandidateSpec(BaseModel):
    """The candidate under evaluation."""

    description: str
    skills: list[str]


class UpskillingRequest(BaseModel):
    """A job + candidate pair to compare.

    Serializes to exactly the ``{"job": ..., "candidate": ...}`` JSON structure
    the ``upskilling_suggestion`` prompt templates expect as input.
    """

    job: JobSpec
    candidate: CandidateSpec


class QuerySuggestion(BaseModel):
    """One missing competency and the Udemy courses that address it."""

    query: str
    courses: list[Course]


class UpskillingResponse(BaseModel):
    """The recruiter verdict plus course links grouped by search query.

    ``reasons`` lists the concrete gaps that keep the candidate from being a
    perfect match — one short reason per entry — so the caller can show *why*
    rather than a bare "not a perfect match". When the candidate is a perfect
    match, ``outcome`` is the fixed "You are perfect for this role." string and
    both ``reasons`` and ``suggestions`` are empty.
    """

    outcome: str
    reasons: list[str] = []
    suggestions: list[QuerySuggestion]
