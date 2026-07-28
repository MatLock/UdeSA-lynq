"""Renders the key-extractor prompt for the selected LLM provider."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from llm_client import LLMProvider

# src/prompt/skill_enhance.py -> parents[2] is the repo root; templates live
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


def render_key_extractor_prompt(
    provider: LLMProvider,
    *,
    job_title: str,
    work_type: str,
    job_description: str,
) -> str:
    """Render ``job_post_skill_extraction/<provider>.jinja`` with the job posting data.

    Args:
        provider: Selects the provider-specific template variant.
        job_title: The job title.
        work_type: The work type (e.g. ``REMOTE`` / ``IN_OFFICE``).
        job_description: The full job description.

    Returns:
        The rendered prompt ready to send to the model.
    """
    template = _env.get_template(f"job_post_skill_extraction/{provider.value}.jinja")
    return template.render(
        job_title=job_title,
        work_type=work_type,
        job_description=job_description,
    )