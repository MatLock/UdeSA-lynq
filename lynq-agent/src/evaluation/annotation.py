from __future__ import annotations

import csv
import json
import random
from dataclasses import dataclass
from typing import Any, Iterator

# What an annotator may write in the `label` column. `valid` means the change
# says only what the resume or the candidate said, in its place, in its language;
# the rest are the judge's own kinds, so that the judge can be scored against the
# human on the same vocabulary, plus `other` for what none of them names.
VALID = "valid"
KINDS = (
    "invented",
    "dropped_content",
    "reordered",
    "dropped_skill",
    "unsupported_skill",
    "language",
    "wording",
    "padding",
    "other",
)
LABELS = (VALID, *KINDS)

ANNOTATION_COLUMNS = (
    "unit_id",
    "resume_language",
    "chat_language",
    "section",
    "label_of_part",
    "original",
    "proposed",
    "candidate_said",
    "job_skills",
    "label",
    "comment",
)
KEY_COLUMNS = ("unit_id", "key", "pair", "script", "arm", "turn", "applied", "system_kind", "faults")


def load_runs(path: str) -> list[dict[str, Any]]:
    """Every turn record of a run, one per key: a key written twice (a
    conversation run again after an interruption) keeps its last record."""
    by_key: dict[str, dict[str, Any]] = {}
    with open(path, encoding="utf-8") as file:
        for line in file:
            if line.strip():
                row = json.loads(line)
                by_key[row["key"]] = row
    return list(by_key.values())


def units_in(records: list[dict[str, Any]]) -> Iterator[tuple[dict[str, Any], dict[str, Any]]]:
    for record in records:
        if record.get("error"):
            continue
        for unit in [*record.get("units", []), *record.get("rejected", [])]:
            yield record, unit


def export(records: list[dict[str, Any]], annotation_path: str, key_path: str, seed: int = 7) -> int:
    """Writes the sheet the annotators fill and the key that maps each row back
    to its arm. The sheet carries nothing that tells the arms apart: no arm, no
    system verdict, no reply; rows are shuffled so neighbours come from
    different conversations. The key stays with whoever runs the analysis."""
    rows: list[tuple[dict[str, Any], dict[str, Any]]] = list(units_in(records))
    random.Random(seed).shuffle(rows)

    with open(annotation_path, "w", encoding="utf-8", newline="") as sheet, \
            open(key_path, "w", encoding="utf-8", newline="") as key:
        sheet_writer = csv.DictWriter(sheet, fieldnames=ANNOTATION_COLUMNS)
        key_writer = csv.DictWriter(key, fieldnames=KEY_COLUMNS)
        sheet_writer.writeheader()
        key_writer.writeheader()
        for record, unit in rows:
            sheet_writer.writerow({
                "unit_id": unit["id"],
                "resume_language": record.get("resume_language", ""),
                "chat_language": record.get("language", ""),
                "section": unit["section"],
                "label_of_part": unit["label"],
                "original": unit["original"],
                "proposed": unit["proposed"],
                "candidate_said": "\n".join(record.get("candidate_said", [])),
                "job_skills": ", ".join(record.get("job_skills", [])),
                "label": "",
                "comment": "",
            })
            key_writer.writerow({
                "unit_id": unit["id"],
                "key": record["key"],
                "pair": record["pair"],
                "script": record["script"],
                "arm": record["arm"],
                "turn": record["turn"],
                "applied": unit["applied"],
                "system_kind": unit["system_kind"],
                "faults": ";".join(unit.get("faults", [])),
            })
    return len(rows)


@dataclass
class Label:
    unit_id: str
    label: str
    comment: str = ""

    @property
    def valid(self) -> bool:
        return self.label == VALID


def load_labels(path: str) -> dict[str, Label]:
    """The filled sheet. A row with no label is unlabelled and left out; a label
    outside the vocabulary stops the analysis, because a typo is not a verdict."""
    labels: dict[str, Label] = {}
    with open(path, encoding="utf-8", newline="") as file:
        for number, row in enumerate(csv.DictReader(file), 2):
            value = (row.get("label") or "").strip().lower()
            if not value:
                continue
            if value not in LABELS:
                raise ValueError(f"{path}:{number}: label {value!r} is none of {', '.join(LABELS)}")
            labels[row["unit_id"]] = Label(row["unit_id"], value, (row.get("comment") or "").strip())
    return labels
