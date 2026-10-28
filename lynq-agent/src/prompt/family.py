from __future__ import annotations

import hashlib
import os

from jinja2 import Environment, FileSystemLoader, StrictUndefined, Template, select_autoescape

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HASH_LENGTH = 12


class PromptFamily:
    def __init__(self, name: str) -> None:
        self.name = name
        self.template_dir = os.path.join(_REPO_ROOT, "resources", "prompts", name)
        self._environment = Environment(
            loader=FileSystemLoader(self.template_dir),
            autoescape=select_autoescape(default=False, default_for_string=False),
            undefined=StrictUndefined,
            trim_blocks=True,
            lstrip_blocks=True,
            keep_trailing_newline=True,
        )

    def _source(self, provider: str) -> str:
        path = os.path.join(self.template_dir, f"{provider}.jinja")
        with open(path, encoding="utf-8") as file:
            return file.read()

    def reference(self, provider: str) -> str:
        digest = hashlib.sha256(self._source(provider).encode("utf-8")).hexdigest()
        return f"{self.name}/{provider}@{digest[:HASH_LENGTH]}"

    def template(self, provider: str) -> Template:
        return self._environment.get_template(f"{provider}.jinja")
