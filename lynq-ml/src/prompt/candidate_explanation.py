"""Renders the candidate-explanation prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from llm_client import LLMProvider

# src/prompt/candidate_explanation.py -> parents[2] is the repo root;
# templates live under resources/prompts/.
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


def render_candidate_explanation_prompt(
    provider: LLMProvider, *, input_json: str
) -> str:
    """Render ``candidate_explanation/<provider>.jinja`` with the input JSON.

    Args:
        provider: Selects the provider-specific template variant.
        input_json: The ``{"job": ..., "candidate": ...}`` payload, already
            serialized to a JSON string exactly as the prompt expects it.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"candidate_explanation/{provider.value}.jinja")
    return template.render(input_json=input_json)
