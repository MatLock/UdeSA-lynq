"""Renders the resume-extractor prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from llm_client import LLMProvider

# src/resume_extractor/prompts.py -> parents[2] is the repo root; templates
# live under resources/prompts/.
_PROMPTS_DIR = Path(__file__).resolve().parents[2] / "resources" / "prompts"

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
    keep_trailing_newline=True,
)


def render_resume_extractor_prompt(provider: LLMProvider, *, resume_text: str) -> str:
    """Render ``resume_extractor/<provider>.jinja`` with the resume text.

    Args:
        provider: Selects the provider-specific template variant.
        resume_text: The full plain-text content extracted from the resume.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"resume_extractor/{provider.value}.jinja")
    return template.render(resume_text=resume_text)