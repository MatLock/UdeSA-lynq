from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field

from langchain.agents import create_agent
from pydantic import BaseModel, Field

from agent.callbacks import TraceCollector
from agent.context import TurnContext
from agent.tools import build_tools
from client import LynqMlClient
from db.repository import SpanRecord
from llm import ModelHandle
from prompt.resume_tailor import render_system_prompt

log = logging.getLogger(__name__)

STEPS_PER_REACT_CYCLE = 2
RECURSION_HEADROOM = 6


def recursion_limit_for(max_steps: int) -> int:
    return STEPS_PER_REACT_CYCLE * max_steps + RECURSION_HEADROOM


class AgentError(RuntimeError):
    pass


class TurnAnswer(BaseModel):
    reply: str = Field(description="The message for the candidate")
    warnings: list[str] = Field(default_factory=list)


@dataclass
class TurnOutcome:
    reply: str
    warnings: list[str]
    resume: dict | None
    changes: list[dict]
    spans: list[SpanRecord] = field(default_factory=list)
    job_requirements: list[str] | None = None
    evidence_log: list[dict] = field(default_factory=list)


async def run_turn(
    *,
    context: TurnContext,
    handle: ModelHandle,
    ml_client: LynqMlClient,
    request_uuid: str,
    message: str,
    history: list[dict],
    turns_left: int,
) -> TurnOutcome:
    collector = TraceCollector(
        context.input_price_per_1m, context.output_price_per_1m
    )
    tools = build_tools(
        context, ml_client, request_uuid, on_limit=collector.record_limit
    )

    system_prompt = render_system_prompt(
        handle.provider,
        job=context.job_snapshot,
        resume=context.resume,
        language=context.language,
        turns_left=turns_left,
    )

    agent = create_agent(
        model=handle.model,
        tools=tools,
        system_prompt=system_prompt,
        response_format=TurnAnswer,
    )

    conversation = [*_history_messages(history), ("user", message)]

    try:
        result = await agent.ainvoke(
            {"messages": conversation},
            config={
                "callbacks": [collector],
                "recursion_limit": recursion_limit_for(context.max_steps),
            },
        )
    except Exception as exc:
        raise AgentError(str(exc)) from exc

    answer = _answer_from(result)

    editor = context.editor
    warnings = [*answer.warnings]
    for rejection in editor.rejections:
        if rejection not in warnings:
            warnings.append(rejection)

    return TurnOutcome(
        reply=answer.reply,
        warnings=warnings,
        resume=editor.resume if editor.applied_changes else None,
        changes=editor.applied_changes,
        spans=collector.spans,
        job_requirements=context.job_requirements,
        evidence_log=context.evidence_log,
    )


def _history_messages(history: list[dict]) -> list[tuple[str, str]]:
    mapped: list[tuple[str, str]] = []
    for entry in history:
        role = "assistant" if entry.get("role") == "assistant" else "user"
        content = entry.get("content") or ""
        if content:
            mapped.append((role, content))
    return mapped


def _as_answer(payload: dict) -> TurnAnswer:
    return TurnAnswer(
        reply=str(payload.get("reply") or ""),
        warnings=[str(warning) for warning in (payload.get("warnings") or [])],
    )


def _answer_shaped_json(text: str) -> TurnAnswer | None:
    stripped = text.strip()
    if not stripped.startswith("{"):
        return None
    try:
        payload = json.loads(stripped)
    except json.JSONDecodeError:
        return None
    if isinstance(payload, dict) and isinstance(payload.get("reply"), str):
        return _as_answer(payload)
    return None


def _answer_from(result) -> TurnAnswer:
    structured = result.get("structured_response") if isinstance(result, dict) else None
    if isinstance(structured, TurnAnswer):
        return structured
    if isinstance(structured, dict) and "reply" in structured:
        return _as_answer(structured)

    messages = (result or {}).get("messages") or []
    for message in reversed(messages):
        content = getattr(message, "content", None)
        if isinstance(content, list):
            content = " ".join(
                part.get("text", "") for part in content if isinstance(part, dict)
            )
        if isinstance(content, str) and content.strip():
            # A model that answers in text instead of calling the response tool
            # often pastes the JSON it was asked for. Unwrap it, or the candidate
            # reads raw JSON in the chat.
            return _answer_shaped_json(content) or TurnAnswer(
                reply=content.strip(), warnings=[]
            )

    raise AgentError("the agent produced no reply")
