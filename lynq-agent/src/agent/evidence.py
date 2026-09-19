from __future__ import annotations

import re
from typing import Any

from agent.skill_aliases import aliases_of, normalize

EXCLUDED_TOP_LEVEL_SECTIONS = frozenset({"personal_info"})


def find_evidence(resume: dict, claim: str) -> list[str]:
    candidates = aliases_of(claim)
    if not any(candidates):
        return []

    found: list[str] = []
    for section, value in resume.items():
        if section in EXCLUDED_TOP_LEVEL_SECTIONS:
            continue
        for path, text in _walk(value, f"$.{section}"):
            normalized = normalize(text)
            if any(_contains(normalized, candidate) for candidate in candidates):
                found.append(path)
    return found


def evidenced_vocabulary(resume: dict) -> set[str]:
    skills = resume.get("skills") or {}
    vocabulary: set[str] = set()
    for bucket in ("technical", "tools", "soft"):
        for name in skills.get(bucket) or []:
            vocabulary.update(aliases_of(str(name)))

    for entry in resume.get("work_experience") or []:
        for technology in entry.get("technologies") or []:
            vocabulary.update(aliases_of(str(technology)))
    for entry in resume.get("projects") or []:
        for technology in entry.get("technologies") or []:
            vocabulary.update(aliases_of(str(technology)))
    for entry in resume.get("certifications") or []:
        name = entry.get("name")
        if name:
            vocabulary.update(aliases_of(str(name)))
    return vocabulary


def is_evidenced(resume: dict, claim: str) -> bool:
    if normalize(claim) in evidenced_vocabulary(resume):
        return True
    return bool(find_evidence(resume, claim))


def _walk(node: Any, path: str):
    if isinstance(node, dict):
        for key, value in node.items():
            yield from _walk(value, f"{path}.{key}")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from _walk(value, f"{path}[{index}]")
    elif isinstance(node, str):
        yield path, node


def _contains(haystack: str, needle: str) -> bool:
    if not needle:
        return False
    pattern = r"(?<![0-9a-z])" + re.escape(needle) + r"(?![0-9a-z])"
    return re.search(pattern, haystack) is not None
