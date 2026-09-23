from __future__ import annotations

import logging
import os

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from prompt import bare_language

log = logging.getLogger(__name__)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATE_DIR = os.path.join(_REPO_ROOT, "resources", "refusals")
DEFAULT_LANGUAGE = "en"
SUFFIX = ".jinja"

_environment = Environment(
    loader=FileSystemLoader(TEMPLATE_DIR),
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


def render(language: str) -> str:
    code = bare_language(language)
    if code not in languages():
        log.warning(
            "message= No refusal template for %s, falling back to %s",
            language,
            DEFAULT_LANGUAGE,
        )
        code = DEFAULT_LANGUAGE

    return _environment.get_template(f"{code}{SUFFIX}").render().strip()
