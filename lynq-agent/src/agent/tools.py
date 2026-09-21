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

OK = "OK"

NO_EVIDENCE = "no evidence in base resume"
IMMUTABLE_PERSONAL_INFO = "personal_info is immutable"
IMMUTABLE_DATES = "dates are immutable"
NO_NEW_ENTRIES = "new entries are not allowed"
UNKNOWN_SECTION = "unknown section"
UNKNOWN_OP = "unknown op for this section"
INVALID_PAYLOAD = "invalid payload"
INDEX_OUT_OF_RANGE = "index out of range"
NOT_EDITABLE = "field is not editable"

SUMMARY = "summary"
SKILLS = "skills"
ENTRY_SECTIONS = ("work_experience", "education", "projects")
SKILL_BUCKETS = ("technical", "tools", "soft")
DATE_FIELDS = ("start_date", "end_date", "is_current")
EDITABLE_FIELDS = {
    "work_experience": ("description", "achievements"),
    "education": ("description",),
    "projects": ("description",),
}
ADDING_OPS = ("add", "append", "insert", "create")
MAX_EVIDENCE_HITS = 10
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


def _record_change(state: TurnState, section: str, kind: str, detail: str) -> str:
    state.changes.append({"section": section, "kind": kind, "detail": detail})
    log.info(
        "message= Edit applied, conversationId=%s, section=%s, op=%s",
        state.conversation_id,
        section,
        kind,
    )
    return OK


def _edit_summary(state: TurnState, op: str, payload: dict[str, Any]) -> str:
    if op != "rewrite":
        return _rejected(UNKNOWN_OP)
    text = payload.get("text")
    if not isinstance(text, str) or not text.strip():
        return _rejected(INVALID_PAYLOAD)

    conflict = _detected_conflict(state, [text])
    if conflict is not None:
        return _rejected(
            f"payload language ({conflict}) does not match resume language "
            f"({_language_of(state.resume_language)})"
        )

    if state.resume.get(SUMMARY) == text.strip():
        return OK

    state.resume[SUMMARY] = text.strip()
    return _record_change(state, SUMMARY, op, "rewrote the summary")


def _edit_entries(
    state: TurnState, section: str, op: str, payload: dict[str, Any]
) -> str:
    entries = state.resume.get(section)
    if not isinstance(entries, list):
        entries = []
        state.resume[section] = entries

    if op in ADDING_OPS:
        return _rejected(NO_NEW_ENTRIES)
    if op == "reorder":
        return _reorder_entries(state, section, entries, payload)
    if op != "rewrite":
        return _rejected(UNKNOWN_OP)

    index = payload.get("index")
    if not isinstance(index, int) or isinstance(index, bool):
        return _rejected(INVALID_PAYLOAD)
    if not 0 <= index < len(entries):
        return _rejected(INDEX_OUT_OF_RANGE)

    fields = {key: value for key, value in payload.items() if key != "index"}
    if not fields:
        return _rejected(INVALID_PAYLOAD)
    if any(field in fields for field in DATE_FIELDS):
        return _rejected(IMMUTABLE_DATES)
    if any(field not in EDITABLE_FIELDS[section] for field in fields):
        return _rejected(NOT_EDITABLE)

    conflict = _detected_conflict(state, _prose_of(fields))
    if conflict is not None:
        return _rejected(
            f"payload language ({conflict}) does not match resume language "
            f"({_language_of(state.resume_language)})"
        )

    entries[index].update(fields)
    return _record_change(
        state, section, op, f"rewrote {', '.join(sorted(fields))} of entry {index}"
    )


def _reorder_entries(
    state: TurnState, section: str, entries: list[Any], payload: dict[str, Any]
) -> str:
    order = payload.get("order")
    if not isinstance(order, list) or any(
        not isinstance(position, int) or isinstance(position, bool)
        for position in order
    ):
        return _rejected(INVALID_PAYLOAD)
    if len(order) != len(entries):
        return _rejected(NO_NEW_ENTRIES)
    if sorted(order) != list(range(len(entries))):
        return _rejected(INVALID_PAYLOAD)

    state.resume[section] = [entries[position] for position in order]
    return _record_change(state, section, "reorder", f"reordered {section} as {order}")


def _edit_skills(state: TurnState, op: str, payload: dict[str, Any]) -> str:
    if op not in ("replace", "rewrite", "reorder"):
        return _rejected(UNKNOWN_OP)

    buckets = {
        bucket: value for bucket, value in payload.items() if bucket in SKILL_BUCKETS
    }
    if not buckets or any(
        not isinstance(value, list)
        or any(not isinstance(name, str) for name in value)
        for value in buckets.values()
    ):
        return _rejected(INVALID_PAYLOAD)

    allowed = _evidenced_skills(state)
    accepted: dict[str, list[str]] = {}
    for bucket, names in buckets.items():
        resolved = []
        for name in names:
            backing = _backing_for(name, allowed)
            if backing is None:
                log.info(
                    "message= Skill rejected for lack of evidence, "
                    "conversationId=%s, skill=%s",
                    state.conversation_id,
                    name,
                )
                return _rejected(NO_EVIDENCE)
            if backing not in resolved:
                resolved.append(backing)
        accepted[bucket] = resolved

    current = state.resume.get(SKILLS)
    if not isinstance(current, dict):
        current = {}
    if all(current.get(bucket) == names for bucket, names in accepted.items()):
        return OK

    current.update(accepted)
    state.resume[SKILLS] = current
    return _record_change(
        state, SKILLS, "replace", f"rewrote the {', '.join(sorted(accepted))} skills"
    )


@tool
async def find_evidence(claim: str) -> Any:
    """Look for wording in the candidate's base resume that backs a claim.

    Use it before putting any skill into the resume. It never judges: it is a
    literal search over the resume the candidate already wrote, and it returns
    the matching text so the resume keeps the candidate's own wording.

    Args:
        claim: the skill or statement to look for, e.g. "PostgreSQL".

    Returns:
        A list of {"path", "matched"} objects, empty when the resume does not
        back the claim.
    """
    state = current_turn_state()
    if _step_limit_hit(state, "find_evidence"):
        return STEP_LIMIT_MESSAGE

    wanted = claim_words(claim or "")
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

    log.info(
        "message= Evidence looked up, conversationId=%s, claim=%s, hits=%s",
        state.conversation_id,
        claim,
        len(hits),
    )
    return hits


@tool
async def apply_edit(section: str, op: str, payload: dict[str, Any]) -> str:
    """Apply one change to the resume that is being tailored.

    Sections and ops:
      - summary: op "rewrite", payload {"text": "..."}
      - work_experience, education, projects: op "rewrite", payload
        {"index": 0, "description": "...", "achievements": ["..."]}; or op
        "reorder", payload {"order": [2, 0, 1]}
      - skills: op "replace", payload {"technical": [...], "tools": [...],
        "soft": [...]}

    Args:
        section: the resume section to change.
        op: what to do with it.
        payload: the change itself.

    Returns:
        "OK", or "REJECTED: <reason>" when a rule of the resume forbids it.
    """
    state = current_turn_state()
    if _step_limit_hit(state, "apply_edit"):
        return STEP_LIMIT_MESSAGE

    section = (section or "").strip()
    op = (op or "").strip().lower()

    if section == PERSONAL_INFO:
        return _rejected(IMMUTABLE_PERSONAL_INFO)
    if section == SUMMARY:
        return _edit_summary(state, op, payload)
    if section in ENTRY_SECTIONS:
        return _edit_entries(state, section, op, payload)
    if section == SKILLS:
        return _edit_skills(state, op, payload)
    return _rejected(UNKNOWN_SECTION)
