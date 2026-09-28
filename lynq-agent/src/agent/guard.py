from __future__ import annotations

import logging
import re
from typing import Any

from agent.evidence import backing_for, hits_for, searchable, skill_names
from agent.language import bare, detected_conflict
from agent.lexical import find_match, normalize, same_skill
from agent.schemas import EditProposal, EntryEdit
from agent.state import TurnState

log = logging.getLogger(__name__)

SUMMARY = "summary"
WORK_EXPERIENCE = "work_experience"
SKILLS = "skills"
ENTRY_TEXT_FIELDS = ("description", "achievements")
ENTRY_BACKING_FIELDS = ("description", "achievements", "technologies", "start_date", "end_date")

# A rewrite may say the same thing better, not say more. The slack absorbs a
# resume whose summary is one line.
LENGTH_RATIO = 2
LENGTH_SLACK = 120

# The reasons are literal and stable: docs/queries.sql groups by them, and the
# templates under resources/rejections translate them for the candidate.
UNKNOWN_ENTRY = "no such entry in work_experience"
UNBACKED_NUMBER = "a number the base resume does not carry"
UNBACKED_SKILL = "a posting skill the base resume does not back"
POSTING_WORDING = "the posting's spelling of a skill the resume spells otherwise"
TOO_LONG = "more than twice the original"
LANGUAGE_MISMATCH = "not written in the language of the resume"
NO_EVIDENCE = "no evidence in base resume"
DROPPED_SKILL = "drops a skill the resume lists"

# A number is digits standing on their own: `k8s`, `S3` and `Python3` are names.
_DIGITS = re.compile(r"(?<![A-Za-z0-9])\d+(?![A-Za-z0-9])")
_YEAR = re.compile(r"\d{4}")
_DATE_FIELDS = ("start_date", "end_date", "issue_date")


class Rejection(str):
    """`where: reason (detail)` — what was proposed and why it did not enter the
    resume. A string, so the trace and the queries read it as one; with its parts
    on the side, so the notice to the candidate can say it in their language."""

    section: str
    label: str
    reason: str
    detail: str

    def __new__(cls, section: str, label: str, reason: str, detail: str = "") -> "Rejection":
        where = section if not label else (f"{section}.{label}" if section == SKILLS else f"{section} {label}")
        rejection = super().__new__(cls, f"{where}: {reason}" + (f" ({detail})" if detail else ""))
        rejection.section, rejection.label, rejection.reason, rejection.detail = section, label, reason, detail
        return rejection


def _rejection(section: str, reason: str, detail: str = "", label: str = "") -> Rejection:
    return Rejection(section, label, reason, detail)


def _texts(*values: Any) -> list[str]:
    texts: list[str] = []
    for value in values:
        if isinstance(value, str) and value.strip():
            texts.append(value)
        elif isinstance(value, list):
            texts.extend(item for item in value if isinstance(item, str) and item.strip())
    return texts


def _numbers(texts: list[str]) -> set[str]:
    return {number for text in texts for number in _DIGITS.findall(text)}


def _backed_numbers(backing: dict[str, Any]) -> set[str]:
    """The numbers the base resume carries: every figure in its prose, and from a
    date only its year — `2020-12` backs `2020`, not the `12` of `12 years`."""
    numbers: set[str] = set()
    for path, text in searchable(backing):
        if path.rsplit(".", 1)[-1] in _DATE_FIELDS:
            numbers.update(_YEAR.findall(text))
        else:
            numbers.update(_DIGITS.findall(text))
    return numbers


def _label(entry: dict[str, Any]) -> str:
    return " at ".join(
        str(entry.get(field) or "").strip()
        for field in ("position", "company")
        if entry.get(field)
    ) or "?"


def _wordings_of(backing: dict[str, Any], skill: str) -> set[str]:
    """Every spelling the backing part of the base resume uses for a skill."""
    wordings = {normalize(hit["matched"]) for hit in hits_for(backing, skill)}
    wordings.update(normalize(name) for name in skill_names(backing) if same_skill(name, skill))
    return wordings


def _vocabulary(state: TurnState) -> set[str]:
    return {
        normalize(name) for name in skill_names(state.base_resume) + list(state.job_skills)
    }


def _check_prose(
    state: TurnState,
    section: str,
    label: str,
    proposed: list[str],
    base: list[str],
    backing: dict[str, Any],
) -> Rejection | None:
    """The rules a rewrite has to pass: it stays in the resume's language, it adds
    no number the base does not carry, it names no posting skill the base does not
    back, and it does not double the original. `backing` is the part of the base
    resume the rewrite is allowed to draw on."""
    def rejected(reason: str, detail: str = "") -> Rejection:
        return _rejection(section, reason, detail, label=label)

    unbacked_numbers = _numbers(proposed) - _backed_numbers(backing)
    if unbacked_numbers:
        return rejected(UNBACKED_NUMBER, ", ".join(sorted(unbacked_numbers)))

    for skill in state.job_skills:
        matched = next((m for m in (find_match(text, skill) for text in proposed) if m), None)
        if matched is None:
            continue
        wording = backing_for(backing, skill)
        if wording is None:
            return rejected(UNBACKED_SKILL, skill)
        # The posting's spelling of a technology the candidate spells otherwise
        # stays out of the prose too: `PostgreSQL` over a resume that says
        # `Postgres` is the posting's voice, not the candidate's.
        if normalize(matched) not in _wordings_of(backing, skill):
            return rejected(POSTING_WORDING, f"{matched}, the resume says {wording}")

    if sum(map(len, proposed)) > LENGTH_RATIO * sum(map(len, base)) + LENGTH_SLACK:
        return rejected(TOO_LONG)

    detected = detected_conflict(
        proposed,
        language=state.language,
        resume_language=state.resume_language,
        vocabulary=_vocabulary(state),
    )
    if detected is not None:
        return rejected(LANGUAGE_MISMATCH, f"{detected}, not {bare(state.resume_language)}")
    return None


def _apply_summary(state: TurnState, text: str) -> Rejection | None:
    proposed = text.strip()
    base = str(state.base_resume.get(SUMMARY) or "")
    rejection = _check_prose(state, SUMMARY, "", [proposed], [base], state.base_resume)
    if rejection is not None:
        return rejection

    if state.resume.get(SUMMARY) == proposed:
        return None
    state.resume[SUMMARY] = proposed
    state.record_change(SUMMARY, "rewrite", "rewrote the summary")
    return None


def _find_entry(entries: list[Any], edit: EntryEdit) -> int | None:
    def same(entry: Any, field: str, wanted: str) -> bool:
        return isinstance(entry, dict) and normalize(str(entry.get(field) or "")) == normalize(wanted)

    both = [
        index
        for index, entry in enumerate(entries)
        if same(entry, "company", edit.company) and same(entry, "position", edit.position)
    ]
    if len(both) == 1:
        return both[0]
    by_company = [index for index, entry in enumerate(entries) if same(entry, "company", edit.company)]
    if len(by_company) == 1 and edit.company.strip():
        return by_company[0]
    return None


def _apply_entry(state: TurnState, edit: EntryEdit) -> Rejection | None:
    label = _label({"company": edit.company, "position": edit.position})

    def rejected(reason: str, detail: str = "") -> Rejection:
        return _rejection(WORK_EXPERIENCE, reason, detail, label=label)

    fields: dict[str, Any] = {}
    if edit.description.strip():
        fields["description"] = edit.description.strip()
    achievements = [item.strip() for item in edit.achievements if item.strip()]
    if achievements:
        fields["achievements"] = achievements
    if not fields:
        # The model lists the entries it leaves alone with nothing written, as
        # the prompt tells it to: that is "keep it", not a proposal.
        return None

    entries = state.resume.get(WORK_EXPERIENCE)
    base_entries = state.base_resume.get(WORK_EXPERIENCE)
    if not isinstance(entries, list) or not isinstance(base_entries, list):
        return rejected(UNKNOWN_ENTRY)

    # Entries are never added, dropped or reordered, so the index is the same in
    # the base resume and in the one being edited.
    index = _find_entry(entries, edit)
    if index is None or index >= len(base_entries):
        return rejected(UNKNOWN_ENTRY)
    entry, base = entries[index], base_entries[index]

    rejection = _check_prose(
        state,
        WORK_EXPERIENCE,
        label,
        _texts(*fields.values()),
        _texts(*(base.get(field) for field in ENTRY_TEXT_FIELDS)),
        {field: base.get(field) for field in ENTRY_BACKING_FIELDS},
    )
    if rejection is not None:
        return rejection

    if all(entry.get(field) == value for field, value in fields.items()):
        return None
    entry.update(fields)
    state.record_change(
        WORK_EXPERIENCE,
        "rewrite",
        f"rewrote {', '.join(sorted(fields))} of {_label(entry)}",
        fields=sorted(fields),
        index=index,
    )
    return None


def _apply_skills(state: TurnState, buckets: dict[str, list[str]]) -> Rejection | None:
    accepted: dict[str, list[str]] = {}
    for bucket, names in buckets.items():
        resolved: list[str] = []
        for name in names:
            backing = backing_for(state.base_resume, name)
            if backing is None:
                return _rejection(SKILLS, NO_EVIDENCE, name, label=bucket)
            if backing not in resolved:
                resolved.append(backing)
        accepted[bucket] = resolved

    current = state.resume.get(SKILLS)
    if not isinstance(current, dict):
        current = {}

    # Replacing a bucket may reorder it and add to it, never take from it: a skill
    # the candidate listed is theirs, and dropping it is not tailoring.
    for bucket, names in accepted.items():
        for listed in current.get(bucket) or []:
            if isinstance(listed, str) and not any(same_skill(listed, name) for name in names):
                return _rejection(SKILLS, DROPPED_SKILL, listed, label=bucket)

    if all(current.get(bucket) == names for bucket, names in accepted.items()):
        return None
    current.update(accepted)
    state.resume[SKILLS] = current
    state.record_change(
        SKILLS,
        "replace",
        f"rewrote the {', '.join(sorted(accepted))} skills",
        fields=sorted(accepted),
    )
    return None


def apply(state: TurnState, proposal: EditProposal) -> list[Rejection]:
    """Puts into the resume every part of the proposal the rules let through and
    says which parts they did not. The parts are independent: a summary that fails
    does not hold back an entry that passes."""
    rejections: list[Rejection] = []

    if proposal.summary.strip():
        rejection = _apply_summary(state, proposal.summary)
        if rejection is not None:
            rejections.append(rejection)

    for edit in proposal.entries:
        rejection = _apply_entry(state, edit)
        if rejection is not None:
            rejections.append(rejection)

    buckets = proposal.skills.buckets()
    if buckets:
        rejection = _apply_skills(state, buckets)
        if rejection is not None:
            rejections.append(rejection)

    for rejection in rejections:
        log.info(
            "message= Edit rejected, conversationId=%s, reason=%s",
            state.conversation_id,
            rejection,
        )
    return rejections
