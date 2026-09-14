from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from model.resume import Resume


class JobSnapshot(BaseModel):
    id: str
    title: str
    description: str = ""
    company: str | None = None
    work_type: str | None = Field(default=None, alias="workType")
    skills: list[str] = Field(default_factory=list)
    similarity_tags: list[str] = Field(default_factory=list, alias="similarityTags")

    model_config = ConfigDict(populate_by_name=True)


class CreateConversationRequest(BaseModel):
    job: JobSnapshot
    base_resume_id: str = Field(alias="baseResumeId")
    base_resume: Resume = Field(alias="baseResume")
    score_before: int | None = Field(default=None, alias="scoreBefore")
    language: str = "es"

    model_config = ConfigDict(populate_by_name=True)


class CreateConversationResponse(BaseModel):
    conversation_id: str = Field(serialization_alias="conversationId")
    greeting: str
    status: str
    turns_left: int = Field(serialization_alias="turnsLeft")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class TurnRequest(BaseModel):
    message: str = Field(min_length=1)
    turn_key: str = Field(alias="turnKey")

    model_config = ConfigDict(populate_by_name=True)


class ResumeChange(BaseModel):
    section: str
    kind: str
    detail: str


class TurnResponse(BaseModel):
    reply: str
    resume: dict
    changes: list[ResumeChange] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    version: int
    status: str
    turns_left: int = Field(serialization_alias="turnsLeft")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class ConversationMessage(BaseModel):
    seq: int
    role: str
    content: str
    warnings: list[str] = Field(default_factory=list)


class ConversationVersion(BaseModel):
    version: int
    changes: list[ResumeChange] = Field(default_factory=list)
    is_current: bool = Field(serialization_alias="isCurrent")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class ConversationView(BaseModel):
    conversation_id: str = Field(serialization_alias="conversationId")
    status: str
    job_id: str = Field(serialization_alias="jobId")
    base_resume_id: str = Field(serialization_alias="baseResumeId")
    language: str
    messages: list[ConversationMessage] = Field(default_factory=list)
    current_resume: dict = Field(serialization_alias="currentResume")
    versions: list[ConversationVersion] = Field(default_factory=list)
    turns_left: int = Field(serialization_alias="turnsLeft")
    score_before: int | None = Field(default=None, serialization_alias="scoreBefore")
    score_after: int | None = Field(default=None, serialization_alias="scoreAfter")

    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class AppliedRequest(BaseModel):
    applied_resume_id: str = Field(alias="appliedResumeId")
    score_after: int | None = Field(default=None, alias="scoreAfter")

    model_config = ConfigDict(populate_by_name=True)


class AppliedResponse(BaseModel):
    status: str
