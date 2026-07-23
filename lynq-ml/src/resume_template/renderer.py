"""Render a structured resume into a PDF using the HTML/CSS templates."""

from __future__ import annotations

from pathlib import Path
from typing import Optional

from jinja2 import Environment, FileSystemLoader, select_autoescape

from resume_extractor.models import Resume

from .models import Template

# src/resume_template/renderer.py -> parents[2] is the repo root; the HTML/CSS
# templates live under resources/resume_template/<variant>/.
_TEMPLATES_DIR = Path(__file__).resolve().parents[2] / "resources" / "resume_template"

_env = Environment(
    loader=FileSystemLoader(str(_TEMPLATES_DIR)),
    autoescape=select_autoescape(["html", "xml"]),
    keep_trailing_newline=True,
)


def render_resume_pdf(
    resume: Resume, template: Template, photo_url: Optional[str] = None
) -> bytes:
    """Render ``resume`` with the given template and return the PDF bytes.

    Args:
        resume: The structured resume to render.
        template: Which visual template to use.
        photo_url: Optional URL to the profile photo. WeasyPrint fetches it at
            render time; a missing/unreachable image is skipped, not fatal.

    Returns:
        The rendered PDF as raw bytes.
    """
    variant = template.value.lower()
    html_str = _env.get_template(f"{variant}/index.html").render(
        resume=resume, photo_url=photo_url
    )

    # Lazy import: WeasyPrint pulls native libraries (Pango/Cairo), so keep it
    # out of module import time. This lets the app boot — and the unit tests
    # run — on environments where WeasyPrint is not installed.
    from weasyprint import HTML

    # base_url resolves the template's relative ``style.css`` reference.
    base_url = str(_TEMPLATES_DIR / variant)
    return HTML(string=html_str, base_url=base_url).write_pdf()
