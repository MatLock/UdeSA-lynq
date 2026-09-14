from __future__ import annotations

import copy
import json
from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=select_autoescape(
        enabled_extensions=("html", "htm", "xml"),
        default_for_string=False,
    ),
    keep_trailing_newline=True,
)

GREETINGS = {
    "es": (
        "Miré el aviso de {title}{company}. ¿Armo una versión de tu CV apuntada "
        "a este puesto?"
    ),
    "en": (
        "I had a look at the {title}{company} posting. Want me to tailor your "
        "resume for it?"
    ),
}

_COMPANY_SUFFIX = {"es": " en {company}", "en": " at {company}"}


def render_greeting(language: str, *, title: str, company: str | None) -> str:
    lang = language if language in GREETINGS else "es"
    suffix = ""
    if company:
        suffix = _COMPANY_SUFFIX[lang].format(company=company)
    return GREETINGS[lang].format(title=title, company=suffix)


def without_personal_info(resume: dict) -> dict:
    stripped = copy.deepcopy(resume)
    stripped.pop("personal_info", None)
    return stripped


def render_system_prompt(
    provider: str,
    *,
    job: dict,
    resume: dict,
    language: str,
    turns_left: int,
) -> str:
    template = _env.get_template(f"resume_tailor/{provider}.jinja")
    return template.render(
        job_title=job.get("title") or "",
        job_company=job.get("company") or "",
        job_work_type=job.get("work_type") or "",
        job_description=job.get("description") or "",
        job_skills=", ".join(job.get("skills") or []),
        resume_json=json.dumps(
            without_personal_info(resume), ensure_ascii=False, indent=2
        ),
        language=language,
        turns_left=turns_left,
        is_last_turn=turns_left <= 1,
    )
