from __future__ import annotations

import logging
import re

from langchain_core.messages import SystemMessage

from agent.apply import gaps_in
from agent.context import TurnContext
from agent.schemas import Advice, Recommendation
from agent.state import TurnState
from agent.structured import ask
from prompt.tailor import ADVISE, render

log = logging.getLogger(__name__)

RESTATED = 0.6

_MARKER = re.compile(r"^\s*(?:\d+[.)]|[-•*])\s*")
_WORD = re.compile(r"\w+")


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


def _words(text: str) -> set[str]:
    return set(_WORD.findall(text.lower()))


def _restates(line: str, recommendation: Recommendation) -> bool:
    words, theirs = _words(_MARKER.sub("", line)), _words(recommendation.what)
    return bool(words) and len(words & theirs) / len(words | theirs) >= RESTATED


def listed(advice: Advice) -> Advice:
    if not advice.recommendations:
        return advice
    answer = "\n".join(
        line
        for line in advice.reply.split("\n")
        if not any(_restates(line, r) for r in advice.recommendations)
    ).strip()
    items = "\n".join(f"{r.id}. {r.what}" for r in advice.recommendations)
    return advice.model_copy(update={"reply": f"{answer}\n\n{items}" if answer else items})


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
            statements=state.evidence(excluding=context.message),
            gaps=gaps_in(state.job_skills, state.base_resume, state.evidence()),
        )
    )
    advice = await ask(model, Advice, [system, *messages], callbacks=callbacks, retries=retries)
    state.confirm(advice.confirmed)
    return listed(numbered(advice))
