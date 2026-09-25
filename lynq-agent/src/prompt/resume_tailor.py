from __future__ import annotations

import hashlib
import json
import os
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from prompt import bare_language

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATE_DIR = os.path.join(_REPO_ROOT, "resources", "prompts", "resume_tailor")
FAMILY = "resume_tailor"
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


def _source(provider: str) -> str:
    with open(os.path.join(TEMPLATE_DIR, f"{provider}.jinja"), encoding="utf-8") as file:
        return file.read()


def reference(provider: str) -> str:
    digest = hashlib.sha256(_source(provider).encode("utf-8")).hexdigest()
    return f"{FAMILY}/{provider}@{digest[:HASH_LENGTH]}"


def render(
    provider: str,
    job: dict[str, Any],
    resume: dict[str, Any],
    language: str,
    resume_language: str,
    max_steps: int,
    turns_left: int,
) -> str:
    template = _environment.get_template(f"{provider}.jinja")
    return template.render(
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
        max_steps=max_steps,
        turns_left=turns_left,
    )
