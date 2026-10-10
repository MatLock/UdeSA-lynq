from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any

PER_MILLION = Decimal("1000000")


class Intent:
    ADVISE = "advise"
    EDIT = "edit"


DEFAULT_INTENT = Intent.EDIT
INTENT_SPAN = "intent"


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
    """Everything a turn needs, loaded in the claiming transaction so that the
    agents run with no session open."""

    conversation_id: str
    run_token: str
    language: str
    resume_language: str
    job_snapshot: dict[str, Any]
    base_resume: dict[str, Any]
    current_resume: dict[str, Any]
    history: list[tuple[str, str]]
    message: str
    turns_left: int
    recommendations: list[dict[str, Any]] = field(default_factory=list)
    statements: list[str] = field(default_factory=list)
    resume_version_id: str | None = None
    spans: list[SpanRecord] = field(default_factory=list)


@dataclass
class TurnOutcome:
    reply: str
    resume: dict[str, Any]
    changes: list[dict[str, Any]] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    spans: list[SpanRecord] = field(default_factory=list)
    intent: str = DEFAULT_INTENT
    recommendations: list[dict[str, Any]] = field(default_factory=list)
    confirmed: list[str] = field(default_factory=list)


def deep_copy(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: deep_copy(item) for key, item in value.items()}
    if isinstance(value, list):
        return [deep_copy(item) for item in value]
    return value


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
