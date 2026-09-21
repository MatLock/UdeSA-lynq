from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any, Iterator

PERSONAL_INFO = "personal_info"
PER_MILLION = Decimal("1000000")


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


@dataclass
class SpanRecord:
    step: int
    kind: str
    name: str
    input: str | None = None
    output: str | None = None
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    cached_prompt_tokens: int | None = None
    cost_usd: Decimal | None = None
    latency_ms: int | None = None
    error: str | None = None
    parent_id: str | None = None
    created_on: datetime = field(default_factory=utc_now)


@dataclass
class TurnContext:
    conversation_id: str
    run_token: str
    language: str
    resume_language: str
    job_snapshot: dict[str, Any]
    base_resume: dict[str, Any]
    current_resume: dict[str, Any]
    history: list[tuple[str, str]]
    message: str
    max_steps: int
    turns_left: int
    resume_version_id: str | None = None
    spans: list[SpanRecord] = field(default_factory=list)


@dataclass
class TurnOutcome:
    reply: str
    resume: dict[str, Any]
    changes: list[dict[str, Any]] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    spans: list[SpanRecord] = field(default_factory=list)


@dataclass
class TurnState:
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
    limit_reported: bool = False

    def next_step(self) -> int:
        self.steps += 1
        return self.steps

    def at_step_limit(self) -> bool:
        return self.steps >= self.max_steps

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


def deep_copy(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: deep_copy(item) for key, item in value.items()}
    if isinstance(value, list):
        return [deep_copy(item) for item in value]
    return value


def build_turn_state(context: TurnContext) -> TurnState:
    resume, personal_info = without_personal_info(context.current_resume)
    return TurnState(
        conversation_id=context.conversation_id,
        run_token=context.run_token,
        language=context.language,
        resume_language=context.resume_language,
        max_steps=context.max_steps,
        base_resume=context.base_resume,
        personal_info=personal_info,
        resume=resume,
        job_skills=list(context.job_snapshot.get("extractedSkills") or []),
        spans=context.spans,
    )


_TURN_STATE: ContextVar[TurnState | None] = ContextVar("lynq_agent_turn", default=None)


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


def span_cost(
    record: SpanRecord, input_price_per_1m: Decimal, output_price_per_1m: Decimal
) -> Decimal:
    prompt = Decimal(record.prompt_tokens or 0) * input_price_per_1m
    completion = Decimal(record.completion_tokens or 0) * output_price_per_1m
    return (prompt + completion) / PER_MILLION


def apply_pricing(
    records: list[SpanRecord],
    input_price_per_1m: Decimal,
    output_price_per_1m: Decimal,
) -> list[SpanRecord]:
    for record in records:
        if record.cost_usd is None and (record.prompt_tokens or record.completion_tokens):
            record.cost_usd = span_cost(record, input_price_per_1m, output_price_per_1m)
    return records
