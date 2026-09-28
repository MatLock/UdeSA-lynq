from __future__ import annotations

import json
import logging
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from agent import apply, editor
from agent.advisor import advise
from agent.context import Intent, SpanRecord, TurnContext
from agent.intent import classify
from agent.judge import judge
from agent.schemas import Advice, EditProposal
from agent.state import TurnState
from db.models import SpanKind
from prompt.tailor import ADVISE as ADVISE_PROMPT, EDIT as EDIT_PROMPT, JUDGE as JUDGE_PROMPT, reference

log = logging.getLogger(__name__)

CLASSIFY = "classify"
ADVISE = "advise"
PROPOSE = "propose"
JUDGE = "judge"
APPLY = "apply"
APPLY_SPAN = "apply"
OK = "OK"


class PromptReference:
    """The trace records which template a model span ran under. Three templates
    take turns inside one graph run, so the node about to call the model sets the
    family and the collector stringifies it when it writes the span."""

    def __init__(self) -> None:
        self.family = EDIT_PROMPT

    def __str__(self) -> str:
        return reference(self.family)


class TurnGraphState(TypedDict, total=False):
    """What flows between the nodes. The context and the state are the same
    objects the service built; the rest is what each node leaves for the next."""

    context: TurnContext
    state: TurnState
    provider: str
    model: Any
    intent_model: Any
    judge_model: Any
    callbacks: list
    retries: int
    messages: list
    prompt: PromptReference
    intent: str
    thread: list
    proposal: EditProposal
    parts: list
    approved: set
    rejections: list
    passes: int
    advice: Advice


async def classify_node(graph: TurnGraphState) -> TurnGraphState:
    intent = await classify(graph["context"], graph["provider"], graph.get("intent_model"))
    return {"intent": intent}


async def advise_node(graph: TurnGraphState) -> TurnGraphState:
    graph["prompt"].family = ADVISE_PROMPT
    advice = await advise(
        graph["context"], graph["state"], graph["model"],
        provider=graph["provider"], callbacks=graph["callbacks"],
        retries=graph["retries"], messages=graph["messages"],
    )
    return {"advice": advice}


async def propose_node(graph: TurnGraphState) -> TurnGraphState:
    graph["prompt"].family = EDIT_PROMPT
    thread = graph.get("thread")
    if thread is None:
        thread = editor.opening_thread(
            graph["context"], graph["state"], graph["provider"], graph["messages"]
        )
    else:
        thread = editor.retry_thread(thread, graph["proposal"], graph["rejections"])
    proposal = await editor.propose(
        graph["model"], thread, callbacks=graph["callbacks"], retries=graph["retries"]
    )
    return {"thread": thread, "proposal": proposal}


async def judge_node(graph: TurnGraphState) -> TurnGraphState:
    """The judging agent reads the proposal part by part beside the text each part
    replaces. A part with nowhere to go (an entry the resume does not have) never
    reaches it: that is the one thing the code decides."""
    graph["prompt"].family = JUDGE_PROMPT
    state, context = graph["state"], graph["context"]
    parts, misplaced = apply.plan(state, graph["proposal"])
    approved, rejected = await judge(
        state, parts, graph.get("judge_model") or graph["model"],
        provider=graph["provider"], language=context.language,
        job_skills=state.job_skills, callbacks=graph["callbacks"], retries=graph["retries"],
    )
    return {"parts": parts, "approved": approved, "rejections": [*misplaced, *rejected]}


def apply_node(graph: TurnGraphState) -> TurnGraphState:
    state = graph["state"]
    apply.commit(state, graph["parts"], graph["approved"])
    rejections = graph["rejections"]
    state.spans.append(
        SpanRecord(
            step=state.next_step(), kind=SpanKind.TOOL, name=APPLY_SPAN,
            input=json.dumps(
                [{"id": part.id, "proposed": part.proposed} for part in graph["parts"]],
                ensure_ascii=False,
            ),
            output=OK if not rejections else json.dumps(
                [{"where": str(r), "kind": r.kind} for r in rejections], ensure_ascii=False
            ),
        )
    )
    return {"passes": graph.get("passes", 0) + 1}


def route_intent(graph: TurnGraphState) -> str:
    return ADVISE if graph["intent"] == Intent.ADVISE else PROPOSE


def route_apply(graph: TurnGraphState) -> str:
    if graph["rejections"] and graph["passes"] < editor.MAX_PASSES:
        return PROPOSE
    return END


def build_graph():
    graph = StateGraph(TurnGraphState)
    graph.add_node(CLASSIFY, classify_node)
    graph.add_node(ADVISE, advise_node)
    graph.add_node(PROPOSE, propose_node)
    graph.add_node(JUDGE, judge_node)
    graph.add_node(APPLY, apply_node)

    graph.add_edge(START, CLASSIFY)
    graph.add_conditional_edges(CLASSIFY, route_intent, [ADVISE, PROPOSE])
    graph.add_edge(ADVISE, END)
    graph.add_edge(PROPOSE, JUDGE)
    graph.add_edge(JUDGE, APPLY)
    graph.add_conditional_edges(APPLY, route_apply, [PROPOSE, END])
    return graph.compile()


TURN_GRAPH = build_graph()

# A pass through propose, judge and apply is three nodes; the cap is a hard
# backstop the apply route never reaches, so it never turns a valid turn into
# an error.
RECURSION_LIMIT = 2 + 3 * editor.MAX_PASSES + 4


def mermaid() -> str:
    return TURN_GRAPH.get_graph().draw_mermaid()
