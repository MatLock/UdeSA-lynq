"""Request/response schemas for the skill-enhance endpoint."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class WorkType(str, Enum):
    """Where the job is performed."""

    REMOTE = "REMOTE"
    IN_OFFICE = "IN_OFFICE"


class SkillEnhanceRequest(BaseModel):
    """Job posting to extract skills from."""

    title: str
    description: str
    work_type: WorkType


class SkillEnhanceResponse(BaseModel):
    """The extracted key skills plus their generalized capability tags.

    ``skills`` are the concrete skills the posting asks for, in the posting's own
    language. ``similarity_tags`` generalize them into transferable capabilities —
    always in English — so a candidate who solved the same problem with a substitute
    skill (RabbitMQ against a posting asking for Kafka) still matches. Defaulted to
    an empty list: a model that omits the field must not fail the request.
    """

    skills: list[str]
    similarity_tags: list[str] = Field(default_factory=list)
