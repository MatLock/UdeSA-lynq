from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from agent.schemas import EditProposal
from agent.state import TurnState

log = logging.getLogger(__name__)

SUMMARY = "summary"
WORK_EXPERIENCE = "work_experience"
SKILLS = "skills"

# The only reason the code itself rejects a part: it has nowhere to put it. What
# a part says is the judge's call, never the code's.
UNKNOWN_ENTRY = "unknown_entry"


class Rejection(str):
    """`where: reason` — one part of a proposal that did not enter the resume. A
    string, so the trace and the retry read it as one; with its parts on the
    side, so the notice to the candidate can name the section in their language."""

    section: str
    label: str
    kind: str
    reason: str

    def __new__(cls, section: str, label: str, kind: str, reason: str) -> "Rejection":
        where = section if not label else (
            f"{section}.{label}" if section == SKILLS else f"{section} {label}"
        )
        rejection = super().__new__(cls, f"{where}: {reason}")
        rejection.section, rejection.label, rejection.kind, rejection.reason = (
            section, label, kind, reason,
        )
        return rejection


@dataclass
class Part:
    """One thing the proposal wants to change, resolved to its place in the resume,
    with the text it replaces beside the text it proposes: what the judge reads."""

    id: str
    section: str
    label: str
    original: str
    proposed: str
    index: int | None = None
    fields: dict[str, Any] | None = None
    bucket: str | None = None
    names: list[str] | None = None


def _normalize(text: str) -> str:
    return " ".join(str(text or "").lower().split())


def _entry_label(entry: dict[str, Any]) -> str:
    return " at ".join(
        str(entry.get(field) or "").strip()
        for field in ("position", "company")
        if entry.get(field)
    ) or "?"


def find_entry(entries: list[Any], company: str, position: str) -> int | None:
    """The entry an edit names. Company and position together; the company alone
    when it is unambiguous, because a model paraphrases a title now and then."""
    def same(entry: Any, field: str, wanted: str) -> bool:
        return isinstance(entry, dict) and _normalize(entry.get(field)) == _normalize(wanted)

    both = [
        index for index, entry in enumerate(entries)
        if same(entry, "company", company) and same(entry, "position", position)
    ]
    if len(both) == 1:
        return both[0]
    by_company = [index for index, entry in enumerate(entries) if same(entry, "company", company)]
    if len(by_company) == 1 and company.strip():
        return by_company[0]
    return None


def _entry_text(entry: dict[str, Any]) -> str:
    pieces = [str(entry.get("description") or "").strip()]
    pieces.extend(str(item).strip() for item in entry.get("achievements") or [])
    return "\n".join(piece for piece in pieces if piece)


def plan(state: TurnState, proposal: EditProposal) -> tuple[list[Part], list[Rejection]]:
    """Every part of the proposal with its place in the resume, and the parts that
    have none. Entries are never added, dropped or reordered, so an index is the
    same in the base resume and in the one being edited."""
    parts: list[Part] = []
    rejections: list[Rejection] = []

    if proposal.summary.strip():
        parts.append(Part(
            id=SUMMARY, section=SUMMARY, label="",
            original=str(state.base_resume.get(SUMMARY) or ""),
            proposed=proposal.summary.strip(),
        ))

    entries = state.resume.get(WORK_EXPERIENCE) or []
    base_entries = state.base_resume.get(WORK_EXPERIENCE) or []
    for number, edit in enumerate(proposal.entries):
        fields: dict[str, Any] = {}
        if edit.description.strip():
            fields["description"] = edit.description.strip()
        achievements = [item.strip() for item in edit.achievements if item.strip()]
        if achievements:
            fields["achievements"] = achievements
        if not fields:
            # An entry sent with nothing written is "keep it", as the prompt says.
            continue
        label = _entry_label({"company": edit.company, "position": edit.position})
        index = find_entry(entries, edit.company, edit.position)
        if index is None or index >= len(base_entries):
            rejections.append(Rejection(WORK_EXPERIENCE, label, UNKNOWN_ENTRY, UNKNOWN_ENTRY))
            continue
        parts.append(Part(
            id=f"entry:{number}", section=WORK_EXPERIENCE, label=_entry_label(entries[index]),
            original=_entry_text(base_entries[index]),
            proposed=_entry_text(fields),
            index=index, fields=fields,
        ))

    current = state.resume.get(SKILLS) if isinstance(state.resume.get(SKILLS), dict) else {}
    for bucket, names in proposal.skills.buckets().items():
        parts.append(Part(
            id=f"skills:{bucket}", section=SKILLS, label=bucket,
            original=", ".join(str(name) for name in current.get(bucket) or []),
            proposed=", ".join(names),
            bucket=bucket, names=list(dict.fromkeys(name.strip() for name in names if name.strip())),
        ))

    return parts, rejections


def commit(state: TurnState, parts: list[Part], approved: set[str]) -> None:
    """Writes the approved parts into the resume and records each as a change.
    Nothing here decides anything: the judge did."""
    for part in parts:
        if part.id not in approved:
            continue
        if part.section == SUMMARY:
            if state.resume.get(SUMMARY) == part.proposed:
                continue
            state.resume[SUMMARY] = part.proposed
            state.record_change(SUMMARY, "rewrite", "rewrote the summary")
        elif part.section == WORK_EXPERIENCE and part.fields is not None and part.index is not None:
            entry = state.resume[WORK_EXPERIENCE][part.index]
            if all(entry.get(field) == value for field, value in part.fields.items()):
                continue
            entry.update(part.fields)
            state.record_change(
                WORK_EXPERIENCE, "rewrite",
                f"rewrote {', '.join(sorted(part.fields))} of {part.label}",
                fields=sorted(part.fields), index=part.index,
            )
        elif part.section == SKILLS and part.bucket is not None:
            skills = state.resume.get(SKILLS)
            if not isinstance(skills, dict):
                skills = {}
                state.resume[SKILLS] = skills
            if skills.get(part.bucket) == part.names:
                continue
            skills[part.bucket] = list(part.names or [])
            state.record_change(
                SKILLS, "replace", f"rewrote the {part.bucket} skills", fields=[part.bucket],
            )
