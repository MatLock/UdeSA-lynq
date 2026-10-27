from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.fixtures.spanish import ASK_FOR_GO, REPLY, WARNING
from tests.support import intending, scripted, tool_call
from tests.test_turn import context_for

from agent.context import INTENT_SPAN, Intent
from config import reset_settings
from db.models import SpanKind
from evaluation.arms.react.loop import react_intent, recursion_limit, run_turn

ANSWER = {"reply": REPLY, "warnings": [WARNING]}


class ReactArmTest(unittest.IsolatedAsyncioTestCase):
    """The baseline as it was: tools that write as they are called, a loop the
    model stops, and a reorder tool the current design no longer has."""

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_the_loop_can_reorder_the_experience(self) -> None:
        model = scripted(
            tool_call("find_evidence", {"claims": ["Kubernetes"]}, "1"),
            tool_call("reorder_entries", {"section": "work_experience", "order": [1, 0]}, "2"),
            tool_call("TurnAnswer", ANSWER, "3"),
        )

        outcome = await run_turn(context_for(message=ASK_FOR_GO), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, REPLY)
        self.assertEqual([e["company"] for e in outcome.resume["work_experience"]], ["Acme", "Globex"])
        self.assertEqual(outcome.changes[0]["kind"], "reorder")
        self.assertEqual(outcome.spans[0].name, INTENT_SPAN)
        self.assertEqual(
            [s.name for s in outcome.spans if s.kind == SpanKind.TOOL],
            ["find_evidence", "reorder_entries"],
        )
        self.assertEqual(outcome.resume["personal_info"], context_for().base_resume["personal_info"])

    async def test_a_skill_without_evidence_is_rejected_by_the_tool(self) -> None:
        model = scripted(
            tool_call("replace_skills", {"technical": ["Java", "Postgres", "Go"]}, "1"),
            tool_call("TurnAnswer", ANSWER, "2"),
        )

        outcome = await run_turn(context_for(message=ASK_FOR_GO), model=model, intent_model=intending())

        rejected = [s for s in outcome.spans if s.kind == SpanKind.TOOL and (s.output or "").startswith("REJECTED")]
        self.assertEqual(len(rejected), 1)
        self.assertNotIn("Go", outcome.resume["skills"]["technical"])
        self.assertEqual(outcome.changes, [])

    async def test_an_advise_turn_holds_no_edit_tool(self) -> None:
        model = scripted(tool_call("TurnAnswer", ANSWER, "1"))

        outcome = await run_turn(context_for(message=ASK_FOR_GO), model=model, intent_model=intending(Intent.ADVISE))

        self.assertEqual(outcome.intent, Intent.ADVISE)
        bound = model.binds[-1]["tools"]
        self.assertIn("find_evidence", bound)
        for edit_tool in ("rewrite_summary", "rewrite_entry", "reorder_entries", "replace_skills"):
            self.assertNotIn(edit_tool, bound)

    def test_the_unknown_intent_falls_back_to_edit_as_the_old_loop_did(self) -> None:
        self.assertEqual(react_intent(Intent.UNKNOWN), Intent.EDIT)
        self.assertEqual(react_intent(Intent.ADVISE), Intent.ADVISE)
        self.assertEqual(react_intent(Intent.EDIT), Intent.EDIT)

    def test_the_recursion_limit_leaves_headroom_over_the_steps(self) -> None:
        self.assertGreater(recursion_limit(12), 24)
