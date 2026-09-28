from __future__ import annotations

import hashlib
import os

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEMPLATE_DIR = os.path.join(_REPO_ROOT, "resources", "prompts", "intent")
FAMILY = "intent"
HASH_LENGTH = 12

_environment = Environment(
    loader=FileSystemLoader(TEMPLATE_DIR),
    autoescape=select_autoescape(default=False, default_for_string=False),
    undefined=StrictUndefined,
    trim_blocks=True,
    lstrip_blocks=True,
    keep_trailing_newline=True,
)


def _source(provider: str) -> str:
    with open(os.path.join(TEMPLATE_DIR, f"{provider}.jinja"), encoding="utf-8") as file:
        return file.read()


def reference(provider: str) -> str:
    digest = hashlib.sha256(_source(provider).encode("utf-8")).hexdigest()
    return f"{FAMILY}/{provider}@{digest[:HASH_LENGTH]}"


def render(provider: str, history: list[tuple[str, str]], message: str) -> str:
    template = _environment.get_template(f"{provider}.jinja")
    return template.render(history=history, message=message)
