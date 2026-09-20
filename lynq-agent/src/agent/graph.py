from __future__ import annotations

import json
import logging

from agent.context import SpanRecord, TurnContext, TurnOutcome
from db.models import SpanKind

log = logging.getLogger(__name__)

STUB_REPLY = (
    "I kept your resume as it is. The language model is wired in stage 3; "
    "for now every turn is recorded end to end without editing anything."
)

STUB_CHANGE = {
    "section": "summary",
    "kind": "noop",
    "detail": "the stubbed agent did not modify the resume",
}

_TOKENS_PER_CHAR = 4


def build_greeting(job_snapshot: dict, language: str) -> str:
    title = job_snapshot.get("title") or "this job"
    company = job_snapshot.get("company")
    where = f" at {company}" if company else ""
    return (
        f"I read the posting for {title}{where}. "
        "Should I put together a version of your resume aimed at it?"
    )


async def run_turn(context: TurnContext) -> TurnOutcome:
    prompt = json.dumps(
        {
            "job": context.job_snapshot,
            "resume": context.current_resume,
            "history": context.history,
            "message": context.message,
            "language": context.language,
            "resumeLanguage": context.resume_language,
        },
        ensure_ascii=False,
    )

    context.spans.append(
        SpanRecord(
            step=1,
            kind=SpanKind.LLM,
            name="tailor",
            input=prompt,
            output=STUB_REPLY,
            prompt_tokens=len(prompt) // _TOKENS_PER_CHAR,
            completion_tokens=len(STUB_REPLY) // _TOKENS_PER_CHAR,
            latency_ms=0,
        )
    )
    context.spans.append(
        SpanRecord(
            step=2,
            kind=SpanKind.TOOL,
            name="apply_edit",
            input=json.dumps(STUB_CHANGE, ensure_ascii=False),
            output="OK",
            latency_ms=0,
        )
    )

    log.info(
        "message= Stubbed turn finished, conversationId=%s, steps=%s",
        context.conversation_id,
        len(context.spans),
    )

    return TurnOutcome(
        reply=STUB_REPLY,
        resume=context.current_resume,
        changes=[dict(STUB_CHANGE)],
        warnings=[],
        spans=context.spans,
    )
