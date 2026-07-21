"""Renders the candidate-explanation prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from llm_client import LLMProvider

# src/candidate_explanation/prompts.py -> parents[2] is the repo root;
# templates live under resources/prompts/.
_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
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
