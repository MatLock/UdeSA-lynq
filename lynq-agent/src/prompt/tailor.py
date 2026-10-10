from __future__ import annotations

import hashlib
import json
import os
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from prompt import bare_language

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATE_DIR = os.path.join(_REPO_ROOT, "resources", "prompts")
EDIT = "edit"
ADVISE = "advise"
JUDGE = "judge"
FAMILIES = (EDIT, ADVISE, JUDGE)
LANGUAGE_NAMES = {"en": "English", "es": "Spanish", "pt": "Portuguese"}
HASH_LENGTH = 12

_environment = Environment(
    loader=FileSystemLoader(TEMPLATE_DIR),
    autoescape=select_autoescape(default=False, default_for_string=False),
    undefined=StrictUndefined,
    trim_blocks=True,
    lstrip_blocks=True,
    keep_trailing_newline=True,
)


def language_name(code: str) -> str:
    bare = bare_language(code)
    return f"{LANGUAGE_NAMES.get(bare, bare)} ({bare})"


def _source(family: str) -> str:
    with open(os.path.join(TEMPLATE_DIR, f"{family}.jinja"), encoding="utf-8") as file:
        return file.read()


def reference(family: str) -> str:
    digest = hashlib.sha256(_source(family).encode("utf-8")).hexdigest()
    return f"{family}@{digest[:HASH_LENGTH]}"


def render(
    family: str,
    *,
    provider: str,
    job: dict[str, Any],
    resume: dict[str, Any],
    language: str,
    resume_language: str,
    turns_left: int,
    recommendations: list[dict[str, Any]] | None = None,
    statements: list[str] | None = None,
    gaps: list[str] | None = None,
) -> str:
    template = _environment.get_template(f"{family}.jinja")
    return template.render(
        provider=provider,
        job={
            "title": job.get("title") or "",
            "company": job.get("company") or "",
            "work_type": job.get("workType") or "",
            "description": job.get("description") or "",
        },
        extracted_skills=job.get("extractedSkills") or job.get("skills") or [],
        resume=json.dumps(resume, ensure_ascii=False, indent=2),
        language=language_name(language),
        resume_language=language_name(resume_language),
        turns_left=turns_left,
        recommendations=recommendations or [],
        statements=statements or [],
        gaps=gaps or [],
    )


def render_judge(
    *,
    provider: str,
    resume: dict[str, Any],
    job_skills: list[str],
    language: str,
    resume_language: str,
    parts: list,
    statements: list[str] | None = None,
    asked: str = "",
    message: str = "",
) -> str:
    template = _environment.get_template(f"{JUDGE}.jinja")
    return template.render(
        provider=provider,
        resume=json.dumps(
            {key: value for key, value in resume.items() if key != "personal_info"},
            ensure_ascii=False,
            indent=2,
        ),
        job_skills=job_skills,
        language=language_name(language),
        resume_language=language_name(resume_language),
        parts=parts,
        statements=statements or [],
        asked=asked,
        message=message,
    )
