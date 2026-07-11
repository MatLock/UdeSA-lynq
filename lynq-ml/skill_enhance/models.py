"""Request/response schemas for the skill-enhance endpoint."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel


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
    """The extracted key skills."""

    skills: list[str]