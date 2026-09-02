"""Resolving the UI language codes the backend forwards into prompt wording.

Prompts instruct the model in plain terms ("Write them in Spanish."), so the code
that arrives with a request has to become a language name first. How it arrives
varies by endpoint — the ``output-language`` header for upskilling suggestions, a
``language`` query parameter for resume skill extraction — but the resolution is
the same, so every prompt that produces human-readable text shares this.
"""

from __future__ import annotations

# Human-readable names for the language codes the UI sends. An unknown code falls
# back to English rather than leaking a raw code into the prompt.
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
