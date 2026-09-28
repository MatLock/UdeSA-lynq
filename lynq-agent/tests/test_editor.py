from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from langchain_core.messages import HumanMessage

from tests.fixtures.spanish import JUDGE_REASON_ES
from tests.support import intending, scripted, tool_call
from tests.test_turn import RESUME, context_for

from agent import editor
from agent.apply import Rejection
from agent.graph import APPLY_SPAN
from agent.schemas import EditProposal
from agent.state import build_turn_state
from agent.turn import run_turn
from config import reset_settings
from db.models import SpanKind
from prompt.rejection import render as rejection_notice

FIXED = "Backend engineer with eight years on distributed systems, Postgres and Kubernetes."
INVENTED = "Backend engineer with 12 years."


def verdict(*parts):
    return tool_call("Verdict", {"parts": [dict(p) for p in parts]}, "v")


class CorrectionPassTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_a_rejection_goes_back_once_with_its_reason(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": INVENTED, "skills": {"technical": ["Java", "Postgres", "Kubernetes"]}}, "1"),
            verdict({"id": "summary", "ok": False, "kind": "invented", "reason": JUDGE_REASON_ES}, {"id": "skills:technical", "ok": True}),
            tool_call("EditProposal", {"reply": "second", "summary": FIXED}, "2"),
            verdict({"id": "summary", "ok": True}),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, "second")
        self.assertEqual(outcome.resume["summary"], FIXED)
        self.assertEqual(outcome.resume["skills"]["technical"], ["Java", "Postgres", "Kubernetes"])
        self.assertEqual(outcome.warnings, [])
        self.assertEqual(len(model.prompts), 4)
        note = model.prompts[2][-1].content
        self.assertIn(JUDGE_REASON_ES, note)
        self.assertIn("Everything else was applied and stays", note)

    async def test_what_is_still_rejected_after_the_correction_stays_out_and_is_told(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": INVENTED}, "1"),
            verdict({"id": "summary", "ok": False, "kind": "invented", "reason": JUDGE_REASON_ES}),
            tool_call("EditProposal", {"reply": "second", "summary": "Backend engineer with 15 years."}, "2"),
            verdict({"id": "summary", "ok": False, "kind": "invented", "reason": JUDGE_REASON_ES}),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.resume["summary"], RESUME["summary"])
        self.assertEqual(outcome.changes, [])
        self.assertEqual(len(model.prompts), 4)
        self.assertIn(rejection_notice("es", [Rejection("summary", "", "invented", JUDGE_REASON_ES)]), outcome.warnings)

    async def test_a_clean_proposal_costs_two_calls(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "done", "summary": FIXED}, "1"),
            verdict({"id": "summary", "ok": True}),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(len(model.prompts), 2)
        self.assertEqual(
            [(span.kind, span.name) for span in outcome.spans[1:]],
            [(SpanKind.LLM, "model"), (SpanKind.LLM, "model"), (SpanKind.TOOL, APPLY_SPAN)],
        )
        self.assertEqual(outcome.spans[-1].output, "OK")

    async def test_the_apply_span_carries_the_parts_and_the_verdict(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "done", "summary": INVENTED}, "1"),
            verdict({"id": "summary", "ok": False, "kind": "invented", "reason": JUDGE_REASON_ES}),
            tool_call("EditProposal", {"reply": "done"}, "2"),
            verdict(),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        first = [span for span in outcome.spans if span.name == APPLY_SPAN][0]
        self.assertEqual(json.loads(first.input), [{"id": "summary", "proposed": INVENTED}])
        self.assertEqual(json.loads(first.output), [{"where": f"summary: {JUDGE_REASON_ES}", "kind": "invented"}])

    async def test_each_model_span_says_which_template_it_ran_under(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "done", "summary": FIXED}, "1"),
            verdict({"id": "summary", "ok": True}),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        references = [json.loads(s.input)["prompt"].split("@")[0] for s in outcome.spans if s.name == "model"]
        self.assertEqual(references, ["edit", "judge"])

    async def test_a_separate_judge_model_may_be_given(self) -> None:
        editor_model = scripted(tool_call("EditProposal", {"reply": "done", "summary": FIXED}, "1"))
        judge_model = scripted(verdict({"id": "summary", "ok": True}))

        outcome = await run_turn(context_for(), model=editor_model, intent_model=intending(), judge_model=judge_model)

        self.assertEqual(outcome.resume["summary"], FIXED)
        self.assertEqual(judge_model.binds[0]["tools"], ["Verdict"])


class ThreadTest(unittest.TestCase):

    def test_the_correction_continues_the_same_thread(self) -> None:
        proposal = EditProposal(reply="first", summary=INVENTED)
        thread = editor.retry_thread([HumanMessage("go")], proposal, ["summary: x"])

        self.assertEqual([message.type for message in thread], ["human", "ai", "human"])
        self.assertIn(INVENTED, thread[1].content)
        self.assertIn("- summary: x", thread[2].content)

    def test_the_opening_thread_starts_with_the_edit_prompt(self) -> None:
        context = context_for()
        thread = editor.opening_thread(context, build_turn_state(context), "ollama", [HumanMessage("go")])

        self.assertEqual([message.type for message in thread], ["system", "human"])
        self.assertIn("EditProposal", thread[0].content)
        self.assertNotIn("Ada Lovelace", thread[0].content)
