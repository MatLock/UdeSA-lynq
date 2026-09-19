from __future__ import annotations

import json
import time
from decimal import Decimal
from typing import Any

from langchain_core.callbacks import AsyncCallbackHandler

from agent.pricing import cost_of
from db.models import SpanKind
from db.repository import SpanRecord, now

MAX_SPAN_TEXT = 16000


def truncate(text: str | None) -> str | None:
    if text is None:
        return None
    if len(text) <= MAX_SPAN_TEXT:
        return text
    return text[:MAX_SPAN_TEXT] + f"... [{len(text) - MAX_SPAN_TEXT} chars truncados]"


def _as_text(value: Any) -> str:
    if isinstance(value, str):
        return value
    content = getattr(value, "content", None)
    if isinstance(content, str):
        return content
    try:
        return json.dumps(value, ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        return str(value)


class TraceCollector(AsyncCallbackHandler):

    def __init__(
        self, input_price_per_1m: Decimal, output_price_per_1m: Decimal
    ) -> None:
        self.spans: list[SpanRecord] = []
        self.input_price_per_1m = input_price_per_1m
        self.output_price_per_1m = output_price_per_1m
        self._step = 0
        self._started: dict[str, float] = {}

    async def on_llm_start(self, serialized, prompts, *, run_id=None, **kwargs):
        self._started[str(run_id)] = time.monotonic()

    async def on_chat_model_start(self, serialized, messages, *, run_id=None, **kwargs):
        self._started[str(run_id)] = time.monotonic()

    async def on_llm_end(self, response, *, run_id=None, **kwargs):
        usage = {}
        text = ""
        generations = getattr(response, "generations", None) or []
        for batch in generations:
            for generation in batch:
                message = getattr(generation, "message", None)
                if message is not None:
                    usage = getattr(message, "usage_metadata", None) or usage
                    text = _as_text(getattr(message, "content", "")) or text
                else:
                    text = getattr(generation, "text", "") or text

        prompt_tokens = usage.get("input_tokens")
        completion_tokens = usage.get("output_tokens")
        cached = (usage.get("input_token_details") or {}).get("cache_read")

        cost = None
        if prompt_tokens is not None or completion_tokens is not None:
            cost = cost_of(
                prompt_tokens or 0,
                completion_tokens or 0,
                self.input_price_per_1m,
                self.output_price_per_1m,
            )

        self.spans.append(
            SpanRecord(
                step=self._next_step(),
                kind=SpanKind.LLM,
                name="llm",
                input=None,
                output=truncate(text),
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
                cached_prompt_tokens=cached,
                cost_usd=cost,
                latency_ms=self._elapsed_ms(run_id),
                created_on=now(),
            )
        )

    async def on_llm_error(self, error, *, run_id=None, **kwargs):
        self.spans.append(
            SpanRecord(
                step=self._next_step(),
                kind=SpanKind.ERROR,
                name="llm",
                error=str(error),
                latency_ms=self._elapsed_ms(run_id),
                created_on=now(),
            )
        )

    async def on_tool_start(self, serialized, input_str, *, run_id=None, **kwargs):
        self._started[str(run_id)] = time.monotonic()
        name = (serialized or {}).get("name", "tool")
        self._started[f"name:{run_id}"] = name
        self.spans.append(
            SpanRecord(
                step=self._next_step(),
                kind=SpanKind.TOOL,
                name=name,
                input=truncate(_as_text(input_str)),
                created_on=now(),
            )
        )

    async def on_tool_end(self, output, *, run_id=None, **kwargs):
        latency = self._elapsed_ms(run_id)
        name = self._started.pop(f"name:{run_id}", None)
        for span in reversed(self.spans):
            if span.kind == SpanKind.TOOL and span.output is None:
                if name is None or span.name == name:
                    span.output = truncate(_as_text(output))
                    span.latency_ms = latency
                    return

    async def on_tool_error(self, error, *, run_id=None, **kwargs):
        latency = self._elapsed_ms(run_id)
        self._started.pop(f"name:{run_id}", None)
        for span in reversed(self.spans):
            if span.kind == SpanKind.TOOL and span.output is None:
                span.error = str(error)
                span.latency_ms = latency
                return

    def record_limit(self, detail: str) -> None:
        self.spans.append(
            SpanRecord(
                step=self._next_step(),
                kind=SpanKind.LIMIT,
                name="max_steps",
                output=detail,
                created_on=now(),
            )
        )

    def _next_step(self) -> int:
        self._step += 1
        return self._step

    def _elapsed_ms(self, run_id: Any) -> int | None:
        started = self._started.pop(str(run_id), None)
        if started is None:
            return None
        return int((time.monotonic() - started) * 1000)
