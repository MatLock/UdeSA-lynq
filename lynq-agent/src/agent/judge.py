from __future__ import annotations

import logging

from langchain_core.messages import AIMessage, HumanMessage

from agent.apply import Part, Rejection
from agent.context import span_cost
from agent.schemas import PartVerdict, Verdict
from agent.state import TurnState
from agent.structured import ask
from config import get_settings
from db.models import SpanKind
from prompt.tailor import render_judge

log = logging.getLogger(__name__)

# What the judge answers when it does not answer: a part it left out is not
# approved. The safe default costs a retry, never an invention.
UNJUDGED = "unjudged"
REJECTED = "rejected"

UNDECIDED_NOTE = (
    "Your verdict left `ok` out on these parts: {parts}. Answer the Verdict again, every "
    "part with `ok` set to true when the resume or the candidate supports it and false "
    "when not."
)


async def judge(
    state: TurnState,
    parts: list[Part],
    model,
    *,
    provider: str,
    language: str,
    job_skills: list[str],
    callbacks: list,
    retries: int,
    statements: list[str] | None = None,
    asked: str = "",
    message: str = "",
) -> tuple[set[str], list[Rejection]]:
    """The judging agent: reads every part of the proposal beside the text it
    replaces and says, part by part, whether the resume supports it. It never
    rewrites; it approves or rejects, with a reason the candidate can read."""
    if not parts:
        return set(), []

    prompt = render_judge(
        provider=provider,
        resume=state.base_resume,
        job_skills=job_skills,
        language=language,
        resume_language=state.resume_language,
        parts=parts,
        statements=statements if statements is not None else state.evidence(),
        asked=asked,
        message=message,
    )
    spans_before = len(state.spans)
    thread = [HumanMessage(prompt)]
    verdict = await ask(model, Verdict, thread, callbacks=callbacks, retries=retries)
    answers = {answer.id.strip(): answer for answer in verdict.parts if answer.id.strip()}

    undecided = [part.id for part in parts if part.id in answers and not _decided(answers[part.id])]
    if undecided:
        log.warning(
            "message= The judge left ok out and is asked again, conversationId=%s, parts=%s",
            state.conversation_id, ", ".join(undecided),
        )
        thread = [
            *thread,
            AIMessage(verdict.model_dump_json()),
            HumanMessage(UNDECIDED_NOTE.format(parts=", ".join(undecided))),
        ]
        again = await ask(model, Verdict, thread, callbacks=callbacks, retries=retries)
        answers.update({
            answer.id.strip(): answer
            for answer in again.parts
            if answer.id.strip() in undecided and _decided(answer)
        })
    _price(state, spans_before)

    approved: set[str] = set()
    rejections: list[Rejection] = []
    for part in parts:
        answer = answers.get(part.id)
        if answer is None or not _decided(answer):
            log.warning(
                "message= The judge gave no verdict on a part, conversationId=%s, part=%s",
                state.conversation_id, part.id,
            )
            rejections.append(Rejection(part.section, part.label, UNJUDGED, UNJUDGED))
        elif answer.ok:
            approved.add(part.id)
        else:
            rejections.append(Rejection(
                part.section, part.label, answer.kind.strip() or REJECTED,
                answer.reason.strip() or answer.kind.strip() or REJECTED,
            ))
    return approved, rejections


def _decided(answer: PartVerdict) -> bool:
    return "ok" in answer.model_fields_set


def _price(state: TurnState, spans_before: int) -> None:
    # The judge may not be the conversation's model, so its spans are priced here
    # with their own sheet instead of the rates frozen on the conversation.
    settings = get_settings()
    for record in state.spans[spans_before:]:
        if record.kind == SpanKind.LLM and record.cost_usd is None:
            record.cost_usd = span_cost(
                record, settings.judge_input_price_per_1m, settings.judge_output_price_per_1m
            )
