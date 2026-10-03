from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.fixtures.spanish import JUDGE_REASON_ES
from tests.support import scripted, tool_call
from tests.test_turn import context_for

from agent import apply
from agent.callbacks import TraceCollector
from agent.judge import UNJUDGED, judge
from agent.schemas import EditProposal, EntryEdit, SkillsEdit
from agent.state import build_turn_state
from config import reset_settings
from db.models import SpanKind

PROPOSAL = EditProposal(
    reply="done",
    summary="Backend engineer with twelve years on distributed systems and Postgres.",
    entries=[EntryEdit(company="Acme", position="Backend Engineer", description="Ran services on Kubernetes.")],
    skills=SkillsEdit(technical=["Java", "Postgres", "Kubernetes"]),
)


class JudgeTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def run_judge(self, model, proposal=PROPOSAL):
        context = context_for()
        state = build_turn_state(context)
        parts, _ = apply.plan(state, proposal)
        collector = TraceCollector(state, "judge@0123456789ab", "version-1")
        approved, rejections = await judge(
            state, parts, model, provider="ollama", language="es",
            job_skills=state.job_skills, callbacks=[collector], retries=0,
        )
        return state, parts, approved, rejections, model

    async def test_the_verdict_splits_the_parts(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": [
            {"id": "summary", "ok": False, "kind": "invented", "reason": JUDGE_REASON_ES},
            {"id": "entry:0", "ok": True},
            {"id": "skills:technical", "ok": True},
        ]}, "1"))

        _, _, approved, rejections, _ = await self.run_judge(model)

        self.assertEqual(approved, {"entry:0", "skills:technical"})
        self.assertEqual(rejections, [f"summary: {JUDGE_REASON_ES}"])
        self.assertEqual((rejections[0].section, rejections[0].kind), ("summary", "invented"))

    async def test_the_quoted_evidence_travels_in_the_trace(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": [
            {"id": "entry:0", "evidence": "Built services deployed on Kubernetes.", "ok": True},
        ]}, "1"))

        state, _, approved, _, _ = await self.run_judge(model)

        self.assertIn("entry:0", approved)
        span = [s for s in state.spans if s.kind == SpanKind.LLM][0]
        self.assertIn("Built services deployed on Kubernetes.", span.output or "")

    async def test_a_part_the_judge_did_not_answer_is_not_approved(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": [{"id": "summary", "ok": True}]}, "1"))

        _, _, approved, rejections, _ = await self.run_judge(model)

        self.assertEqual(approved, {"summary"})
        self.assertEqual([r.kind for r in rejections], [UNJUDGED, UNJUDGED])

    async def test_the_judge_reads_every_part_beside_its_original(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": []}, "1"))

        _, _, _, _, model = await self.run_judge(model)

        prompt = model.prompts[0][0].content
        self.assertIn('<part id="summary" section="summary">', prompt)
        self.assertIn('<original lines="1">\n1| Backend engineer with eight years on distributed systems and Postgres.\n</original>', prompt)
        self.assertIn('<proposed lines="1">\n1| Backend engineer with twelve years on distributed systems and Postgres.\n</proposed>', prompt)
        self.assertIn('<part id="entry:0" section="work_experience" entry="Backend Engineer at Acme">', prompt)
        self.assertIn('<original lines="2">\n1| Java\n2| Postgres\n</original>', prompt)
        self.assertNotIn("Ada Lovelace", prompt)
        self.assertEqual(model.binds[0]["tools"], ["Verdict"])

    async def test_nothing_to_judge_costs_no_call(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": []}, "1"))

        _, _, approved, rejections, model = await self.run_judge(model, EditProposal(reply="done"))

        self.assertEqual((approved, rejections), (set(), []))
        self.assertEqual(model.prompts, [])

    async def test_the_judge_span_is_priced_with_its_own_sheet(self) -> None:
        model = scripted(tool_call("Verdict", {"parts": []}, "1"))

        state, _, _, _, _ = await self.run_judge(model)

        span = [s for s in state.spans if s.kind == SpanKind.LLM][0]
        self.assertIsNotNone(span.cost_usd)
