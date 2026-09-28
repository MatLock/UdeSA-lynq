from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from tests.support import scripted, tool_call
from tests.test_turn import RESUME, context_for

from agent import guard
from agent.callbacks import TraceCollector
from agent.editor import GUARD_SPAN, edit
from agent.state import build_turn_state
from agent.turn import turn_messages
from config import reset_settings
from db.models import SpanKind

FIXED = "Backend engineer with eight years on distributed systems, Postgres and Kubernetes."


class EditorTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def run_editor(self, model, context=None):
        context = context or context_for()
        state = build_turn_state(context)
        collector = TraceCollector(state, "edit@0123456789ab", "version-1")
        proposal, rejections = await edit(
            context, state, model,
            provider="ollama", callbacks=[collector], retries=0, messages=turn_messages(context),
        )
        return state, proposal, rejections

    async def test_a_rejection_goes_back_once_with_its_reason(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": "Backend engineer with 12 years.", "skills": {"technical": ["Kubernetes"]}}, "1"),
            tool_call("EditProposal", {"reply": "second", "summary": FIXED}, "2"),
        )

        state, proposal, rejections = await self.run_editor(model)

        self.assertEqual(rejections, [])
        self.assertEqual(proposal.reply, "second")
        self.assertEqual(state.resume["summary"], FIXED)
        self.assertEqual(state.resume["skills"]["technical"], ["Kubernetes"])
        self.assertEqual(len(model.prompts), 2)
        note = model.prompts[1][-1].content
        self.assertIn(guard.UNBACKED_NUMBER, note)
        self.assertIn("Everything else was applied and stays", note)

    async def test_what_is_still_rejected_after_the_retry_stays_out(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": "first", "summary": "Backend engineer with 12 years."}, "1"),
            tool_call("EditProposal", {"reply": "second", "summary": "Backend engineer with 15 years."}, "2"),
        )

        state, proposal, rejections = await self.run_editor(model)

        self.assertEqual(rejections, [f"summary: {guard.UNBACKED_NUMBER} (15)"])
        self.assertEqual(state.resume["summary"], RESUME["summary"])
        self.assertEqual(state.changes, [])

    async def test_a_clean_proposal_costs_one_call(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": "done", "summary": FIXED}, "1"))

        state, _, rejections = await self.run_editor(model)

        self.assertEqual(rejections, [])
        self.assertEqual(len(model.prompts), 1)
        self.assertEqual(
            [(span.kind, span.name) for span in state.spans],
            [(SpanKind.LLM, "model"), (SpanKind.TOOL, GUARD_SPAN)],
        )

    async def test_the_guard_span_carries_the_edits_and_the_verdict(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": "done", "summary": "Backend engineer with 12 years."}, "1"), tool_call("EditProposal", {"reply": "done"}, "2"))

        state, _, _ = await self.run_editor(model)

        first = [span for span in state.spans if span.name == GUARD_SPAN][0]
        self.assertEqual(json.loads(first.input)["summary"], "Backend engineer with 12 years.")
        self.assertNotIn("reply", json.loads(first.input))
        self.assertEqual(json.loads(first.output), [f"summary: {guard.UNBACKED_NUMBER} (12)"])
