from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from langchain_core.messages import HumanMessage

from tests.support import intending, scripted, tool_call
from tests.test_turn import RESUME, context_for

from agent import editor, guard
from agent.editor import GUARD_SPAN
from agent.schemas import EditProposal
from agent.state import build_turn_state
from agent.turn import run_turn
from config import reset_settings
from db.models import SpanKind

FIXED = "Backend engineer with eight years on distributed systems, Postgres and Kubernetes."
INVENTED = "Backend engineer with 12 years."


class CorrectionPassTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_a_rejection_goes_back_once_with_its_reason(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": INVENTED, "skills": {"technical": ["Kubernetes"]}}, "1"),
            tool_call("EditProposal", {"reply": "second", "summary": FIXED}, "2"),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, "second")
        self.assertEqual(outcome.resume["summary"], FIXED)
        self.assertEqual(outcome.resume["skills"]["technical"], ["Kubernetes"])
        self.assertEqual(len(model.prompts), 2)
        note = model.prompts[1][-1].content
        self.assertIn(guard.UNBACKED_NUMBER, note)
        self.assertIn("Everything else was applied and stays", note)

    async def test_what_is_still_rejected_after_the_correction_stays_out(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": INVENTED}, "1"),
            tool_call("EditProposal", {"reply": "second", "summary": "Backend engineer with 15 years."}, "2"),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.resume["summary"], RESUME["summary"])
        self.assertEqual(outcome.changes, [])
        self.assertEqual(len(model.prompts), 2)
        guards = [span for span in outcome.spans if span.name == GUARD_SPAN]
        self.assertEqual(json.loads(guards[-1].output), [f"summary: {guard.UNBACKED_NUMBER} (15)"])

    async def test_a_clean_proposal_costs_one_call(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": "done", "summary": FIXED}, "1"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(len(model.prompts), 1)
        self.assertEqual(
            [(span.kind, span.name) for span in outcome.spans[1:]],
            [(SpanKind.LLM, "model"), (SpanKind.TOOL, GUARD_SPAN)],
        )

    async def test_the_guard_span_carries_the_edits_and_the_verdict(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "done", "summary": INVENTED}, "1"),
            tool_call("EditProposal", {"reply": "done"}, "2"),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        first = [span for span in outcome.spans if span.name == GUARD_SPAN][0]
        self.assertEqual(json.loads(first.input)["summary"], INVENTED)
        self.assertNotIn("reply", json.loads(first.input))
        self.assertEqual(json.loads(first.output), [f"summary: {guard.UNBACKED_NUMBER} (12)"])

    async def test_the_model_span_says_which_template_it_ran_under(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": "done"}, "1"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        model_span = [span for span in outcome.spans if span.name == "model"][0]
        self.assertTrue(json.loads(model_span.input)["prompt"].startswith("edit@"))


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
