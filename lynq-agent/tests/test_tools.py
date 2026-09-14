from __future__ import annotations

import json
import unittest
from decimal import Decimal
from unittest.mock import AsyncMock

from tests.support import JOB, base_resume

from agent.context import STEP_LIMIT_MESSAGE, TurnContext
from agent.editor import ResumeEditor
from agent.tools import build_tools
from client import LynqMlClient, MlError


def _context(max_steps: int = 12) -> TurnContext:
    base = base_resume()
    return TurnContext(
        conversation_id="conv-1",
        job_snapshot=JOB,
        base_resume=base,
        editor=ResumeEditor(base, base_resume()),
        max_steps=max_steps,
        language="es",
        input_price_per_1m=Decimal("0.8"),
        output_price_per_1m=Decimal("3.2"),
    )


class ToolsTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.context = _context()
        self.ml_client = LynqMlClient("http://ml", "system", 1.0)
        self.limits: list[str] = []
        self.tools = {
            tool.name: tool
            for tool in build_tools(
                self.context, self.ml_client, "uuid-1", self.limits.append
            )
        }

    async def test_job_requirements_merges_the_snapshot_with_the_extraction(self):
        self.ml_client.job_skills = AsyncMock(return_value=["Docker", "Kubernetes"])

        raw = await self.tools["job_requirements"].ainvoke({})

        requirements = json.loads(raw)
        self.assertIn("Kubernetes", requirements)
        self.assertIn("Docker", requirements)
        self.assertEqual(len([r for r in requirements if r == "Kubernetes"]), 1)

    async def test_job_requirements_is_cached_after_the_first_call(self):
        self.ml_client.job_skills = AsyncMock(return_value=["Docker"])

        await self.tools["job_requirements"].ainvoke({})
        await self.tools["job_requirements"].ainvoke({})

        self.ml_client.job_skills.assert_awaited_once()

    async def test_a_failing_extraction_falls_back_to_the_posting_skills(self):
        self.ml_client.job_skills = AsyncMock(side_effect=MlError("ml is down"))

        requirements = json.loads(await self.tools["job_requirements"].ainvoke({}))

        self.assertEqual(requirements, JOB["skills"] + JOB["similarity_tags"])

    async def test_find_evidence_returns_paths_and_records_them(self):
        raw = await self.tools["find_evidence"].ainvoke({"claim": "Jenkins"})

        self.assertEqual(json.loads(raw), ["$.work_experience[0].description"])
        self.assertEqual(self.context.evidence_log[0]["claim"], "Jenkins")

    async def test_find_evidence_says_plainly_when_there_is_none(self):
        raw = await self.tools["find_evidence"].ainvoke({"claim": "Go"})

        self.assertTrue(raw.startswith("SIN EVIDENCIA"))
        self.assertEqual(self.context.evidence_log[0]["paths"], [])

    async def test_apply_edit_reports_the_rejection_reason_back_to_the_model(self):
        raw = await self.tools["apply_edit"].ainvoke(
            {"section": "skills", "op": "add", "payload": {"name": "Go"}}
        )

        self.assertTrue(raw.startswith("RECHAZADO"))
        self.assertIn("no hay evidencia", raw)

    async def test_apply_edit_answers_ok_when_it_lands(self):
        raw = await self.tools["apply_edit"].ainvoke(
            {
                "section": "summary",
                "op": "rewrite",
                "payload": {"text": "Backend con Kubernetes."},
            }
        )

        self.assertEqual(raw, "OK")
        self.assertEqual(len(self.context.changes), 1)

    async def test_the_soft_cap_stops_the_tools_and_is_reported_once(self):
        context = _context(max_steps=2)
        limits: list[str] = []
        tools = {
            tool.name: tool
            for tool in build_tools(context, self.ml_client, "uuid-1", limits.append)
        }

        await tools["find_evidence"].ainvoke({"claim": "Jenkins"})
        await tools["find_evidence"].ainvoke({"claim": "Postgres"})
        blocked = await tools["find_evidence"].ainvoke({"claim": "Docker"})
        blocked_again = await tools["apply_edit"].ainvoke(
            {"section": "summary", "op": "rewrite", "payload": {"text": "x"}}
        )

        self.assertEqual(blocked, STEP_LIMIT_MESSAGE)
        self.assertEqual(blocked_again, STEP_LIMIT_MESSAGE)
        self.assertEqual(len(limits), 1)
        self.assertEqual(context.tool_steps, 2)


if __name__ == "__main__":
    unittest.main()
