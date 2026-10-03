"""Renders the language-detection prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from llm_client import LLMProvider

# src/prompt/language_detection.py -> parents[2] is the repo root; templates
# live under resources/prompts/.
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


def render_language_detection_prompt(provider: LLMProvider, *, text: str) -> str:
    """Render ``language_detection/<provider>.jinja`` with the input text.

    Args:
        provider: Selects the provider-specific template variant.
        text: The free-form text whose main language should be detected.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"language_detection/{provider.value}.jinja")
    return template.render(text=text)
