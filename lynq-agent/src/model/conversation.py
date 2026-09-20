from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

_CAMEL = ConfigDict(populate_by_name=True)


class JobPayload(BaseModel):
    model_config = _CAMEL

    id: str
    title: str
    description: str = ""
    company: str | None = None
    work_type: str | None = Field(default=None, alias="workType")
    skills: list[str] = Field(default_factory=list)


class CreateConversationRequest(BaseModel):
    model_config = _CAMEL

    job: JobPayload
    base_resume_id: str = Field(alias="baseResumeId")
    base_resume: dict[str, Any] = Field(alias="baseResume")
    language: str
    resume_language: str = Field(alias="resumeLanguage")


class CreateConversationResponse(BaseModel):
    model_config = _CAMEL

    conversation_id: str = Field(alias="conversationId")
    greeting: str
    status: str


class TurnRequest(BaseModel):
    model_config = _CAMEL

    message: str = Field(min_length=1)
    turn_key: str = Field(alias="turnKey", min_length=1)


class ResumeChange(BaseModel):
    model_config = _CAMEL

    section: str
    kind: str
    detail: str


class TurnResponse(BaseModel):
    model_config = _CAMEL

    reply: str
    resume: dict[str, Any]
    changes: list[ResumeChange] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    version: int
    status: str
    turns_left: int = Field(alias="turnsLeft")


class MessageView(BaseModel):
    model_config = _CAMEL

    seq: int
    role: str
    content: str
    warnings: list[str] = Field(default_factory=list)
    created_on: datetime = Field(alias="createdOn")


class VersionView(BaseModel):
    model_config = _CAMEL

    version: int
    is_current: bool = Field(alias="isCurrent")
    changes: list[ResumeChange] = Field(default_factory=list)
    created_on: datetime = Field(alias="createdOn")


class ConversationView(BaseModel):
    model_config = _CAMEL

    conversation_id: str = Field(alias="conversationId")
    status: str
    turn_count: int = Field(alias="turnCount")
    turns_left: int = Field(alias="turnsLeft")
    messages: list[MessageView] = Field(default_factory=list)
    current_resume: dict[str, Any] = Field(alias="currentResume")
    versions: list[VersionView] = Field(default_factory=list)


class AppliedRequest(BaseModel):
    model_config = _CAMEL

    applied_resume_id: str = Field(alias="appliedResumeId", min_length=1)


class AppliedResponse(BaseModel):
    model_config = _CAMEL

    status: str
