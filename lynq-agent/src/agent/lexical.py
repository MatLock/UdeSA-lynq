from __future__ import annotations

import string
import unicodedata

MIN_PREFIX_CHARS = 4
_PUNCTUATION = string.punctuation + "«»¿¡“”’–—"


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text)
    without_accents = "".join(
        character for character in decomposed if not unicodedata.combining(character)
    )
    return "".join(
        character for character in without_accents.lower() if character.isalnum()
    )


def matches(candidate: str, claim: str) -> bool:
    if not candidate or not claim:
        return False
    if len(candidate) < MIN_PREFIX_CHARS or len(claim) < MIN_PREFIX_CHARS:
        return candidate == claim
    return candidate.startswith(claim) or claim.startswith(candidate)


def words(text: str) -> list[str]:
    return [word.strip(_PUNCTUATION) for word in text.split() if word.strip(_PUNCTUATION)]


def claim_words(claim: str) -> list[str]:
    return [normalized for normalized in (normalize(word) for word in claim.split()) if normalized]


def find_match(text: str, wanted: list[str]) -> str | None:
    if not wanted:
        return None

    if matches(normalize(text), "".join(wanted)):
        return text.strip()

    tokens = [(word, normalize(word)) for word in words(text)]
    tokens = [(word, normalized) for word, normalized in tokens if normalized]
    width = len(wanted)
    for start in range(len(tokens) - width + 1):
        window = tokens[start : start + width]
        if all(matches(window[index][1], wanted[index]) for index in range(width)):
            return " ".join(word for word, _ in window)
    return None
