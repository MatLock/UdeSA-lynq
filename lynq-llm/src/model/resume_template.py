"""Request schema and template catalog for the resume-template endpoint."""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel

from model.resume_extractor import Resume


class Template(str, Enum):
    """Available visual templates, each backed by a folder under
    ``resources/resume_template/<name lowercased>/``.
    """

    MODERN = "MODERN"
    CLASSIC = "CLASSIC"


class ResumeTemplateCreationRequest(BaseModel):
    """Input for rendering a resume PDF and uploading it to S3.

    - ``resume_content``: the structured resume to render.
    - ``profile_url``: optional presigned URL to the profile photo; when absent
      the template renders without an avatar.
    - ``put_resume_url``: presigned S3 PUT URL the rendered PDF is uploaded to.
    - ``template``: which visual template to use (defaults to ``MODERN``).
    """

    resume_content: Resume
    profile_url: Optional[str] = None
    put_resume_url: str
    template: Template = Template.MODERN
