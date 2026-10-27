"""The three designs under comparison, behind one call. Every arm takes the same
`TurnContext` the service builds and answers the same `TurnOutcome`, so the runner
cannot tell them apart and neither can the diff that follows.

- `react`: the baseline. One agent over `find_evidence` and four edit tools; the
  tools write as they are called (commit 3582bd6 removed it from the service).
- `guard`: the current graph with the judge switched off. The editing agent
  proposes once, the shape guard in `agent.apply.plan` rejects what lost lines,
  numbers or technologies or names something unbacked, and everything that
  passes the guard is applied.
- `judge`: the current design. Proposal, shape guard, judge, apply, with up to
  `editor.MAX_PASSES` passes."""
from __future__ import annotations

from typing import Any, Awaitable, Callable

from agent.context import TurnContext, TurnOutcome

REACT = "react"
GUARD = "guard"
JUDGE = "judge"
ARMS = (REACT, GUARD, JUDGE)

Runner = Callable[..., Awaitable[TurnOutcome]]


async def run_react(context: TurnContext, **models: Any) -> TurnOutcome:
    from evaluation.arms.react.loop import run_turn

    return await run_turn(
        context, model=models.get("model"), intent_model=models.get("intent_model")
    )


async def run_guard(context: TurnContext, **models: Any) -> TurnOutcome:
    from agent.turn import run_turn

    return await run_turn(
        context,
        model=models.get("model"),
        intent_model=models.get("intent_model"),
        judge_enabled=False,
    )


async def run_judge(context: TurnContext, **models: Any) -> TurnOutcome:
    from agent.turn import run_turn

    return await run_turn(
        context,
        model=models.get("model"),
        intent_model=models.get("intent_model"),
        judge_model=models.get("judge_model"),
    )


RUNNERS: dict[str, Runner] = {REACT: run_react, GUARD: run_guard, JUDGE: run_judge}


def runner_for(arm: str) -> Runner:
    if arm not in RUNNERS:
        raise ValueError(f"unknown arm {arm!r}; one of {', '.join(ARMS)}")
    return RUNNERS[arm]
