from __future__ import annotations

import json
import logging

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from agent import guard
from agent.context import SpanRecord, TurnContext
from agent.schemas import EditProposal
from agent.state import TurnState
from agent.structured import ask
from db.models import SpanKind
from prompt.tailor import EDIT, render

log = logging.getLogger(__name__)

GUARD_SPAN = "guard"
OK = "OK"

RETRY_NOTE = (
    "The resume's rules rejected these parts of your proposal, and they were not "
    "applied:\n{rejections}\n\nEverything else was applied and stays. Propose again "
    "only the rejected parts, backed by what the resume already says, or leave them "
    "empty and tell the candidate, in `warnings`, what could not be done and why. "
    "Write `reply` again so that it describes what the resume now says."
)


def _edits_of(proposal: EditProposal) -> str:
    return proposal.model_dump_json(exclude={"reply", "warnings"})


def _guard_span(state: TurnState, proposal: EditProposal, rejections: list[str]) -> None:
    state.spans.append(
        SpanRecord(
            step=state.next_step(),
            kind=SpanKind.TOOL,
            name=GUARD_SPAN,
            input=_edits_of(proposal),
            output=OK if not rejections else json.dumps(rejections, ensure_ascii=False),
        )
    )


async def edit(
    context: TurnContext,
    state: TurnState,
    model,
    *,
    provider: str,
    callbacks: list,
    retries: int,
    messages: list,
) -> tuple[EditProposal, list[str]]:
    """The editing agent: one structured answer with every change of the turn, run
    through the guard. A rejection goes back once, so the model can correct itself
    with the reason in hand; what is still rejected after that stays out."""
    system = SystemMessage(
        render(
            EDIT,
            provider=provider,
            job=context.job_snapshot,
            resume=state.resume,
            language=context.language,
            resume_language=context.resume_language,
            turns_left=context.turns_left,
            recommendations=context.recommendations,
        )
    )
    thread = [system, *messages]

    proposal = await ask(model, EditProposal, thread, callbacks=callbacks, retries=retries)
    rejections = guard.apply(state, proposal)
    _guard_span(state, proposal, rejections)
    if not rejections:
        return proposal, []

    thread = [
        *thread,
        AIMessage(proposal.model_dump_json()),
        HumanMessage(RETRY_NOTE.format(rejections="\n".join(f"- {r}" for r in rejections))),
    ]
    proposal = await ask(model, EditProposal, thread, callbacks=callbacks, retries=retries)
    rejections = guard.apply(state, proposal)
    _guard_span(state, proposal, rejections)
    return proposal, rejections
