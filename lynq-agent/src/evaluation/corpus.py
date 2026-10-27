from __future__ import annotations

import json
import string
from dataclasses import dataclass, field
from typing import Any

from agent.apply import gaps_in

# Placeholders a script may use. They are filled from the pair, so that the same
# script asks every candidate about their own gaps and their own jobs.
GAP = "gap"
COMPANY = "company"
POSITION = "position"


@dataclass
class Pair:
    """One (job posting, base resume) pair of the corpus, without personal data."""

    id: str
    job: dict[str, Any]
    base_resume: dict[str, Any]
    language: str = "es"
    resume_language: str = "es"
    category: str = ""
    source: str = ""

    @property
    def job_skills(self) -> list[str]:
        return list(self.job.get("extractedSkills") or self.job.get("skills") or [])

    @property
    def gaps(self) -> list[str]:
        return gaps_in(self.job_skills, self.base_resume, [])

    @property
    def entries(self) -> list[dict[str, Any]]:
        return [e for e in self.base_resume.get("work_experience") or [] if isinstance(e, dict)]


@dataclass
class Script:
    """A scripted conversation: the candidate's messages, in order, with
    placeholders the pair fills (`{gap1}`, `{company1}`, `{position1}`...)."""

    id: str
    turns: list[str]
    purpose: str = ""
    fields: set[str] = field(default_factory=set)

    def __post_init__(self) -> None:
        self.fields = {
            name
            for turn in self.turns
            for _, name, _, _ in string.Formatter().parse(turn)
            if name
        }


def load_pairs(path: str) -> list[Pair]:
    pairs: list[Pair] = []
    with open(path, encoding="utf-8") as file:
        for number, line in enumerate(file, 1):
            if not line.strip():
                continue
            row = json.loads(line)
            try:
                pairs.append(
                    Pair(
                        id=str(row["id"]),
                        job=row["job"],
                        base_resume=row["base_resume"],
                        language=row.get("language") or "es",
                        resume_language=row.get("resume_language") or "es",
                        category=row.get("category") or "",
                        source=row.get("source") or "",
                    )
                )
            except KeyError as missing:
                raise ValueError(f"{path}:{number}: pair without {missing}") from None
    ids = [pair.id for pair in pairs]
    if len(ids) != len(set(ids)):
        raise ValueError(f"{path}: pair ids repeat")
    return pairs


def load_scripts(path: str) -> list[Script]:
    with open(path, encoding="utf-8") as file:
        document = json.load(file)
    scripts = [
        Script(id=str(row["id"]), turns=list(row["turns"]), purpose=row.get("purpose") or "")
        for row in document["scripts"]
    ]
    ids = [script.id for script in scripts]
    if len(ids) != len(set(ids)):
        raise ValueError(f"{path}: script ids repeat")
    return scripts


def placeholders(pair: Pair) -> dict[str, str]:
    """What a script may name about a pair: `gap1..gapN` are the posting's skills
    the resume does not back, `company1`/`position1` the most recent entry (the
    first one, as resumes list them) and `companyN` the N-th."""
    values: dict[str, str] = {"job_title": str(pair.job.get("title") or "")}
    for number, gap in enumerate(pair.gaps, 1):
        values[f"{GAP}{number}"] = gap
    for number, entry in enumerate(pair.entries, 1):
        values[f"{COMPANY}{number}"] = str(entry.get("company") or "")
        values[f"{POSITION}{number}"] = str(entry.get("position") or "")
    return values


def fill(script: Script, pair: Pair) -> list[str] | None:
    """The script's turns written for this pair, or None when the pair cannot
    fill a placeholder the script needs (a resume with no gaps cannot be asked
    about its second gap): that script is skipped for that pair, and counted."""
    values = placeholders(pair)
    if any(name not in values or not values[name] for name in script.fields):
        return None
    return [turn.format_map(values) for turn in script.turns]
