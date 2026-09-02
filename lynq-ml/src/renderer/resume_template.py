"""Render a structured resume into a PDF using the HTML/CSS templates."""

from __future__ import annotations

import logging
from functools import lru_cache
from pathlib import Path
from typing import Optional

from jinja2 import Environment, FileSystemLoader, select_autoescape

from model.resume_extractor import Resume
from model.resume_template import Template

# src/renderer/resume_template.py -> parents[2] is the repo root; the HTML/CSS
# templates live under resources/resume_template/<variant>/.
_TEMPLATES_DIR = Path(__file__).resolve().parents[2] / "resources" / "resume_template"

log = logging.getLogger(__name__)

# Not Python packages, so pip cannot install them and requirements.txt cannot
# pin them. Kept next to the check that reports they are missing.
_NATIVE_LIBS_HINT = (
    "install them with 'brew install pango' on macOS or "
    "'apt-get install libcairo2 libpango-1.0-0 libpangocairo-1.0-0 "
    "libgdk-pixbuf-2.0-0' on Debian/Ubuntu"
)

_env = Environment(
    loader=FileSystemLoader(str(_TEMPLATES_DIR)),
    autoescape=select_autoescape(["html", "xml"]),
    keep_trailing_newline=True,
)


# Cached: a failed import is not remembered by Python, so without this every
# health probe would retry the dlopen and reprint WeasyPrint's own banner.
@lru_cache(maxsize=1)
def pdf_renderer_available() -> bool:
    """Whether WeasyPrint can load its native stack (Pango/Cairo).

    The WeasyPrint import is deliberately lazy inside :func:`render_resume_pdf`
    so the service boots — and the unit tests run — on a machine without those
    libraries. The cost is that a missing library stays invisible until the first
    PDF request 500s, which is what this exists to prevent: it is checked at
    startup and reported by ``/lynq-ml/health``.
    """
    try:
        import weasyprint  # noqa: F401
    except Exception as exc:  # noqa: BLE001 - any import failure means no PDFs
        log.error(
            "message= PDF rendering is unavailable, WeasyPrint could not load its "
            "native libraries: %s",
            _NATIVE_LIBS_HINT,
            exc_info=exc,
        )
        return False
    return True


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
