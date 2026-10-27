from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any, Iterator

from agent.context import SpanRecord, TurnContext, deep_copy

PERSONAL_INFO = "personal_info"
DEFAULT_MAX_EDITS = 2


@dataclass
class TurnState:
    """What the ReAct loop worked on: the resume with `personal_info` split off,
    the evidence the tools found, and the step and edit budgets the loop spent."""

    conversation_id: str
    run_token: str
    language: str
    resume_language: str
    max_steps: int
    base_resume: dict[str, Any]
    personal_info: dict[str, Any]
    resume: dict[str, Any]
    job_skills: list[str]
    evidence: dict[str, str] = field(default_factory=dict)
    changes: list[dict[str, Any]] = field(default_factory=list)
    spans: list[SpanRecord] = field(default_factory=list)
    steps: int = 0
    max_edits: int = DEFAULT_MAX_EDITS
    limit_reported: bool = False
    edit_limit_reported: bool = False

    def next_step(self) -> int:
        self.steps += 1
        return self.steps

    def at_step_limit(self) -> bool:
        return self.steps >= self.max_steps

    def at_edit_limit(self) -> bool:
        return len(self.changes) >= self.max_edits

    def remember_evidence(self, normalized: str, matched: str) -> None:
        self.evidence.setdefault(normalized, matched)

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


def build_turn_state(
    context: TurnContext, max_steps: int, max_edits: int = DEFAULT_MAX_EDITS
) -> TurnState:
    resume, personal_info = without_personal_info(context.current_resume)
    return TurnState(
        conversation_id=context.conversation_id,
        run_token=context.run_token,
        language=context.language,
        resume_language=context.resume_language,
        max_steps=max_steps,
        max_edits=max_edits,
        base_resume=context.base_resume,
        personal_info=personal_info,
        resume=resume,
        job_skills=list(context.job_snapshot.get("extractedSkills") or []),
        spans=context.spans,
    )


_TURN_STATE: ContextVar[TurnState | None] = ContextVar("lynq_eval_react_turn", default=None)


@contextmanager
def use_turn_state(state: TurnState) -> Iterator[TurnState]:
    token = _TURN_STATE.set(state)
    try:
        yield state
    finally:
        _TURN_STATE.reset(token)


def current_turn_state() -> TurnState:
    state = _TURN_STATE.get()
    if state is None:
        raise RuntimeError("the tools were called outside of a turn")
    return state
