from __future__ import annotations

import logging
import os
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from prompt import bare_language

log = logging.getLogger(__name__)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATE_DIR = os.path.join(_REPO_ROOT, "resources", "greetings")
DEFAULT_LANGUAGE = "en"
SUFFIX = ".jinja"

_environment = Environment(
    loader=FileSystemLoader(TEMPLATE_DIR),
    autoescape=select_autoescape(default=False, default_for_string=False),
    undefined=StrictUndefined,
    trim_blocks=True,
    lstrip_blocks=True,
)


def languages() -> set[str]:
    return {
        name[: -len(SUFFIX)]
        for name in os.listdir(TEMPLATE_DIR)
        if name.endswith(SUFFIX)
    }


def render(job_snapshot: dict[str, Any], language: str) -> str:
    code = bare_language(language)
    if code not in languages():
        log.warning(
            "message= No greeting template for %s, falling back to %s",
            language,
            DEFAULT_LANGUAGE,
        )
        code = DEFAULT_LANGUAGE

    template = _environment.get_template(f"{code}{SUFFIX}")
    return template.render(
        job={
            "title": job_snapshot.get("title") or "",
            "company": job_snapshot.get("company") or "",
        }
    ).strip()
