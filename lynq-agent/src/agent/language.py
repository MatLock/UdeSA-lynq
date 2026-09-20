from __future__ import annotations

import logging

from langdetect import LangDetectException, detect

log = logging.getLogger(__name__)

_MIN_SAMPLE_CHARS = 24


def _sample(resume: dict) -> str:
    pieces = [resume.get("summary") or ""]
    for entry in resume.get("work_experience") or []:
        if isinstance(entry, dict):
            pieces.append(entry.get("description") or "")
    return " ".join(piece.strip() for piece in pieces if piece).strip()


def _base(code: str) -> str:
    return code.split("-")[0].split("_")[0].strip().lower()


def verify_resume_language(resume: dict, declared: str) -> str:
    sample = _sample(resume)
    if len(sample) < _MIN_SAMPLE_CHARS:
        return declared

    try:
        detected = detect(sample)
    except LangDetectException as exc:
        log.warning("message= Could not detect the resume language, %s", exc)
        return declared

    if _base(detected) != _base(declared):
        log.warning(
            "message= Resume language mismatch, declared=%s, detected=%s. "
            "Keeping the declared one",
            declared,
            detected,
        )
    return declared
