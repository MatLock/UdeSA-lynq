from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from typing import Any

from agent.apply import SKILLS, SUMMARY, WORK_EXPERIENCE

PERSONAL_INFO = "personal_info"
IMMUTABLE_ENTRY_FIELDS = ("company", "position", "start_date", "end_date", "is_current")
EDITABLE_ENTRY_FIELDS = ("description", "achievements", "technologies")
EDIT_SURFACE = (SUMMARY, WORK_EXPERIENCE, SKILLS)

# What the code can tell on its own by comparing the resume before and after a
# turn. These are structural: no reading is needed to see that an entry moved.
PERSONAL_INFO_CHANGED = "personal_info_changed"
ENTRIES_ADDED_OR_DROPPED = "entries_added_or_dropped"
ENTRIES_REORDERED = "entries_reordered"
IMMUTABLE_FIELD_CHANGED = "immutable_field_changed"
OUT_OF_SURFACE_CHANGED = "out_of_surface_changed"
SKILLS_DROPPED = "skills_dropped"
SKILLS_REORDERED = "skills_reordered"
FAULTS = (
    PERSONAL_INFO_CHANGED,
    ENTRIES_ADDED_OR_DROPPED,
    ENTRIES_REORDERED,
    IMMUTABLE_FIELD_CHANGED,
    OUT_OF_SURFACE_CHANGED,
    SKILLS_DROPPED,
    SKILLS_REORDERED,
)


@dataclass
class Unit:
    """One thing a turn changed, as a human reads it: the text before and the
    text after, in one place of the resume. The unit of annotation."""

    id: str
    section: str
    label: str
    original: str
    proposed: str
    applied: bool = True
    system_kind: str = "ok"
    faults: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "section": self.section,
            "label": self.label,
            "original": self.original,
            "proposed": self.proposed,
            "applied": self.applied,
            "system_kind": self.system_kind,
            "faults": list(self.faults),
        }


def unit_id(key: str, section: str, label: str, proposed: str = "") -> str:
    digest = hashlib.sha1(f"{key}|{section}|{label}|{proposed}".encode("utf-8")).hexdigest()
    return digest[:12]


def _text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "\n".join(_text(item) for item in value)
    if isinstance(value, dict):
        return "\n".join(f"{k}: {_text(v)}" for k, v in value.items())
    return str(value).strip()


def entry_label(entry: dict[str, Any]) -> str:
    return " at ".join(
        str(entry.get(f) or "").strip() for f in ("position", "company") if entry.get(f)
    ) or "?"


def entry_identity(entry: Any) -> tuple[str, str]:
    if not isinstance(entry, dict):
        return ("", "")
    return (
        " ".join(str(entry.get("company") or "").lower().split()),
        " ".join(str(entry.get("position") or "").lower().split()),
    )


def entry_text(entry: dict[str, Any]) -> str:
    pieces = [_text(entry.get("description"))]
    pieces.extend(_text(item) for item in entry.get("achievements") or [])
    technologies = [str(t) for t in entry.get("technologies") or []]
    if technologies:
        pieces.append("technologies: " + ", ".join(technologies))
    return "\n".join(piece for piece in pieces if piece)


def _skills_of(resume: dict[str, Any]) -> dict[str, list[str]]:
    buckets = resume.get(SKILLS)
    if isinstance(buckets, dict):
        return {str(b): [str(n) for n in names or []] for b, names in buckets.items()}
    if isinstance(buckets, list):
        return {"skills": [str(n) for n in buckets]}
    return {}


def units_of(key: str, before: dict[str, Any], after: dict[str, Any]) -> tuple[list[Unit], list[str]]:
    """Every place the resume changed in this turn, and the structural faults the
    change carries. A design that cannot reorder never produces `entries_reordered`;
    one that holds a reorder tool may. The diff does not know which arm ran."""
    units: list[Unit] = []
    faults: list[str] = []

    if (before.get(PERSONAL_INFO) or {}) != (after.get(PERSONAL_INFO) or {}):
        faults.append(PERSONAL_INFO_CHANGED)

    if _text(before.get(SUMMARY)) != _text(after.get(SUMMARY)):
        units.append(Unit(
            id=unit_id(key, SUMMARY, ""), section=SUMMARY, label="",
            original=_text(before.get(SUMMARY)), proposed=_text(after.get(SUMMARY)),
        ))

    units.extend(_entry_units(key, before, after, faults))
    units.extend(_skill_units(key, before, after, faults))

    for section in sorted(set(before) | set(after)):
        if section in EDIT_SURFACE or section == PERSONAL_INFO:
            continue
        if before.get(section) != after.get(section):
            if OUT_OF_SURFACE_CHANGED not in faults:
                faults.append(OUT_OF_SURFACE_CHANGED)
            units.append(Unit(
                id=unit_id(key, section, ""), section=section, label="",
                original=_text(before.get(section)), proposed=_text(after.get(section)),
                faults=[OUT_OF_SURFACE_CHANGED],
            ))
    return units, faults


def _entry_units(key: str, before: dict, after: dict, faults: list[str]) -> list[Unit]:
    old = [e for e in before.get(WORK_EXPERIENCE) or [] if isinstance(e, dict)]
    new = [e for e in after.get(WORK_EXPERIENCE) or [] if isinstance(e, dict)]
    old_ids = [entry_identity(e) for e in old]
    new_ids = [entry_identity(e) for e in new]

    if sorted(old_ids) != sorted(new_ids):
        faults.append(ENTRIES_ADDED_OR_DROPPED)
    elif old_ids != new_ids:
        faults.append(ENTRIES_REORDERED)

    units: list[Unit] = []
    remaining = list(enumerate(new))
    for index, entry in enumerate(old):
        match = next((i for i, (_, other) in enumerate(remaining) if entry_identity(other) == old_ids[index]), None)
        if match is None:
            continue
        _, other = remaining.pop(match)
        unit_faults: list[str] = []
        if any(entry.get(f) != other.get(f) for f in IMMUTABLE_ENTRY_FIELDS):
            unit_faults.append(IMMUTABLE_FIELD_CHANGED)
            if IMMUTABLE_FIELD_CHANGED not in faults:
                faults.append(IMMUTABLE_FIELD_CHANGED)
        if entry_text(entry) != entry_text(other) or unit_faults:
            label = entry_label(entry)
            units.append(Unit(
                id=unit_id(key, WORK_EXPERIENCE, label), section=WORK_EXPERIENCE, label=label,
                original=entry_text(entry), proposed=entry_text(other), faults=unit_faults,
            ))
    for _, added in remaining:
        label = entry_label(added)
        units.append(Unit(
            id=unit_id(key, WORK_EXPERIENCE, label), section=WORK_EXPERIENCE, label=label,
            original="", proposed=entry_text(added), faults=[ENTRIES_ADDED_OR_DROPPED],
        ))
    return units


def _skill_units(key: str, before: dict, after: dict, faults: list[str]) -> list[Unit]:
    old, new = _skills_of(before), _skills_of(after)
    units: list[Unit] = []
    for bucket in sorted(set(old) | set(new)):
        was, now = old.get(bucket, []), new.get(bucket, [])
        if was == now:
            continue
        unit_faults: list[str] = []
        lower_was = [" ".join(n.lower().split()) for n in was]
        lower_now = [" ".join(n.lower().split()) for n in now]
        if any(n not in lower_now for n in lower_was):
            unit_faults.append(SKILLS_DROPPED)
        elif lower_now[: len(lower_was)] != lower_was:
            unit_faults.append(SKILLS_REORDERED)
        for fault in unit_faults:
            if fault not in faults:
                faults.append(fault)
        units.append(Unit(
            id=unit_id(key, SKILLS, bucket), section=SKILLS, label=bucket,
            original=", ".join(was), proposed=", ".join(now), faults=unit_faults,
        ))
    return units
