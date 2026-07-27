"""Renders the upskilling-suggestion prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from llm_client import LLMProvider

# src/prompt/upskilling_suggestion.py -> parents[2] is the repo root;
# templates live under resources/prompts/.
_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
    keep_trailing_newline=True,
)

# Human-readable names for the language codes the UI sends, so the prompt can
# instruct the model in plain terms ("Respond in Spanish."). An unknown code
# falls back to English rather than leaking a raw code into the prompt.
_LANGUAGE_NAMES = {
    "en": "English",
    "es": "Spanish",
}
_DEFAULT_LANGUAGE = "English"


def language_name(language: str | None) -> str:
    """Resolve a UI language code (e.g. ``"es"``) to its English name.

    Matching is case-insensitive and ignores any region suffix (``"es-AR"`` ->
    ``"Spanish"``). Unknown or missing codes fall back to English.
    """
    if not language:
        return _DEFAULT_LANGUAGE
    code = language.strip().lower().split("-")[0]
    return _LANGUAGE_NAMES.get(code, _DEFAULT_LANGUAGE)


def render_upskilling_prompt(
    provider: LLMProvider, *, input_json: str, language: str | None = None
) -> str:
    """Render ``upskilling_suggestion/<provider>.jinja`` with the input JSON.

    Args:
        provider: Selects the provider-specific template variant.
        input_json: The ``{"job": ..., "candidate": ...}`` payload, already
            serialized to a JSON string exactly as the prompt expects it.
        language: The UI language code the response prose should be written in
            (e.g. ``"es"``); resolved to a language name and injected into the
            template. Defaults to English.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"upskilling_suggestion/{provider.value}.jinja")
    return template.render(input_json=input_json, language=language_name(language))
