from __future__ import annotations

import logging
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from agent import editor
from agent.advisor import advise
from agent.context import Intent, TurnContext
from agent.intent import classify
from agent.schemas import Advice, EditProposal
from agent.state import TurnState
from prompt.tailor import ADVISE as ADVISE_PROMPT, EDIT as EDIT_PROMPT, reference

log = logging.getLogger(__name__)

CLASSIFY = "classify"
ADVISE = "advise"
PROPOSE = "propose"
GUARD = "guard"


class PromptReference:
    """The trace records which template a model span ran under. The family is
    only known once the intent is read, so the reference resolves itself late:
    the collector stringifies it when it writes the span."""

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
    callbacks: list
    retries: int
    messages: list
    prompt: PromptReference
    intent: str
    thread: list
    proposal: EditProposal
    rejections: list[str]
    passes: int
    advice: Advice


async def classify_node(graph: TurnGraphState) -> TurnGraphState:
    intent = await classify(graph["context"], graph["provider"], graph.get("intent_model"))
    graph["prompt"].family = ADVISE_PROMPT if intent == Intent.ADVISE else EDIT_PROMPT
    return {"intent": intent}


async def advise_node(graph: TurnGraphState) -> TurnGraphState:
    advice = await advise(
        graph["context"],
        graph["state"],
        graph["model"],
        provider=graph["provider"],
        callbacks=graph["callbacks"],
        retries=graph["retries"],
        messages=graph["messages"],
    )
    return {"advice": advice}


async def propose_node(graph: TurnGraphState) -> TurnGraphState:
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


def guard_node(graph: TurnGraphState) -> TurnGraphState:
    rejections = editor.check(graph["state"], graph["proposal"])
    return {"rejections": rejections, "passes": graph.get("passes", 0) + 1}


def route_intent(graph: TurnGraphState) -> str:
    return ADVISE if graph["intent"] == Intent.ADVISE else PROPOSE


def route_guard(graph: TurnGraphState) -> str:
    if graph["rejections"] and graph["passes"] < editor.MAX_PASSES:
        return PROPOSE
    return END


def build_graph():
    graph = StateGraph(TurnGraphState)
    graph.add_node(CLASSIFY, classify_node)
    graph.add_node(ADVISE, advise_node)
    graph.add_node(PROPOSE, propose_node)
    graph.add_node(GUARD, guard_node)

    graph.add_edge(START, CLASSIFY)
    graph.add_conditional_edges(CLASSIFY, route_intent, [ADVISE, PROPOSE])
    graph.add_edge(ADVISE, END)
    graph.add_edge(PROPOSE, GUARD)
    graph.add_conditional_edges(GUARD, route_guard, [PROPOSE, END])
    return graph.compile()


TURN_GRAPH = build_graph()

# Every pass through propose+guard is two nodes; the cap is a hard backstop the
# guard's own route never reaches, so it does not turn a valid turn into an error.
RECURSION_LIMIT = 2 + 2 * editor.MAX_PASSES + 4


def mermaid() -> str:
    return TURN_GRAPH.get_graph().draw_mermaid()
