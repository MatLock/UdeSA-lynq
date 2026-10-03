"""Request/response schemas for the parse-resume endpoint.

The response mirrors the JSON schema described in
``resources/prompts/resume_extractor/<provider>.jinja``. Every field is
defaulted so that a partial completion from the model still validates instead
of failing the whole request.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class ParseResumeRequest(BaseModel):
    """A presigned URL pointing to the resume document to parse."""

    # The lynq clients send camelCase; keep the Python attribute snake_case.
    pre_signed_url: str = Field(alias="preSignedUrl")

    model_config = ConfigDict(populate_by_name=True)


class Links(BaseModel):
    """Public profile links found in the resume."""

    linkedin: str | None = None
    github: str | None = None
    portfolio: str | None = None
    website: str | None = None


class PersonalInfo(BaseModel):
    """Contact and identity block."""

    full_name: str | None = None
    headline: str | None = None
    email: str | None = None
    phone: str | None = None
    location: str | None = None
    links: Links = Field(default_factory=Links)


class WorkExperience(BaseModel):
    """A single professional role."""

    company: str | None = None
    position: str | None = None
    location: str | None = None
    start_date: str | None = None
    end_date: str | None = None
    is_current: bool = False
    description: str | None = None
    achievements: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)


class Education(BaseModel):
    """A single education entry."""

    institution: str | None = None
    degree: str | None = None
    field_of_study: str | None = None
    start_date: str | None = None
    end_date: str | None = None
    is_current: bool = False
    description: str | None = None


class Skills(BaseModel):
    """Skills bucketed by kind."""

    technical: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    soft: list[str] = Field(default_factory=list)


class Language(BaseModel):
    """A spoken/written language and its proficiency."""

    language: str | None = None
    proficiency: str | None = None


class Certification(BaseModel):
    """A professional certification."""

    name: str | None = None
    issuer: str | None = None
    issue_date: str | None = None
    credential_id: str | None = None


class Project(BaseModel):
    """A personal or professional project."""

    name: str | None = None
    description: str | None = None
    technologies: list[str] = Field(default_factory=list)
    url: str | None = None


class Resume(BaseModel):
    """The structured resume extracted from the document text."""

    personal_info: PersonalInfo = Field(default_factory=PersonalInfo)
    summary: str | None = None
    work_experience: list[WorkExperience] = Field(default_factory=list)
    education: list[Education] = Field(default_factory=list)
    skills: Skills = Field(default_factory=Skills)
    languages: list[Language] = Field(default_factory=list)
    certifications: list[Certification] = Field(default_factory=list)
    projects: list[Project] = Field(default_factory=list)