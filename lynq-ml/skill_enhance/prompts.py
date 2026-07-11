"""Renders the key-extractor prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from llm_client import LLMProvider

_PROMPTS_DIR = Path(__file__).resolve().parent.parent / "prompts"

_env = Environment(
    loader=FileSystemLoader(str(_PROMPTS_DIR)),
    undefined=StrictUndefined,
    autoescape=False,
    keep_trailing_newline=True,
)


def render_key_extractor_prompt(
    provider: LLMProvider,
    *,
    job_title: str,
    work_type: str,
    job_description: str,
) -> str:
    """Render ``key_extractor/<provider>.jinja`` with the job posting data.

    Args:
        provider: Selects the provider-specific template variant.
        job_title: The job title.
        work_type: The work type (e.g. ``REMOTE`` / ``IN_OFFICE``).
        job_description: The full job description.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"key_extractor/{provider.value}.jinja")
    return template.render(
        job_title=job_title,
        work_type=work_type,
        job_description=job_description,
    )