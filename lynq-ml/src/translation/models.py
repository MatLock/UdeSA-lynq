"""Request/response schemas for the translate endpoint.

The resume payload reuses the ``Resume`` model produced by the parse-resume
endpoint, so translation is the natural next step in the pipeline.
"""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel

from resume_extractor.models import Resume


class Language(str, Enum):
    """Supported target languages.

    Mirrors ``com.lynq.backend.enums.Language`` in lynq-app-backend. Any value
    outside this set fails request validation (400).
    """

    EN = "EN"
    ES = "ES"
    FR = "FR"
    PR = "PR"


class TranslateRequest(BaseModel):
    """A resume plus the language every value should be translated into."""

    resume: Resume
    language: Language
