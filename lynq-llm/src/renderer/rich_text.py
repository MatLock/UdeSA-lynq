"""Turn a resume's multi-line prose into the blocks a template can render.

Resume prose is stored as plain text carrying its own structure: one line per
bullet, one line per paragraph. HTML collapses every run of whitespace, so a
template that interpolates the raw string prints a bulleted role as one run-on
sentence — which is what the candidate sees in the PDF and on screen.

Templates therefore ask for the blocks the text describes and render each one
with real markup. The same rule is implemented in the frontend
(``components/RichText``) so a resume reads the same way on screen and in the
PDF, whichever of the two produced it.
"""

from __future__ import annotations

import re

# The glyphs a resume is bulleted with, ASCII stand-ins included. The marker is
# the author saying "this is a list item"; the glyph itself is presentation, so
# it is dropped and the template draws its own.
_BULLET = re.compile(r"^[-–—*•·▪●◦⁃]\s+")

# A line holding a marker and nothing else. It reads as an empty bullet the
# author left behind, so it is dropped rather than rendered as a stray glyph —
# and dropped without ending the list it sits inside.
_EMPTY_BULLET = re.compile(r"^[-–—*•·▪●◦⁃]$")

_LIST = "list"
_PARAGRAPH = "paragraph"


def rich_text_blocks(text: str | None) -> list[dict]:
    """Split ``text`` into consecutive list and paragraph blocks.

    Args:
        text: The stored prose, or ``None``.

    Returns:
        A list of blocks in the order they appear. A list block is
        ``{"type": "list", "entries": [...]}`` with the bullet markers stripped;
        a paragraph block is ``{"type": "paragraph", "text": "..."}``. Empty
        text yields an empty list, so a template can render nothing at all.

    Note:
        Blank lines only separate blocks — they carry no content and never
        become an empty paragraph. Runs of adjacent bullets collapse into one
        list rather than one list each.
    """
    if not text or not text.strip():
        return []

    blocks: list[dict] = []
    entries: list[str] = []

    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or _EMPTY_BULLET.match(stripped):
            continue

        marker = _BULLET.match(stripped)
        if marker:
            entries.append(stripped[marker.end():].strip())
            continue

        if entries:
            blocks.append({"type": _LIST, "entries": entries})
            entries = []
        blocks.append({"type": _PARAGRAPH, "text": stripped})

    if entries:
        blocks.append({"type": _LIST, "entries": entries})

    return blocks
