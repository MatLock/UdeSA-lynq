from __future__ import annotations

import logging

from langchain_core.messages import SystemMessage

from agent.context import TurnContext
from agent.schemas import Advice
from agent.state import TurnState
from agent.structured import ask
from prompt.tailor import ADVISE, render

log = logging.getLogger(__name__)


def numbered(advice: Advice) -> Advice:
    """Recommendations are addressed by number in the next turn, so the numbers
    are the code's, 1..n in the order the model gave them, whatever it wrote."""
    kept = [r for r in advice.recommendations if r.what.strip()]
    return advice.model_copy(
        update={
            "recommendations": [
                recommendation.model_copy(update={"id": position})
                for position, recommendation in enumerate(kept, start=1)
            ]
        }
    )


async def advise(
    context: TurnContext,
    state: TurnState,
    model,
    *,
    provider: str,
    callbacks: list,
    retries: int,
    messages: list,
) -> Advice:
    """The advising agent: answers and recommends. It is given no way to change
    the resume, so an advise turn cannot write a version by construction."""
    system = SystemMessage(
        render(
            ADVISE,
            provider=provider,
            job=context.job_snapshot,
            resume=state.resume,
            language=context.language,
            resume_language=context.resume_language,
            turns_left=context.turns_left,
        )
    )
    advice = await ask(model, Advice, [system, *messages], callbacks=callbacks, retries=retries)
    return numbered(advice)
