from __future__ import annotations

import copy
from dataclasses import dataclass
from typing import Any

from agent.evidence import evidenced_vocabulary, find_evidence
from agent.messages import DEFAULT_LANGUAGE, PROTOCOL_LANGUAGE, render
from agent.skill_aliases import normalize

OK = "OK"
REJECTED_PREFIX = "REJECTED: "

IMMUTABLE_SECTIONS = frozenset({"personal_info"})
FIXED_ENTRY_SECTIONS = frozenset({"work_experience", "education"})
REORDERABLE_SECTIONS = frozenset(
    {"work_experience", "education", "projects", "certifications", "languages"}
)
SKILL_BUCKETS = frozenset({"technical", "tools", "soft"})

IMMUTABLE_ENTRY_FIELDS = frozenset(
    {
        "company",
        "position",
        "institution",
        "degree",
        "field_of_study",
        "start_date",
        "end_date",
        "is_current",
    }
)

EDITABLE_ENTRY_FIELDS = frozenset({"description", "achievements", "technologies"})


@dataclass
class EditResult:
    accepted: bool
    message: str
    reason: str | None = None
    resume: dict | None = None
    change: dict | None = None
    evidence: list[str] | None = None


def _is_permutation(order: Any, size: int) -> bool:
    return (
        isinstance(order, list)
        and len(order) == size
        and all(isinstance(i, int) for i in order)
        and sorted(order) == list(range(size))
    )


class ResumeEditor:

    def __init__(
        self,
        base_resume: dict,
        current_resume: dict,
        language: str = DEFAULT_LANGUAGE,
    ) -> None:
        self.base_resume = base_resume
        self.resume = copy.deepcopy(current_resume)
        self.language = language
        self.applied_changes: list[dict] = []
        self.rejections: list[str] = []
        self._vocabulary = evidenced_vocabulary(base_resume)

    def apply(self, section: str, op: str, payload: dict | None) -> EditResult:
        result = self._dispatch(section, op, payload or {})
        if result.accepted and result.resume is not None:
            self.resume = result.resume
            self.applied_changes.append(result.change)
        elif not result.accepted:
            self.rejections.append(result.reason)
        return result

    def _rejected(self, key: str, **params) -> EditResult:
        return EditResult(
            accepted=False,
            message=REJECTED_PREFIX + render(key, PROTOCOL_LANGUAGE, **params),
            reason=render(key, self.language, **params),
        )

    def _accepted(
        self,
        resume: dict,
        section: str,
        kind: str,
        key: str,
        params: dict | None = None,
        evidence=None,
    ) -> EditResult:
        return EditResult(
            accepted=True,
            message=OK,
            resume=resume,
            change={
                "section": section,
                "kind": kind,
                "detail": render(key, self.language, **(params or {})),
            },
            evidence=evidence,
        )

    def _dispatch(self, section: str, op: str, payload: dict) -> EditResult:
        if section in IMMUTABLE_SECTIONS:
            return self._rejected("immutable_section", section=section)

        if section == "summary":
            return self._edit_summary(op, payload)
        if section == "skills":
            return self._edit_skills(op, payload)
        if section in REORDERABLE_SECTIONS:
            return self._edit_entries(section, op, payload)

        return self._rejected("unknown_section", section=section)

    def _edit_summary(self, op: str, payload: dict) -> EditResult:
        if op != "rewrite":
            return self._rejected("unsupported_op", section="summary", op=op)

        text = payload.get("text")
        if not isinstance(text, str) or not text.strip():
            return self._rejected("missing_summary_text")

        resume = copy.deepcopy(self.resume)
        resume["summary"] = text
        return self._accepted(resume, "summary", "rewrite", "summary_rewritten")

    def _edit_skills(self, op: str, payload: dict) -> EditResult:
        bucket = payload.get("bucket", "technical")
        if bucket not in SKILL_BUCKETS:
            return self._rejected("unknown_skill_bucket", bucket=bucket)

        resume = copy.deepcopy(self.resume)
        skills = resume.setdefault("skills", {})
        current = list(skills.get(bucket) or [])

        if op == "add":
            name = payload.get("name")
            if not isinstance(name, str) or not name.strip():
                return self._rejected("missing_skill_name")
            if any(normalize(name) == normalize(existing) for existing in current):
                return self._rejected(
                    "skill_already_present", name=name, bucket=bucket
                )

            evidence = find_evidence(self.base_resume, name)
            if normalize(name) not in self._vocabulary and not evidence:
                return self._rejected("unevidenced_skill", name=name)

            current.append(name)
            skills[bucket] = current
            return self._accepted(
                resume,
                "skills",
                "add",
                "skill_added",
                {"name": name, "bucket": bucket},
                evidence=evidence,
            )

        if op == "remove":
            name = payload.get("name")
            if not isinstance(name, str):
                return self._rejected("missing_skill_name")
            remaining = [s for s in current if normalize(s) != normalize(name)]
            if len(remaining) == len(current):
                return self._rejected("skill_not_present", name=name, bucket=bucket)
            skills[bucket] = remaining
            return self._accepted(
                resume,
                "skills",
                "remove",
                "skill_removed",
                {"name": name, "bucket": bucket},
            )

        if op == "reorder":
            order = payload.get("order")
            if not _is_permutation(order, len(current)):
                return self._rejected(
                    "skills_order_not_a_permutation",
                    size=len(current),
                    bucket=bucket,
                )
            skills[bucket] = [current[i] for i in order]
            return self._accepted(
                resume, "skills", "reorder", "skills_reordered", {"bucket": bucket}
            )

        return self._rejected("unsupported_op", section="skills", op=op)

    def _edit_entries(self, section: str, op: str, payload: dict) -> EditResult:
        resume = copy.deepcopy(self.resume)
        entries = list(resume.get(section) or [])

        if op == "reorder":
            order = payload.get("order")
            if not _is_permutation(order, len(entries)):
                return self._rejected(
                    "entries_order_not_a_permutation",
                    size=len(entries),
                    section=section,
                )
            resume[section] = [entries[i] for i in order]
            return self._accepted(
                resume, section, "reorder", "section_reordered", {"section": section}
            )

        if op == "rewrite":
            index = payload.get("index")
            if not isinstance(index, int) or not 0 <= index < len(entries):
                return self._rejected(
                    "index_out_of_range", section=section, size=len(entries)
                )

            entry = copy.deepcopy(entries[index])
            fields = {k: v for k, v in payload.items() if k != "index"}

            forbidden = set(fields) & IMMUTABLE_ENTRY_FIELDS
            if forbidden:
                return self._rejected(
                    "immutable_entry_fields",
                    section=section,
                    fields=", ".join(sorted(forbidden)),
                )

            unknown = set(fields) - EDITABLE_ENTRY_FIELDS
            if unknown:
                return self._rejected(
                    "non_editable_fields",
                    section=section,
                    fields=", ".join(sorted(unknown)),
                )
            if not fields:
                return self._rejected("nothing_to_rewrite")

            if "technologies" in fields:
                rejected_technology = self._first_unevidenced(fields["technologies"])
                if rejected_technology:
                    return self._rejected(
                        "unevidenced_technology", name=rejected_technology
                    )

            entry.update(fields)
            entries[index] = entry
            resume[section] = entries
            label = entry.get("company") or entry.get("institution") or f"#{index}"
            return self._accepted(
                resume,
                section,
                "rewrite",
                "section_rewritten",
                {"section": section, "label": label},
            )

        return self._rejected("unsupported_op", section=section, op=op)

    def _first_unevidenced(self, names: Any) -> str | None:
        if not isinstance(names, list):
            return None
        for name in names:
            if normalize(str(name)) in self._vocabulary:
                continue
            if find_evidence(self.base_resume, str(name)):
                continue
            return str(name)
        return None
