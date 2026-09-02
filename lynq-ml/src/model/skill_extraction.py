"""Response schema for the resume skill-extraction endpoint.

The request body reuses the :class:`~model.resume_extractor.Resume` schema, so
only the response payload is defined here. It mirrors the JSON the LLM is asked
to return in ``resources/prompts/user_resume_skill_extraction/<provider>.jinja``.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class SkillExtractionResponse(BaseModel):
    """The skills extracted from a resume, bucketed by kind.

    ``similarity_tags`` is derived from the other three buckets and from what the
    candidate actually did: generalized capabilities ("Asynchronous Messaging"
    rather than Kafka or RabbitMQ), always written in English so a resume in one
    language can be matched against job postings in another.
    """

    skills: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    soft: list[str] = Field(default_factory=list)
    similarity_tags: list[str] = Field(default_factory=list)
