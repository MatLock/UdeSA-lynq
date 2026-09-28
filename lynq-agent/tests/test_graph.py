from __future__ import annotations

import unittest

from langgraph.graph import END

from agent import editor
from agent.context import Intent
from agent.graph import (
    ADVISE,
    APPLY,
    CLASSIFY,
    JUDGE,
    PROPOSE,
    RECURSION_LIMIT,
    TURN_GRAPH,
    PromptReference,
    mermaid,
    route_apply,
    route_intent,
)


class TurnGraphTest(unittest.TestCase):

    def test_the_graph_has_the_four_agents_and_the_apply_step(self) -> None:
        nodes = set(TURN_GRAPH.get_graph().nodes) - {"__start__", "__end__"}

        self.assertEqual(nodes, {CLASSIFY, ADVISE, PROPOSE, JUDGE, APPLY})

    def test_the_intent_decides_between_advising_and_editing(self) -> None:
        self.assertEqual(route_intent({"intent": Intent.ADVISE}), ADVISE)
        self.assertEqual(route_intent({"intent": Intent.EDIT}), PROPOSE)

    def test_a_rejection_goes_back_to_the_editor_once(self) -> None:
        self.assertEqual(route_apply({"rejections": ["summary: x"], "passes": 1}), PROPOSE)
        self.assertEqual(route_apply({"rejections": ["summary: x"], "passes": editor.MAX_PASSES}), END)
        self.assertEqual(route_apply({"rejections": [], "passes": 1}), END)

    def test_a_proposal_is_always_judged_before_it_is_applied(self) -> None:
        edges = {(edge.source, edge.target) for edge in TURN_GRAPH.get_graph().edges}

        self.assertIn((PROPOSE, JUDGE), edges)
        self.assertIn((JUDGE, APPLY), edges)
        self.assertNotIn((PROPOSE, APPLY), edges)

    def test_advising_never_reaches_the_judge(self) -> None:
        edges = {(edge.source, edge.target) for edge in TURN_GRAPH.get_graph().edges}

        self.assertIn((ADVISE, "__end__"), edges)
        self.assertNotIn((ADVISE, JUDGE), edges)
        self.assertNotIn((ADVISE, PROPOSE), edges)

    def test_the_hard_cap_sits_above_what_the_routes_can_reach(self) -> None:
        longest = 1 + 3 * editor.MAX_PASSES  # classify, then propose+judge+apply per pass
        self.assertGreater(RECURSION_LIMIT, longest)

    def test_the_diagram_can_be_drawn(self) -> None:
        drawing = mermaid()

        for node in (CLASSIFY, ADVISE, PROPOSE, JUDGE, APPLY):
            self.assertIn(node, drawing)

    def test_the_prompt_reference_follows_the_intent(self) -> None:
        prompt = PromptReference()

        self.assertTrue(str(prompt).startswith("edit@"))
        prompt.family = "advise"
        self.assertTrue(str(prompt).startswith("advise@"))
