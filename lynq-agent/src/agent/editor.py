from __future__ import annotations

import copy
from dataclasses import dataclass
from typing import Any

from agent.evidence import evidenced_vocabulary, find_evidence
from agent.skill_aliases import normalize

OK = "OK"
REJECTED_PREFIX = "RECHAZADO: "

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
    resume: dict | None = None
    change: dict | None = None
    evidence: list[str] | None = None


def _rejected(reason: str) -> EditResult:
    return EditResult(accepted=False, message=REJECTED_PREFIX + reason)


def _accepted(resume: dict, section: str, kind: str, detail: str, evidence=None):
    return EditResult(
        accepted=True,
        message=OK,
        resume=resume,
        change={"section": section, "kind": kind, "detail": detail},
        evidence=evidence,
    )


def _is_permutation(order: Any, size: int) -> bool:
    return (
        isinstance(order, list)
        and len(order) == size
        and all(isinstance(i, int) for i in order)
        and sorted(order) == list(range(size))
    )


class ResumeEditor:

    def __init__(self, base_resume: dict, current_resume: dict) -> None:
        self.base_resume = base_resume
        self.resume = copy.deepcopy(current_resume)
        self.applied_changes: list[dict] = []
        self.rejections: list[str] = []
        self._vocabulary = evidenced_vocabulary(base_resume)

    def apply(self, section: str, op: str, payload: dict | None) -> EditResult:
        result = self._dispatch(section, op, payload or {})
        if result.accepted and result.resume is not None:
            self.resume = result.resume
            self.applied_changes.append(result.change)
        elif not result.accepted:
            self.rejections.append(result.message)
        return result

    def _dispatch(self, section: str, op: str, payload: dict) -> EditResult:
        if section in IMMUTABLE_SECTIONS:
            return _rejected(
                f"{section} es inmutable: no se puede editar el nombre, el mail, "
                "el telefono ni los links del candidato"
            )

        if section == "summary":
            return self._edit_summary(op, payload)
        if section == "skills":
            return self._edit_skills(op, payload)
        if section in REORDERABLE_SECTIONS:
            return self._edit_entries(section, op, payload)

        return _rejected(f"seccion desconocida: {section}")

    def _edit_summary(self, op: str, payload: dict) -> EditResult:
        if op != "rewrite":
            return _rejected(f"operacion no soportada para summary: {op}")

        text = payload.get("text")
        if not isinstance(text, str) or not text.strip():
            return _rejected("falta el texto del summary")

        resume = copy.deepcopy(self.resume)
        resume["summary"] = text
        return _accepted(resume, "summary", "rewrite", "summary reescrito")

    def _edit_skills(self, op: str, payload: dict) -> EditResult:
        bucket = payload.get("bucket", "technical")
        if bucket not in SKILL_BUCKETS:
            return _rejected(f"bucket de skills desconocido: {bucket}")

        resume = copy.deepcopy(self.resume)
        skills = resume.setdefault("skills", {})
        current = list(skills.get(bucket) or [])

        if op == "add":
            name = payload.get("name")
            if not isinstance(name, str) or not name.strip():
                return _rejected("falta el nombre de la skill")
            if any(normalize(name) == normalize(existing) for existing in current):
                return _rejected(f"{name} ya esta en skills.{bucket}")

            evidence = find_evidence(self.base_resume, name)
            if normalize(name) not in self._vocabulary and not evidence:
                return _rejected(
                    f"no hay evidencia de {name} en el CV base: no se agrega una "
                    "skill que el candidato no pueda respaldar"
                )

            current.append(name)
            skills[bucket] = current
            return _accepted(
                resume,
                "skills",
                "add",
                f"{name} agregada a skills.{bucket}",
                evidence=evidence,
            )

        if op == "remove":
            name = payload.get("name")
            if not isinstance(name, str):
                return _rejected("falta el nombre de la skill")
            remaining = [s for s in current if normalize(s) != normalize(name)]
            if len(remaining) == len(current):
                return _rejected(f"{name} no esta en skills.{bucket}")
            skills[bucket] = remaining
            return _accepted(
                resume, "skills", "remove", f"{name} quitada de skills.{bucket}"
            )

        if op == "reorder":
            order = payload.get("order")
            if not _is_permutation(order, len(current)):
                return _rejected(
                    f"order tiene que ser una permutacion de los {len(current)} "
                    f"elementos de skills.{bucket}"
                )
            skills[bucket] = [current[i] for i in order]
            return _accepted(
                resume, "skills", "reorder", f"skills.{bucket} reordenadas"
            )

        return _rejected(f"operacion no soportada para skills: {op}")

    def _edit_entries(self, section: str, op: str, payload: dict) -> EditResult:
        resume = copy.deepcopy(self.resume)
        entries = list(resume.get(section) or [])

        if op == "reorder":
            order = payload.get("order")
            if not _is_permutation(order, len(entries)):
                return _rejected(
                    f"order tiene que ser una permutacion de las {len(entries)} "
                    f"entradas de {section}: no se agregan ni se borran entradas"
                )
            resume[section] = [entries[i] for i in order]
            return _accepted(resume, section, "reorder", f"{section} reordenado")

        if op == "rewrite":
            index = payload.get("index")
            if not isinstance(index, int) or not 0 <= index < len(entries):
                return _rejected(
                    f"index fuera de rango para {section}: hay {len(entries)} entradas"
                )

            entry = copy.deepcopy(entries[index])
            fields = {k: v for k, v in payload.items() if k != "index"}

            forbidden = set(fields) & IMMUTABLE_ENTRY_FIELDS
            if forbidden:
                return _rejected(
                    "no se pueden cambiar los datos duros de una entrada de "
                    f"{section}: {', '.join(sorted(forbidden))}"
                )

            unknown = set(fields) - EDITABLE_ENTRY_FIELDS
            if unknown:
                return _rejected(
                    f"campos no editables en {section}: {', '.join(sorted(unknown))}"
                )
            if not fields:
                return _rejected("no hay nada que reescribir")

            if "technologies" in fields:
                rejected_technology = self._first_unevidenced(fields["technologies"])
                if rejected_technology:
                    return _rejected(
                        f"no hay evidencia de {rejected_technology} en el CV base"
                    )

            entry.update(fields)
            entries[index] = entry
            resume[section] = entries
            label = entry.get("company") or entry.get("institution") or f"#{index}"
            return _accepted(
                resume, section, "rewrite", f"{section} reescrito en {label}"
            )

        return _rejected(f"operacion no soportada para {section}: {op}")

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
