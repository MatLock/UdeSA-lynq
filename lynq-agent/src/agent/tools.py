from __future__ import annotations

import logging
from typing import Any

from langchain_core.tools import tool
from langdetect import LangDetectException, detect

from agent.context import PERSONAL_INFO, SpanRecord, TurnState, current_turn_state
from agent.lexical import claim_words, find_match, matches, normalize
from db.models import SpanKind

log = logging.getLogger(__name__)

STEP_LIMIT_MESSAGE = (
    "STEP LIMIT REACHED: no more edits are allowed in this turn. "
    "Reply to the user with what you have already applied."
)

EDIT_LIMIT_MESSAGE = (
    "EDIT LIMIT REACHED: this turn already applied every edit it is allowed. "
    "Reply to the user with what you have already applied and recommend the changes "
    "you would make next."
)

OK = "OK"

NO_EVIDENCE = "no evidence in base resume"
IMMUTABLE_PERSONAL_INFO = "personal_info is immutable"
UNKNOWN_SECTION = "unknown section"
INVALID_PAYLOAD = "invalid payload"
INDEX_OUT_OF_RANGE = "index out of range"
NOT_EDITABLE = "field is not editable"

SUMMARY = "summary"
SKILLS = "skills"
ENTRY_SECTIONS = ("work_experience", "education", "projects")
SKILL_BUCKETS = ("technical", "tools", "soft")
EDITABLE_FIELDS = {
    "work_experience": ("description", "achievements"),
    "education": ("description",),
    "projects": ("description",),
}
MAX_EVIDENCE_HITS = 10
MAX_EVIDENCE_CLAIMS = 20
TOO_MANY_CLAIMS = f"at most {MAX_EVIDENCE_CLAIMS} claims per call"
NO_CLAIMS = "claims must hold at least one non-empty string"
MIN_DETECTABLE_CHARS = 80


def _language_of(code: str) -> str:
    return code.split("-")[0].split("_")[0].strip().lower()


def _skill_names(resume: dict[str, Any]) -> list[str]:
    names: list[str] = []
    buckets = resume.get(SKILLS)
    if isinstance(buckets, dict):
        for bucket in SKILL_BUCKETS:
            names.extend(
                value for value in buckets.get(bucket) or [] if isinstance(value, str)
            )
    elif isinstance(buckets, list):
        names.extend(value for value in buckets if isinstance(value, str))

    for section in ("work_experience", "projects"):
        for entry in resume.get(section) or []:
            if isinstance(entry, dict):
                names.extend(
                    value
                    for value in entry.get("technologies") or []
                    if isinstance(value, str)
                )
    return names


def _evidenced_skills(state: TurnState) -> dict[str, str]:
    allowed = {normalize(name): name for name in _skill_names(state.base_resume)}
    allowed.update(state.evidence)
    return {key: value for key, value in allowed.items() if key}


def _backing_for(name: str, allowed: dict[str, str]) -> str | None:
    wanted = normalize(name)
    if wanted in allowed:
        return allowed[wanted]
    for evidenced, backing in allowed.items():
        if matches(evidenced, wanted):
            return backing
    return None


def _searchable(node: Any, path: str = "") -> list[tuple[str, str]]:
    if isinstance(node, str):
        return [(path, node)] if node.strip() else []
    if isinstance(node, dict):
        found: list[tuple[str, str]] = []
        for key, value in node.items():
            if not path and key == PERSONAL_INFO:
                continue
            found.extend(_searchable(value, f"{path}.{key}" if path else key))
        return found
    if isinstance(node, list):
        found = []
        for index, item in enumerate(node):
            found.extend(_searchable(item, f"{path}[{index}]"))
        return found
    return []


def _rejected(reason: str) -> str:
    return f"REJECTED: {reason}"


def _step_limit_hit(state: TurnState, tool_name: str) -> bool:
    if not state.at_step_limit():
        return False
    if not state.limit_reported:
        state.limit_reported = True
        state.spans.append(
            SpanRecord(
                step=state.steps,
                kind=SpanKind.LIMIT,
                name="max_steps",
                input=tool_name,
                output=STEP_LIMIT_MESSAGE,
            )
        )
        log.warning(
            "message= Soft step limit reached, conversationId=%s, maxSteps=%s",
            state.conversation_id,
            state.max_steps,
        )
    return True


def _edit_limit_hit(state: TurnState, tool_name: str) -> bool:
    if not state.at_edit_limit():
        return False
    if not state.edit_limit_reported:
        state.edit_limit_reported = True
        state.spans.append(
            SpanRecord(
                step=state.steps,
                kind=SpanKind.LIMIT,
                name="max_edits",
                input=tool_name,
                output=EDIT_LIMIT_MESSAGE,
            )
        )
        log.info(
            "message= Edit budget spent, conversationId=%s, maxEdits=%s",
            state.conversation_id,
            state.max_edits,
        )
    return True


def _detected_conflict(state: TurnState, texts: list[str]) -> str | None:
    if _language_of(state.language) == _language_of(state.resume_language):
        return None

    vocabulary = {
        normalize(name)
        for name in _skill_names(state.base_resume) + list(state.job_skills)
    }
    for text in texts:
        prose = " ".join(
            word for word in text.split() if normalize(word) not in vocabulary
        )
        if len(prose) <= MIN_DETECTABLE_CHARS:
            continue
        try:
            detected = _language_of(detect(prose))
        except LangDetectException:
            continue
        if detected == _language_of(state.language) != _language_of(
            state.resume_language
        ):
            return detected
    return None


def _prose_of(payload: dict[str, Any]) -> list[str]:
    texts: list[str] = []
    for value in payload.values():
        if isinstance(value, str):
            texts.append(value)
        elif isinstance(value, list):
            texts.extend(item for item in value if isinstance(item, str))
    return texts


def _record_change(
    state: TurnState,
    section: str,
    kind: str,
    detail: str,
    fields: list[str] | None = None,
    index: int | None = None,
) -> str:
    state.changes.append(
        {
            "section": section,
            "kind": kind,
            "detail": detail,
            "fields": fields or [],
            "index": index,
        }
    )
    log.info(
        "message= Edit applied, conversationId=%s, section=%s, op=%s",
        state.conversation_id,
        section,
        kind,
    )
    return OK


FIND_EVIDENCE_DESCRIPTION = """Look in the candidate's base resume for wording that backs one or more claims.

Call it before putting any skill into the resume. It never judges: it is a literal
search over the resume the candidate already wrote.

claims: the list of skills or statements to look for, for example
["PostgreSQL", "Kubernetes", "Docker"]. Ask for every claim you need in one call: the
whole call costs a single step, so one call with ten claims leaves you nine more steps
to edit with than ten calls do. At most 20 claims per call.

It answers a list of {"claim", "hits"} objects, one per claim you asked for, where each
hit is a {"path", "matched"} and "matched" is the text the resume itself uses. An empty
"hits" means the resume does not back that claim."""


def _asked_claims(claims: Any) -> list[str]:
    values = [claims] if isinstance(claims, str) else claims
    if not isinstance(values, list):
        return []

    wanted: list[str] = []
    seen: set[str] = set()
    for value in values:
        if not isinstance(value, str):
            continue
        trimmed = value.strip()
        if not trimmed or trimmed.lower() in seen:
            continue
        seen.add(trimmed.lower())
        wanted.append(trimmed)
    return wanted


def _hits_for(state: TurnState, claim: str) -> list[dict[str, str]]:
    wanted = claim_words(claim)
    hits: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()

    for path, text in _searchable(state.base_resume):
        matched = find_match(text, wanted)
        if matched is None or (path, matched) in seen:
            continue
        seen.add((path, matched))
        state.remember_evidence(normalize(matched), matched)
        hits.append({"path": path, "matched": matched})
        if len(hits) >= MAX_EVIDENCE_HITS:
            break

    return hits


@tool(description=FIND_EVIDENCE_DESCRIPTION)
async def find_evidence(claims: list[str]) -> Any:
    state = current_turn_state()
    if _step_limit_hit(state, "find_evidence"):
        return STEP_LIMIT_MESSAGE

    asked = _asked_claims(claims)
    if not asked:
        return _rejected(NO_CLAIMS)
    if len(asked) > MAX_EVIDENCE_CLAIMS:
        return _rejected(TOO_MANY_CLAIMS)

    found = [{"claim": claim, "hits": _hits_for(state, claim)} for claim in asked]

    log.info(
        "message= Evidence looked up, conversationId=%s, claims=%s, backed=%s",
        state.conversation_id,
        len(asked),
        sum(1 for entry in found if entry["hits"]),
    )
    return found


def _language_mismatch(state: TurnState, conflict: str) -> str:
    return (
        f"payload language ({conflict}) does not match resume language "
        f"({_language_of(state.resume_language)})"
    )


def _limit_answer(state: TurnState, tool_name: str) -> str | None:
    if _step_limit_hit(state, tool_name):
        return STEP_LIMIT_MESSAGE
    if _edit_limit_hit(state, tool_name):
        return EDIT_LIMIT_MESSAGE
    return None


def _entries_of(state: TurnState, section: str) -> list[Any]:
    entries = state.resume.get(section)
    if not isinstance(entries, list):
        entries = []
        state.resume[section] = entries
    return entries


def _entry_section(state: TurnState, section: str) -> tuple[str | None, str]:
    named = (section or "").strip()
    if named == PERSONAL_INFO:
        return None, _rejected(IMMUTABLE_PERSONAL_INFO)
    if named not in ENTRY_SECTIONS:
        return None, _rejected(UNKNOWN_SECTION)
    return named, ""


def _written_fields(
    description: str, achievements: list[str]
) -> dict[str, Any] | None:
    fields: dict[str, Any] = {}
    if isinstance(description, str) and description.strip():
        fields["description"] = description.strip()
    if isinstance(achievements, list) and achievements:
        if any(not isinstance(item, str) or not item.strip() for item in achievements):
            return None
        fields["achievements"] = [item.strip() for item in achievements]
    return fields or None


def _reorder(state: TurnState, section: str, entries: list[Any], order: list[int]) -> str:
    if not isinstance(order, list) or any(
        not isinstance(position, int) or isinstance(position, bool)
        for position in order
    ):
        return _rejected(INVALID_PAYLOAD)
    if sorted(order) != list(range(len(entries))):
        return _rejected(
            f"order must list each of the {len(entries)} positions of {section} "
            f"exactly once, from 0 to {len(entries) - 1}"
        )
    if order == sorted(order):
        return OK

    state.resume[section] = [entries[position] for position in order]
    return _record_change(state, section, "reorder", f"reordered {section} as {order}")


def _backed_buckets(
    state: TurnState, buckets: dict[str, list[str]]
) -> tuple[dict[str, list[str]] | None, str]:
    allowed = _evidenced_skills(state)
    accepted: dict[str, list[str]] = {}
    for bucket, names in buckets.items():
        resolved: list[str] = []
        for name in names:
            backing = _backing_for(name, allowed)
            if backing is None:
                log.info(
                    "message= Skill rejected for lack of evidence, "
                    "conversationId=%s, skill=%s",
                    state.conversation_id,
                    name,
                )
                return None, _rejected(NO_EVIDENCE)
            if backing not in resolved:
                resolved.append(backing)
        accepted[bucket] = resolved
    return accepted, ""


REWRITE_SUMMARY_DESCRIPTION = """Rewrite the summary of the resume that is being tailored.

text: the whole summary as it should read once you are done, in the language the resume
is written in. It replaces what is there; there is no way to append to it.

It answers "OK", or "REJECTED: <reason>" when a rule of the resume forbids it."""

REWRITE_ENTRY_DESCRIPTION = """Rewrite one entry of the experience, the education or the projects.

section: "work_experience", "education" or "projects".
index: which entry of that section, counting from 0 in the order the resume shows them.
description: the whole description of that entry as it should read once you are done.
achievements: the whole list of achievements, replacing the one that is there. Only
work_experience has achievements; education and projects are rejected for it.

Send description, achievements or both, and leave empty what you are not changing. The
company, the position, the institution and every date of an entry are not yours to
change, and there is no way to add an entry: the resume only holds what the candidate
already lived.

It answers "OK", or "REJECTED: <reason>" when a rule of the resume forbids it."""

REORDER_ENTRIES_DESCRIPTION = """Reorder the entries of the experience, the education or the projects.

section: "work_experience", "education" or "projects".
order: the positions the section already has, in the order you want them, each one
exactly once. [2, 0, 1] puts the third entry first. Nothing is added or dropped by
reordering.

It answers "OK", or "REJECTED: <reason>" when a rule of the resume forbids it."""

REPLACE_SKILLS_DESCRIPTION = """Replace the skills of the resume, bucket by bucket.

technical, tools, soft: the whole list of that bucket as it should read once you are
done. A bucket you leave empty is kept as it is.

Every skill has to be backed by the resume, with the wording find_evidence returned. One
skill without evidence rejects the whole call.

It answers "OK", or "REJECTED: <reason>" when a rule of the resume forbids it."""


@tool(description=REWRITE_SUMMARY_DESCRIPTION)
async def rewrite_summary(text: str) -> str:
    state = current_turn_state()
    limit = _limit_answer(state, "rewrite_summary")
    if limit is not None:
        return limit

    if not isinstance(text, str) or not text.strip():
        return _rejected(INVALID_PAYLOAD)

    conflict = _detected_conflict(state, [text])
    if conflict is not None:
        return _rejected(_language_mismatch(state, conflict))

    if state.resume.get(SUMMARY) == text.strip():
        return OK

    state.resume[SUMMARY] = text.strip()
    return _record_change(state, SUMMARY, "rewrite", "rewrote the summary")


@tool(description=REWRITE_ENTRY_DESCRIPTION)
async def rewrite_entry(
    section: str,
    index: int,
    description: str = "",
    achievements: list[str] = [],
) -> str:
    state = current_turn_state()
    limit = _limit_answer(state, "rewrite_entry")
    if limit is not None:
        return limit

    named, rejection = _entry_section(state, section)
    if named is None:
        return rejection

    entries = _entries_of(state, named)
    if not isinstance(index, int) or isinstance(index, bool):
        return _rejected(INVALID_PAYLOAD)
    if not 0 <= index < len(entries):
        return _rejected(INDEX_OUT_OF_RANGE)

    fields = _written_fields(description, achievements)
    if fields is None:
        return _rejected(INVALID_PAYLOAD)
    if any(field not in EDITABLE_FIELDS[named] for field in fields):
        return _rejected(NOT_EDITABLE)

    conflict = _detected_conflict(state, _prose_of(fields))
    if conflict is not None:
        return _rejected(_language_mismatch(state, conflict))

    entries[index].update(fields)
    return _record_change(
        state,
        named,
        "rewrite",
        f"rewrote {', '.join(sorted(fields))} of entry {index}",
        fields=sorted(fields),
        index=index,
    )


@tool(description=REORDER_ENTRIES_DESCRIPTION)
async def reorder_entries(section: str, order: list[int]) -> str:
    state = current_turn_state()
    limit = _limit_answer(state, "reorder_entries")
    if limit is not None:
        return limit

    named, rejection = _entry_section(state, section)
    if named is None:
        return rejection

    return _reorder(state, named, _entries_of(state, named), order)


@tool(description=REPLACE_SKILLS_DESCRIPTION)
async def replace_skills(
    technical: list[str] = [],
    tools: list[str] = [],
    soft: list[str] = [],
) -> str:
    state = current_turn_state()
    limit = _limit_answer(state, "replace_skills")
    if limit is not None:
        return limit

    asked = {"technical": technical, "tools": tools, "soft": soft}
    if any(
        not isinstance(names, list) or any(not isinstance(name, str) for name in names)
        for names in asked.values()
    ):
        return _rejected(INVALID_PAYLOAD)

    buckets = {bucket: names for bucket, names in asked.items() if names}
    if not buckets:
        return _rejected(INVALID_PAYLOAD)

    accepted, rejection = _backed_buckets(state, buckets)
    if accepted is None:
        return rejection

    current = state.resume.get(SKILLS)
    if not isinstance(current, dict):
        current = {}
    if all(current.get(bucket) == names for bucket, names in accepted.items()):
        return OK

    current.update(accepted)
    state.resume[SKILLS] = current
    return _record_change(
        state,
        SKILLS,
        "replace",
        f"rewrote the {', '.join(sorted(accepted))} skills",
        fields=sorted(accepted),
    )


EDIT_TOOLS = (rewrite_summary, rewrite_entry, reorder_entries, replace_skills)
