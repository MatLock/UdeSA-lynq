from __future__ import annotations

from typing import Any

from agent.lexical import find_match, same_skill

PERSONAL_INFO = "personal_info"
SKILLS = "skills"
SKILL_BUCKETS = ("technical", "tools", "soft")
TECHNOLOGY_SECTIONS = ("work_experience", "projects")
MAX_HITS = 10


def searchable(node: Any, path: str = "") -> list[tuple[str, str]]:
    """Every string of the resume with its JSON path, `personal_info` left out."""
    if isinstance(node, str):
        return [(path, node)] if node.strip() else []
    if isinstance(node, dict):
        found: list[tuple[str, str]] = []
        for key, value in node.items():
            if not path and key == PERSONAL_INFO:
                continue
            found.extend(searchable(value, f"{path}.{key}" if path else key))
        return found
    if isinstance(node, list):
        found = []
        for index, item in enumerate(node):
            found.extend(searchable(item, f"{path}[{index}]"))
        return found
    return []


def skill_names(resume: dict[str, Any]) -> list[str]:
    names: list[str] = []
    buckets = resume.get(SKILLS)
    if isinstance(buckets, dict):
        for bucket in SKILL_BUCKETS:
            names.extend(
                value for value in buckets.get(bucket) or [] if isinstance(value, str)
            )
    elif isinstance(buckets, list):
        names.extend(value for value in buckets if isinstance(value, str))

    for section in TECHNOLOGY_SECTIONS:
        for entry in resume.get(section) or []:
            if isinstance(entry, dict):
                names.extend(
                    value
                    for value in entry.get("technologies") or []
                    if isinstance(value, str)
                )
    return names


def hits_for(resume: dict[str, Any], claim: str) -> list[dict[str, str]]:
    """Where the resume backs a claim: a lexical search in code, never a judgement."""
    hits: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for path, text in searchable(resume):
        matched = find_match(text, claim)
        if matched is None or (path, matched) in seen:
            continue
        seen.add((path, matched))
        hits.append({"path": path, "matched": matched})
        if len(hits) >= MAX_HITS:
            break
    return hits


def backing_for(resume: dict[str, Any], name: str) -> str | None:
    """The wording the resume already uses for a skill, or None when nothing backs it."""
    for listed in skill_names(resume):
        if same_skill(listed, name):
            return listed
    hits = hits_for(resume, name)
    return hits[0]["matched"] if hits else None
