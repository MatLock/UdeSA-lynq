from __future__ import annotations

import logging

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from agent.context import TurnContext
from agent.schemas import EditProposal
from agent.state import TurnState
from agent.structured import ask
from prompt.tailor import EDIT, render

log = logging.getLogger(__name__)

# A rejection goes back to the model once: the second pass is the correction, and
# what it still gets wrong stays out. More passes would be the loop again.
MAX_PASSES = 2

RETRY_NOTE = (
    "The judge rejected these parts of your proposal, and they were not applied:\n"
    "{rejections}\n\nEverything else was applied and stays. Propose again only the "
    "rejected parts, backed by what the resume already says, or leave them empty and "
    "tell the candidate, in `warnings`, what could not be done and why. Write `reply` "
    "again so that it describes what the resume now says."
)


def opening_thread(context: TurnContext, state: TurnState, provider: str, messages: list) -> list:
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
    return [system, *messages]


def retry_thread(thread: list, proposal: EditProposal, rejections: list[str]) -> list:
    return [
        *thread,
        AIMessage(proposal.model_dump_json()),
        HumanMessage(RETRY_NOTE.format(rejections="\n".join(f"- {r}" for r in rejections))),
    ]


async def propose(model, thread: list, *, callbacks: list, retries: int) -> EditProposal:
    """The editing agent: one structured answer with every change of the turn."""
    return await ask(model, EditProposal, thread, callbacks=callbacks, retries=retries)
