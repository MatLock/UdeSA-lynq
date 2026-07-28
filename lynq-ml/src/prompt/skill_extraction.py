"""Renders the skill-extraction prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from llm_client import LLMProvider

from prompt.language import language_name

# src/prompt/user_resume_skill_extraction.py -> parents[2] is the repo root; templates live
# under resources/prompts/.
_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

# Prompt templates are plain-text ".jinja" files, so select_autoescape never
# escapes them; HTML/XML templates would still be escaped if any were added.
_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=select_autoescape(
        enabled_extensions=("html", "htm", "xml"),
        default_for_string=False,
    ),
    keep_trailing_newline=True,
)


def render_skill_extraction_prompt(
    provider: LLMProvider, *, resume_json: str, language: str | None = None
) -> str:
    """Render ``user_resume_skill_extraction/<provider>.jinja`` with the resume JSON.

    Args:
        provider: Selects the provider-specific template variant.
        resume_json: The resume serialized as a JSON string.
        language: The UI language code the soft skills should be written in
            (e.g. ``"es"``); resolved to a language name and injected into the
            template. Defaults to English when absent.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"user_resume_skill_extraction/{provider.value}.jinja")
    return template.render(resume_json=resume_json, language=language_name(language))
