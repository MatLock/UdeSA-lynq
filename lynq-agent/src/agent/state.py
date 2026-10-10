from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from agent.context import SpanRecord, TurnContext, deep_copy
from db.models import MessageRole

PERSONAL_INFO = "personal_info"


@dataclass
class TurnState:
    """What one turn works on: the resume being edited, with `personal_info` split
    off so that no model ever reads it, and everything the turn produces."""

    conversation_id: str
    run_token: str
    language: str
    resume_language: str
    base_resume: dict[str, Any]
    personal_info: dict[str, Any]
    resume: dict[str, Any]
    job_skills: list[str]
    statements: list[str] = field(default_factory=list)
    confirmed: list[str] = field(default_factory=list)
    changes: list[dict[str, Any]] = field(default_factory=list)
    spans: list[SpanRecord] = field(default_factory=list)
    steps: int = 0

    def next_step(self) -> int:
        self.steps += 1
        return self.steps

    def confirm(self, facts: list[str]) -> None:
        for fact in facts:
            fact = " ".join(str(fact or "").split())
            if fact and fact not in self.confirmed and fact not in self.statements:
                self.confirmed.append(fact)

    def evidence(self, excluding: str = "") -> list[str]:
        spoken = " ".join(excluding.split())
        return [fact for fact in [*self.statements, *self.confirmed] if fact != spoken]

    def record_change(
        self,
        section: str,
        kind: str,
        detail: str,
        fields: list[str] | None = None,
        index: int | None = None,
    ) -> None:
        # A part rewritten twice in one turn (the retry re-proposed it) is one
        # change, the last one: the candidate reads the timeline, not the attempts.
        self.changes = [
            change
            for change in self.changes
            if (change["section"], change["index"]) != (section, index)
        ]
        self.changes.append(
            {
                "section": section,
                "kind": kind,
                "detail": detail,
                "fields": fields or [],
                "index": index,
            }
        )

    def serialize_resume(self) -> dict[str, Any]:
        serialized = dict(self.resume)
        if self.personal_info:
            serialized[PERSONAL_INFO] = self.personal_info
        return serialized


def without_personal_info(
    resume: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    editable = {key: value for key, value in resume.items() if key != PERSONAL_INFO}
    return deep_copy(editable), deep_copy(resume.get(PERSONAL_INFO) or {})


def statements_of(context: TurnContext) -> list[str]:
    spoken = context.statements or [
        content for role, content in context.history if role == MessageRole.USER
    ]
    return list(dict.fromkeys(
        text for text in (" ".join(str(item or "").split()) for item in [*spoken, context.message])
        if text
    ))


def build_turn_state(context: TurnContext) -> TurnState:
    resume, personal_info = without_personal_info(context.current_resume)
    return TurnState(
        conversation_id=context.conversation_id,
        run_token=context.run_token,
        language=context.language,
        resume_language=context.resume_language,
        base_resume=context.base_resume,
        personal_info=personal_info,
        resume=resume,
        job_skills=list(context.job_snapshot.get("extractedSkills") or []),
        statements=statements_of(context),
        spans=context.spans,
    )
