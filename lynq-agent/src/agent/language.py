from __future__ import annotations

import logging

from langdetect import LangDetectException, detect

from agent.lexical import normalize

log = logging.getLogger(__name__)

_MIN_SAMPLE_CHARS = 24
MIN_DETECTABLE_CHARS = 80


def _sample(resume: dict) -> str:
    pieces = [resume.get("summary") or ""]
    for entry in resume.get("work_experience") or []:
        if isinstance(entry, dict):
            pieces.append(entry.get("description") or "")
    return " ".join(piece.strip() for piece in pieces if piece).strip()


def bare(code: str) -> str:
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

    if bare(detected) != bare(declared):
        log.warning(
            "message= Resume language mismatch, declared=%s, detected=%s. "
            "Keeping the declared one",
            declared,
            detected,
        )
    return declared


def detected_conflict(
    texts: list[str],
    *,
    language: str,
    resume_language: str,
    vocabulary: set[str],
) -> str | None:
    """The language a text was written in when it is the conversation's and not the
    resume's. Skill names are taken out first: `Kubernetes` speaks no language, and
    a detector fed a list of them guesses. Short prose is left alone for the same
    reason."""
    if bare(language) == bare(resume_language):
        return None

    for text in texts:
        prose = " ".join(
            word for word in text.split() if normalize(word) not in vocabulary
        )
        if len(prose) <= MIN_DETECTABLE_CHARS:
            continue
        try:
            detected = bare(detect(prose))
        except LangDetectException:
            continue
        if detected == bare(language) != bare(resume_language):
            return detected
    return None
