"""Request/response schemas for the detect-language endpoint.

Reuses the ``Language`` enum from the translate endpoint so a detected language
can be fed straight into translation (e.g. auto-detect a resume's language,
then translate it).
"""

from __future__ import annotations

from pydantic import BaseModel

from model.translation import Language


class LanguageDetectionRequest(BaseModel):
    """Free-form text (e.g. a resume) whose main language should be detected."""

    text: str


class LanguageDetectionResponse(BaseModel):
    """The main language detected in the text.

    Constrained to the ``Language`` enum (mirrors lynq-app-backend); the model
    is instructed to pick the closest supported language.
    """

    language: Language