from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass
from typing import Any
from uuid import UUID

from langchain_core.callbacks import AsyncCallbackHandler
from langchain_core.messages import BaseMessage, SystemMessage
from langchain_core.outputs import LLMResult

from agent.context import SpanRecord, TurnState
from db.models import SpanKind

log = logging.getLogger(__name__)

MODEL_SPAN = "model"


@dataclass
class OpenSpan:
    step: int
    name: str
    started_at: float
    input: str | None


class TraceCollector(AsyncCallbackHandler):

    def __init__(
        self,
        state: TurnState,
        prompt_reference: str,
        resume_version_id: str | None = None,
    ) -> None:
        self._state = state
        self._prompt_reference = prompt_reference
        self._resume_version_id = resume_version_id
        self._open: dict[UUID, OpenSpan] = {}

    async def on_chat_model_start(
        self, serialized: dict, messages: list[list[BaseMessage]], *, run_id: UUID, **kwargs: Any
    ) -> None:
        turn = [message for batch in messages for message in batch]
        self._start(run_id, MODEL_SPAN, self._model_input(turn))

    async def on_llm_start(
        self, serialized: dict, prompts: list[str], *, run_id: UUID, **kwargs: Any
    ) -> None:
        self._start(run_id, MODEL_SPAN, self._reference({"prompts": prompts}))

    async def on_llm_end(self, response: LLMResult, *, run_id: UUID, **kwargs: Any) -> None:
        span = self._finish(run_id)
        if span is None:
            return

        message = self._answer_of(response)
        usage = getattr(message, "usage_metadata", None) or {}
        details = usage.get("input_token_details") or {}
        self._record(
            span,
            SpanKind.LLM,
            output=self._output_of(message),
            prompt_tokens=usage.get("input_tokens"),
            completion_tokens=usage.get("output_tokens"),
            cached_prompt_tokens=details.get("cache_read"),
        )

    async def on_llm_error(self, error: BaseException, *, run_id: UUID, **kwargs: Any) -> None:
        span = self._finish(run_id)
        if span is not None:
            self._record(span, SpanKind.ERROR, error=str(error))

    async def on_tool_start(
        self, serialized: dict, input_str: str, *, run_id: UUID, **kwargs: Any
    ) -> None:
        self._start(run_id, (serialized or {}).get("name") or "tool", input_str)

    async def on_tool_end(self, output: Any, *, run_id: UUID, **kwargs: Any) -> None:
        span = self._finish(run_id)
        if span is not None:
            self._record(span, SpanKind.TOOL, output=self._as_text(output))

    async def on_tool_error(self, error: BaseException, *, run_id: UUID, **kwargs: Any) -> None:
        span = self._finish(run_id)
        if span is not None:
            self._record(span, SpanKind.ERROR, error=str(error))

    def _start(self, run_id: UUID, name: str, payload: str | None) -> None:
        self._open[run_id] = OpenSpan(
            step=self._state.next_step(),
            name=name,
            started_at=time.monotonic(),
            input=payload,
        )

    def _finish(self, run_id: UUID) -> OpenSpan | None:
        return self._open.pop(run_id, None)

    def _record(self, span: OpenSpan, kind: str, **values: Any) -> None:
        self._state.spans.append(
            SpanRecord(
                step=span.step,
                kind=kind,
                name=span.name,
                input=span.input,
                latency_ms=int((time.monotonic() - span.started_at) * 1000),
                **values,
            )
        )

    def _model_input(self, messages: list[BaseMessage]) -> str:
        turn = [
            {"role": message.type, "content": self._as_text(message.content)}
            for message in messages
            if not isinstance(message, SystemMessage)
        ]
        return self._reference({"messages": turn})

    def _reference(self, payload: dict[str, Any]) -> str:
        return json.dumps(
            {
                **payload,
                "resume_version_id": self._resume_version_id,
                "prompt": self._prompt_reference,
            },
            ensure_ascii=False,
            default=str,
        )

    @staticmethod
    def _answer_of(response: LLMResult) -> Any:
        for batch in response.generations:
            for generation in batch:
                message = getattr(generation, "message", None)
                if message is not None:
                    return message
        return None

    @classmethod
    def _output_of(cls, message: Any) -> str:
        if message is None:
            return ""
        calls = getattr(message, "tool_calls", None)
        if calls:
            return json.dumps(
                [{"name": call.get("name"), "args": call.get("args")} for call in calls],
                ensure_ascii=False,
                default=str,
            )
        return cls._as_text(getattr(message, "content", message))

    @staticmethod
    def _as_text(value: Any) -> str:
        content = getattr(value, "content", None)
        if content is not None:
            value = content
        if isinstance(value, str):
            return value
        return json.dumps(value, ensure_ascii=False, default=str)
