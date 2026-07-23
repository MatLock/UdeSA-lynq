"""Renders the translation prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from llm_client import LLMProvider

from model.translation import Language

# src/prompt/translation.py -> parents[2] is the repo root; templates live
# under resources/prompts/.
_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

# Human-readable names fed to the model; keys mirror the Language enum.
_LANGUAGE_NAMES = {
    Language.EN: "English",
    Language.ES: "Spanish",
    Language.FR: "French",
    Language.PR: "Portuguese",
}

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
    keep_trailing_newline=True,
)


def render_translation_prompt(
    provider: LLMProvider, *, resume_json: str, language: Language
) -> str:
    """Render ``translation/<provider>.jinja`` with the resume and language.

    Args:
        provider: Selects the provider-specific template variant.
        resume_json: The resume object serialized to a JSON string.
        language: The target language to translate every value into.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"translation/{provider.value}.jinja")
    return template.render(
        resume_json=resume_json,
        target_language=_LANGUAGE_NAMES[language],
    )
