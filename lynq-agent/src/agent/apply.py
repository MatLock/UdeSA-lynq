from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any

from agent.schemas import EditProposal
from agent.state import TurnState

log = logging.getLogger(__name__)

SUMMARY = "summary"
WORK_EXPERIENCE = "work_experience"
SKILLS = "skills"

# What the code rejects on its own: a part with nowhere to go, and a part whose
# shape changed. Structure is mechanical — how many lines, which numbers, which
# skills, in what order — so the code checks it; what a part says is the judge's call.
UNKNOWN_ENTRY = "unknown_entry"
CUT = "cut"
MOVED = "moved"
UNBACKED = "unbacked"

# Two lines are the same line when they share this much of their words: enough to
# catch a line moved as it was or barely touched, loose enough to let a rewrite
# through to the judge.
SAME_LINE = 0.75

_NUMBER = re.compile(r"\d[\d.,]*%?")
_GLYPH = re.compile(r"^[-•*]\s*")
_WORD = re.compile(r"\w+")
# A word that names something rather than says something: capitalised inside a
# sentence, or carrying a digit — `Cognito`, `JUnit`, `S3`, `CSV/HTML`.
_NAME = re.compile(r"^(?:[A-Z][A-Za-z0-9+#./-]*|[A-Za-z]*\d[A-Za-z0-9+#./-]*)$")
_PUNCTUATION = ".,;:()[]{}\"'"


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


def _lines(text: Any) -> list[str]:
    return [line.strip() for line in str(text or "").split("\n") if line.strip()]


def achievements_of(items: list[Any]) -> list[str]:
    """One achievement per item. A model now and then folds the whole list into one
    string with the items separated by newlines; that is a list, not an achievement."""
    return [
        _GLYPH.sub("", piece).strip()
        for item in items
        for piece in str(item or "").split("\n")
        if _GLYPH.sub("", piece).strip()
    ]


def _words(line: str) -> set[str]:
    return set(_WORD.findall(_GLYPH.sub("", line).lower()))


def _same_line(one: str, other: str) -> bool:
    words, others = _words(one), _words(other)
    return bool(words) and len(words & others) / len(words | others) >= SAME_LINE


def _names(text: str, name: str) -> bool:
    # A plural is the same name: `APIs` is backed by `REST API`, `Lambda` by `Lambdas`.
    stem = name[:-1] if name.endswith("s") and len(name) > 2 else name
    return re.search(rf"(?<!\w){re.escape(stem)}s?(?!\w)", text, re.IGNORECASE) is not None


def technologies_of(resume: dict[str, Any]) -> list[str]:
    """Every technology the resume names as such: the skill buckets and the
    `technologies` of each entry. The words a rewrite is not allowed to lose."""
    names: list[str] = []
    skills = resume.get(SKILLS) if isinstance(resume.get(SKILLS), dict) else {}
    for bucket in skills.values():
        names.extend(str(name).strip() for name in bucket or [])
    for entry in resume.get(WORK_EXPERIENCE) or []:
        if isinstance(entry, dict):
            names.extend(str(name).strip() for name in entry.get("technologies") or [])
    return list(dict.fromkeys(name for name in names if name))


def _without_dates(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: _without_dates(item)
            for key, item in value.items()
            if key != "personal_info" and not key.endswith("_date")
        }
    if isinstance(value, list):
        return [_without_dates(item) for item in value]
    return value


def resume_text(resume: dict[str, Any]) -> str:
    """What may back a name: the resume without its dates, so that a `12` in
    `2020-12` never vouches for twelve years of anything."""
    return json.dumps(_without_dates(resume), ensure_ascii=False)


def names_in(line: str) -> list[str]:
    """The names a line drops: every capitalised word that does not open a sentence,
    and every word with a digit in it. What a rewrite may only take from the resume."""
    names: list[str] = []
    opens_sentence = True
    for raw in _GLYPH.sub("", line).split():
        token = raw.strip(_PUNCTUATION)
        if token and not opens_sentence and _NAME.match(token):
            names.append(token)
        opens_sentence = raw.endswith((".", ":", ";", "!", "?"))
    return list(dict.fromkeys(names))


def unbacked_names(proposed: str, backing: str) -> list[str]:
    return [
        name
        for line in _lines(proposed)
        for name in names_in(line)
        if not _names(backing, name)
    ]


def text_fault(
    original: str, proposed: str, technologies: list[str] = (), backing: str = ""
) -> tuple[str, str] | None:
    """Why a proposed text cannot replace the original, if it cannot: it has fewer
    lines, it lost a number or a technology, it moved a line, or it names something
    its backing — the entry for an entry, the resume for the summary — does not. A
    line may be rewritten in its place and lines may be added after the last
    original one, in the words of the resume; nothing else."""
    original_lines, proposed_lines = _lines(original), _lines(proposed)
    if len(proposed_lines) < len(original_lines):
        return CUT, (
            f"the original has {len(original_lines)} lines and the proposal "
            f"{len(proposed_lines)}: every line stays in its place, new lines go after them"
        )
    missing = [number for number in _NUMBER.findall(original) if number not in proposed]
    if missing:
        return CUT, f"numbers of the original are missing: {', '.join(dict.fromkeys(missing))}"
    lost = [
        name for name in technologies
        if _names(original, name) and not _names(proposed, name)
    ]
    if lost:
        return CUT, f"technologies of the original are missing: {', '.join(lost)}"
    for index, line in enumerate(proposed_lines):
        same = [number for number, other in enumerate(original_lines) if _same_line(line, other)]
        if same and index not in same:
            return MOVED, (
                f"line {index + 1} of the proposal is line {same[0] + 1} of the original: "
                "lines keep their order"
            )
    unbacked = unbacked_names(proposed, backing or original)
    if unbacked:
        return UNBACKED, (
            "names the resume does not state here, so they cannot enter: "
            + ", ".join(dict.fromkeys(unbacked))
        )
    return None


def skills_fault(original: list[str], proposed: list[str], resume: str = "") -> tuple[str, str] | None:
    """A bucket keeps every skill it has, in its order; new skills go after the last,
    and only when the resume names them somewhere, spelled as it spells them."""
    def key(name: str) -> str:
        return " ".join(str(name).lower().split())

    original_keys = [key(name) for name in original]
    proposed_keys = [key(name) for name in proposed]
    missing = [name for name, k in zip(original, original_keys) if k not in proposed_keys]
    if missing:
        return CUT, f"skills of the bucket are missing: {', '.join(missing)}"
    if proposed_keys[: len(original_keys)] != original_keys:
        return MOVED, "the bucket keeps its skills in their order, new skills go after the last"
    unbacked = [name for name in proposed[len(original):] if not _names(resume, name)]
    if unbacked:
        return UNBACKED, f"the resume names these nowhere: {', '.join(unbacked)}"
    return None


def plan(state: TurnState, proposal: EditProposal) -> tuple[list[Part], list[Rejection]]:
    """Every part of the proposal with its place in the resume, and the parts that
    have none or that changed shape. Entries are never added, dropped or reordered,
    so an index is the same in the base resume and in the one being edited. The
    shape of a part is measured against the base resume, like the judge does."""
    parts: list[Part] = []
    rejections: list[Rejection] = []
    technologies = technologies_of(state.base_resume)
    backing = resume_text(state.base_resume)

    def keep(part: Part, fault: tuple[str, str] | None) -> None:
        if fault is None:
            parts.append(part)
        else:
            rejections.append(Rejection(part.section, part.label, *fault))

    if proposal.summary.strip():
        original = str(state.base_resume.get(SUMMARY) or "")
        proposed = proposal.summary.strip()
        keep(
            Part(id=SUMMARY, section=SUMMARY, label="", original=original, proposed=proposed),
            text_fault(original, proposed, technologies, backing),
        )

    entries = state.resume.get(WORK_EXPERIENCE) or []
    base_entries = state.base_resume.get(WORK_EXPERIENCE) or []
    for number, edit in enumerate(proposal.entries):
        fields: dict[str, Any] = {}
        if edit.description.strip():
            fields["description"] = edit.description.strip()
        achievements = achievements_of(edit.achievements)
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
        base = base_entries[index]
        entry_backing = resume_text(base)
        fault = None
        if "description" in fields:
            fault = text_fault(
                str(base.get("description") or ""), fields["description"], technologies, entry_backing
            )
        if fault is None and "achievements" in fields:
            fault = text_fault(
                "\n".join(str(item) for item in base.get("achievements") or []),
                "\n".join(fields["achievements"]),
                technologies,
                entry_backing,
            )
        # The judge reads the whole entry on both sides: a field the proposal
        # leaves alone is shown as the base has it, not as missing.
        kept = {key: base.get(key) for key in ("description", "achievements")}
        keep(
            Part(
                id=f"entry:{number}", section=WORK_EXPERIENCE, label=_entry_label(entries[index]),
                original=_entry_text(base), proposed=_entry_text({**kept, **fields}),
                index=index, fields=fields,
            ),
            fault,
        )

    current = state.resume.get(SKILLS) if isinstance(state.resume.get(SKILLS), dict) else {}
    for bucket, names in proposal.skills.buckets().items():
        existing = [str(name) for name in current.get(bucket) or []]
        proposed_names = list(dict.fromkeys(name.strip() for name in names if name.strip()))
        keep(
            Part(
                id=f"skills:{bucket}", section=SKILLS, label=bucket,
                original=", ".join(existing), proposed=", ".join(proposed_names),
                bucket=bucket, names=proposed_names,
            ),
            skills_fault(existing, proposed_names, backing),
        )

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
