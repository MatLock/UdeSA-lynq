"""Read resume documents stored in S3 and extract their text content."""

from __future__ import annotations

import io

from docx import Document
from pypdf import PdfReader

from file_downloader.downloader import download_from_presigned_url

_PDF_MAGIC = b"%PDF"
_ZIP_MAGIC = b"PK\x03\x04"  # .docx files are zip archives


def read_resume(presigned_url: str) -> str:
    """Download a resume from a presigned S3 URL and return its text content.

    Supports PDF and DOCX documents.

    Args:
        presigned_url: A presigned S3 URL pointing to a .pdf or .docx file.

    Returns:
        The extracted text content as a single string.
    """
    content = download_from_presigned_url(presigned_url)

    if content.startswith(_PDF_MAGIC):
        return _read_pdf(content)
    if content.startswith(_ZIP_MAGIC):
        return _read_docx(content)
    raise ValueError("Unsupported document format; expected PDF or DOCX.")


def _read_pdf(content: bytes) -> str:
    """Extract text from PDF bytes."""
    reader = PdfReader(io.BytesIO(content))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def _read_docx(content: bytes) -> str:
    """Extract text from DOCX bytes."""
    document = Document(io.BytesIO(content))
    return "\n".join(paragraph.text for paragraph in document.paragraphs)