from __future__ import annotations

import asyncio
import json
import logging
import os
import time
import uuid
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

from agent.apply import gaps_in
from agent.context import Intent, SpanRecord, TurnContext, TurnOutcome, apply_pricing, deep_copy
from agent.graph import APPLY_SPAN
from config import get_settings
from db.models import MessageRole, SpanKind
from evaluation.arms import ARMS, runner_for
from evaluation.corpus import Pair, Script, fill
from evaluation.diff import Unit, unit_id, units_of
from llm.factory import build_model
from prompt.greeting import render as greeting

log = logging.getLogger(__name__)

KEY_SEPARATOR = "|"
REJECTED_PREFIX = "REJECTED:"


def turn_key(pair: str, script: str, arm: str, turn: int) -> str:
    return KEY_SEPARATOR.join((pair, script, arm, str(turn)))


@dataclass
class Models:
    """The models every arm runs on, built once: the same editor model for the
    three arms, the intent model the service uses, and the judge model the
    service uses (which may be a cheaper one, as in production)."""

    model: Any = None
    intent_model: Any = None
    judge_model: Any = None
    names: dict[str, str] = field(default_factory=dict)

    @classmethod
    def from_settings(cls) -> "Models":
        settings = get_settings()
        return cls(
            model=build_model(),
            intent_model=build_model(model=settings.intent_model),
            judge_model=build_model(model=settings.judge_model),
            names={
                "provider": settings.llm_provider,
                "model": settings.llm_model,
                "intent_model": settings.intent_model,
                "judge_model": settings.judge_model,
            },
        )

    def kwargs(self) -> dict[str, Any]:
        return {"model": self.model, "intent_model": self.intent_model, "judge_model": self.judge_model}


def _span_totals(spans: list[SpanRecord]) -> dict[str, Any]:
    llm = [s for s in spans if s.kind == SpanKind.LLM]
    return {
        "model_calls": len(llm),
        "prompt_tokens": sum(s.prompt_tokens or 0 for s in llm),
        "completion_tokens": sum(s.completion_tokens or 0 for s in llm),
        "cost_usd": str(sum((s.cost_usd or Decimal(0) for s in llm), Decimal(0))),
        "latency_ms": sum(s.latency_ms or 0 for s in spans),
        "errors": len([s for s in spans if s.kind == SpanKind.ERROR]),
        "tool_calls": len([s for s in spans if s.kind == SpanKind.TOOL and s.name != APPLY_SPAN]),
        "tool_rejections": len([
            s for s in spans
            if s.kind == SpanKind.TOOL and (s.output or "").startswith(REJECTED_PREFIX)
        ]),
        "passes": len([s for s in spans if s.name == APPLY_SPAN]),
    }


def rejected_units(key: str, spans: list[SpanRecord]) -> list[Unit]:
    """The parts the guard or the judge kept out, read from the apply spans: one
    unit each, with the kind the system gave and the text it refused beside the
    text it would have replaced, so a human can say whether keeping it out was
    right. A part re-proposed in a later pass is another unit."""
    units: list[Unit] = []
    for number, span in enumerate(s for s in spans if s.name == APPLY_SPAN):
        if (span.output or "OK") == "OK":
            continue
        try:
            rejections = json.loads(span.output)
        except json.JSONDecodeError:
            continue
        for rejection in rejections:
            section, label = rejection.get("section", ""), rejection.get("label", "")
            proposed = rejection.get("proposed", "")
            units.append(Unit(
                id=unit_id(f"{key}#{number}", section, label, proposed),
                section=section, label=label,
                original=rejection.get("original", ""), proposed=proposed,
                applied=False, system_kind=rejection.get("kind", "rejected"),
            ))
    return units


def _as_dict(span: SpanRecord) -> dict[str, Any]:
    return {
        "step": span.step, "kind": span.kind, "name": span.name,
        "prompt_tokens": span.prompt_tokens, "completion_tokens": span.completion_tokens,
        "cost_usd": str(span.cost_usd) if span.cost_usd is not None else None,
        "latency_ms": span.latency_ms, "error": span.error,
        "output": span.output,
    }


class Conversation:
    """One scripted conversation on one arm, run the way the service runs it:
    the greeting first, then each message with the history, the statements and
    the recommendations of the turn before."""

    def __init__(self, pair: Pair, script: Script, arm: str, turns: list[str], models: Models) -> None:
        self.pair, self.script, self.arm, self.turns, self.models = pair, script, arm, turns, models
        self.run = runner_for(arm)
        self.current = deep_copy(pair.base_resume)
        self.history: list[tuple[str, str]] = [
            (MessageRole.ASSISTANT, greeting(pair.job, pair.language, pair.gaps))
        ]
        self.spoken: list[str] = []
        self.confirmed: list[str] = []
        self.recommendations: list[dict[str, Any]] = []

    def context(self, number: int, message: str) -> TurnContext:
        return TurnContext(
            conversation_id=KEY_SEPARATOR.join((self.pair.id, self.script.id, self.arm)),
            run_token=str(uuid.uuid4()),
            language=self.pair.language,
            resume_language=self.pair.resume_language,
            job_snapshot=self.pair.job,
            base_resume=self.pair.base_resume,
            current_resume=self.current,
            history=[*self.history, (MessageRole.USER, message)],
            message=message,
            turns_left=len(self.turns) - number + 1,
            recommendations=self.recommendations,
            statements=[*self.spoken, *self.confirmed],
        )

    async def turn(self, number: int, message: str, timeout: int) -> dict[str, Any]:
        key = turn_key(self.pair.id, self.script.id, self.arm, number)
        settings = get_settings()
        before = deep_copy(self.current)
        started = time.monotonic()
        record: dict[str, Any] = {
            "key": key, "pair": self.pair.id, "script": self.script.id, "arm": self.arm, "turn": number,
            "category": self.pair.category, "language": self.pair.language,
            "resume_language": self.pair.resume_language, "message": message,
            "candidate_said": [*self.spoken, message], "job_skills": self.pair.job_skills,
            "gaps_before": gaps_in(self.pair.job_skills, before, []), **self.models.names,
        }
        try:
            outcome: TurnOutcome = await asyncio.wait_for(
                self.run(self.context(number, message), **self.models.kwargs()), timeout=timeout
            )
        except Exception as exc:  # the turn is the unit of failure: recorded, never hidden
            log.exception("message= Turn failed, key=%s", key)
            record.update({"error": f"{type(exc).__name__}: {exc}", "wall_ms": int((time.monotonic() - started) * 1000)})
            return record

        apply_pricing(outcome.spans, settings.input_price_per_1m, settings.output_price_per_1m)
        units, faults = units_of(key, before, outcome.resume)
        rejected = rejected_units(key, outcome.spans)
        record.update({
            "error": None,
            "intent": outcome.intent,
            "reply": outcome.reply,
            "warnings": list(outcome.warnings),
            "recommendations": list(outcome.recommendations),
            "confirmed": list(outcome.confirmed),
            "changes": list(outcome.changes),
            "before": before,
            "after": outcome.resume,
            "units": [u.as_dict() for u in units],
            "rejected": [u.as_dict() for u in rejected],
            "faults": faults,
            "gaps_after": gaps_in(self.pair.job_skills, outcome.resume, []),
            "wall_ms": int((time.monotonic() - started) * 1000),
            "spans": [_as_dict(s) for s in outcome.spans],
            **_span_totals(outcome.spans),
        })

        self.current = deep_copy(outcome.resume)
        self.history.extend([(MessageRole.USER, message), (MessageRole.ASSISTANT, outcome.reply)])
        self.spoken.append(message)
        for fact in outcome.confirmed:
            if fact not in self.confirmed:
                self.confirmed.append(fact)
        self.recommendations = list(outcome.recommendations) if outcome.intent == Intent.ADVISE else []
        return record


def conversation_key(pair: str, script: str, arm: str) -> str:
    return KEY_SEPARATOR.join((pair, script, arm))


def recorded(path: str) -> tuple[set[str], set[str]]:
    """The turn keys already in the output, and the conversations that ended on
    a failed turn: those are complete as far as the record goes, and are not
    run again unless the line is removed."""
    keys: set[str] = set()
    failed: set[str] = set()
    if not os.path.exists(path):
        return keys, failed
    with open(path, encoding="utf-8") as file:
        for line in file:
            if not line.strip():
                continue
            row = json.loads(line)
            keys.add(row["key"])
            if row.get("error"):
                failed.add(conversation_key(row["pair"], row["script"], row["arm"]))
    return keys, failed


@dataclass
class Plan:
    """Which conversations a run covers, and which scripts had to be skipped
    because a pair could not fill them."""

    conversations: list[tuple[Pair, Script, str, list[str]]] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)


def plan_run(pairs: list[Pair], scripts: list[Script], arms: list[str]) -> Plan:
    plan = Plan()
    for arm in arms:
        if arm not in ARMS:
            raise ValueError(f"unknown arm {arm!r}")
    for pair in pairs:
        for script in scripts:
            turns = fill(script, pair)
            if turns is None:
                plan.skipped.append((pair.id, script.id))
                continue
            for arm in arms:
                plan.conversations.append((pair, script, arm, turns))
    return plan


async def run_all(
    plan: Plan,
    output: str,
    models: Models,
    *,
    timeout: int | None = None,
    parallel: int = 1,
) -> int:
    """Runs every conversation of the plan whose first turn is not yet in the
    output, appending one line per turn as it finishes, so a crash costs at
    most one turn and a rerun picks up where it stopped. A failed turn ends its
    conversation: the next turns would be built on a resume that does not exist.
    A conversation interrupted half-way is run again from its first turn; the
    loader keeps the last record of every key."""
    timeout = timeout or get_settings().turn_timeout_seconds
    done, failed = recorded(output)
    lock = asyncio.Lock()
    gate = asyncio.Semaphore(max(1, parallel))
    written = 0

    async def one(pair: Pair, script: Script, arm: str, turns: list[str]) -> None:
        nonlocal written
        keys = [turn_key(pair.id, script.id, arm, n) for n in range(1, len(turns) + 1)]
        if all(k in done for k in keys) or conversation_key(pair.id, script.id, arm) in failed:
            return
        if any(k in done for k in keys):
            log.warning(
                "message= Conversation partly recorded, it is run again from the start, pair=%s, script=%s, arm=%s",
                pair.id, script.id, arm,
            )
        async with gate:
            conversation = Conversation(pair, script, arm, turns, models)
            for number, message in enumerate(turns, 1):
                record = await conversation.turn(number, message, timeout)
                async with lock:
                    with open(output, "a", encoding="utf-8") as file:
                        file.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")
                    written += 1
                if record.get("error"):
                    break

    await asyncio.gather(*(one(*entry) for entry in plan.conversations))
    return written
