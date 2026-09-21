from __future__ import annotations

import logging

from langchain.agents import create_agent
from langchain_core.messages import AIMessage, HumanMessage

from agent.answer import TurnAnswer, from_result
from agent.callbacks import TraceCollector
from agent.context import TurnContext, TurnOutcome, build_turn_state, use_turn_state
from agent.tools import apply_edit, find_evidence
from config import BEDROCK, OLLAMA, get_settings
from db.models import MessageRole
from llm.factory import build_model
from prompt.resume_tailor import reference, render

log = logging.getLogger(__name__)

RECURSION_HEADROOM = 6


def build_greeting(job_snapshot: dict, language: str) -> str:
    title = job_snapshot.get("title") or "this job"
    company = job_snapshot.get("company")
    where = f" at {company}" if company else ""
    return (
        f"I read the posting for {title}{where}. "
        "Should I put together a version of your resume aimed at it?"
    )


def template_provider(provider: str) -> str:
    return BEDROCK if provider == BEDROCK else OLLAMA


def recursion_limit(max_steps: int) -> int:
    return 2 * max_steps + RECURSION_HEADROOM


def build_agent(system_prompt: str, model=None):
    return create_agent(
        model=model if model is not None else build_model(),
        tools=[find_evidence, apply_edit],
        system_prompt=system_prompt,
        response_format=TurnAnswer,
    )


def turn_messages(context: TurnContext) -> list:
    history = list(context.history)
    if history and history[-1] == (MessageRole.USER, context.message):
        history = history[:-1]

    messages = [
        HumanMessage(content) if role == MessageRole.USER else AIMessage(content)
        for role, content in history
    ]
    messages.append(HumanMessage(context.message))
    return messages


async def run_turn(context: TurnContext, model=None) -> TurnOutcome:
    settings = get_settings()
    provider = template_provider(settings.llm_provider)
    state = build_turn_state(context)

    system_prompt = render(
        provider,
        job=context.job_snapshot,
        resume=state.resume,
        language=context.language,
        resume_language=context.resume_language,
        max_steps=context.max_steps,
        turns_left=context.turns_left,
    )
    collector = TraceCollector(
        state, reference(provider), context.resume_version_id
    )
    agent = build_agent(system_prompt, model)

    with use_turn_state(state):
        result = await agent.ainvoke(
            {"messages": turn_messages(context)},
            config={
                "callbacks": [collector],
                "recursion_limit": recursion_limit(context.max_steps),
            },
        )

    answer = from_result(result)
    log.info(
        "message= Turn finished, conversationId=%s, steps=%s, edits=%s, warnings=%s",
        context.conversation_id,
        state.steps,
        len(state.changes),
        len(answer.warnings),
    )
    return TurnOutcome(
        reply=answer.reply,
        resume=state.serialize_resume(),
        changes=state.changes,
        warnings=answer.warnings,
        spans=state.spans,
    )
