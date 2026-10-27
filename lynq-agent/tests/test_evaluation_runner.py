from __future__ import annotations

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from tests.fixtures.spanish import ASK_FOR_GO, REPLY, WARNING
from tests.support import intending, scripted, tool_call
from tests.test_turn import JOB, NEW_SUMMARY, RESUME

from agent.context import Intent
from config import reset_settings
from evaluation.arms import GUARD, JUDGE, REACT
from evaluation.corpus import Pair, Script
from evaluation.runner import Models, conversation_key, plan_run, recorded, run_all, turn_key

PAIR = Pair(id="p1", job=JOB, base_resume={k: v for k, v in RESUME.items() if k != "personal_info"}, language="es", resume_language="en")
SCRIPT = Script(id="s1", turns=[ASK_FOR_GO])
PROPOSAL = {
    "reply": REPLY, "warnings": [WARNING], "summary": NEW_SUMMARY,
    "skills": {"technical": ["Java", "Postgres", "Kubernetes"]},
}
REJECT_SKILLS = {"parts": [
    {"id": "summary", "ok": True},
    {"id": "skills:technical", "ok": False, "kind": "unsupported_skill", "reason": "no"},
]}


def models(*answers) -> Models:
    return Models(model=scripted(*answers), intent_model=intending(Intent.EDIT), judge_model=None, names={"model": "scripted"})


class RunnerTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)
        self.output = os.path.join(tempfile.mkdtemp(), "runs.jsonl")

    def rows(self) -> list[dict]:
        with open(self.output, encoding="utf-8") as file:
            return [json.loads(line) for line in file if line.strip()]

    async def test_the_judge_arm_records_applied_and_rejected_units(self) -> None:
        plan = plan_run([PAIR], [SCRIPT], [JUDGE])
        written = await run_all(plan, self.output, models(
            tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", REJECT_SKILLS, "2"),
            tool_call("EditProposal", {"reply": REPLY}, "3"),
        ), timeout=30)

        self.assertEqual(written, 1)
        [row] = self.rows()
        self.assertEqual(row["key"], turn_key("p1", "s1", JUDGE, 1))
        self.assertIsNone(row["error"])
        self.assertEqual(row["after"]["summary"], NEW_SUMMARY)
        self.assertEqual([u["section"] for u in row["units"]], ["summary"])
        self.assertTrue(row["units"][0]["applied"])
        self.assertEqual([(u["label"], u["system_kind"]) for u in row["rejected"]], [("technical", "unsupported_skill")])
        self.assertEqual(row["rejected"][0]["original"], "Java, Postgres")
        self.assertEqual(row["faults"], [])
        self.assertGreater(row["model_calls"], 0)
        self.assertEqual(row["candidate_said"], [ASK_FOR_GO])
        self.assertIn("Go", row["gaps_before"])

    async def test_the_guard_arm_never_asks_the_judge(self) -> None:
        plan = plan_run([PAIR], [SCRIPT], [GUARD])
        arm_models = models(tool_call("EditProposal", PROPOSAL, "1"))

        await run_all(plan, self.output, arm_models, timeout=30)

        [row] = self.rows()
        self.assertEqual(row["after"]["summary"], NEW_SUMMARY)
        self.assertEqual(row["after"]["skills"]["technical"], ["Java", "Postgres", "Kubernetes"])
        self.assertEqual(len(arm_models.model.prompts), 1, "one proposal, no verdict")
        self.assertEqual(row["passes"], 1)

    async def test_the_react_arm_runs_through_the_same_runner(self) -> None:
        plan = plan_run([PAIR], [SCRIPT], [REACT])
        await run_all(plan, self.output, models(
            tool_call("reorder_entries", {"section": "work_experience", "order": [1, 0]}, "1"),
            tool_call("TurnAnswer", {"reply": REPLY, "warnings": []}, "2"),
        ), timeout=30)

        [row] = self.rows()
        self.assertEqual(row["arm"], REACT)
        self.assertEqual(row["faults"], ["entries_reordered"])
        self.assertEqual(row["tool_calls"], 1)
        self.assertEqual(row["rejected"], [])

    async def test_a_second_run_skips_what_is_recorded(self) -> None:
        plan = plan_run([PAIR], [SCRIPT], [GUARD])
        await run_all(plan, self.output, models(tool_call("EditProposal", PROPOSAL, "1")), timeout=30)

        written = await run_all(plan, self.output, models(tool_call("EditProposal", PROPOSAL, "1")), timeout=30)

        self.assertEqual(written, 0)
        self.assertEqual(len(self.rows()), 1)

    async def test_a_failed_turn_is_recorded_and_ends_its_conversation(self) -> None:
        two_turns = Script(id="s2", turns=[ASK_FOR_GO, ASK_FOR_GO])
        plan = plan_run([PAIR], [two_turns], [GUARD])
        broken = Models(model=None, intent_model=intending(Intent.EDIT), judge_model=None)

        with patch("evaluation.runner.runner_for", return_value=_explode):
            await run_all(plan_run([PAIR], [two_turns], [GUARD]), self.output, broken, timeout=30)

        rows = self.rows()
        self.assertEqual(len(rows), 1)
        self.assertIn("RuntimeError", rows[0]["error"])
        keys, failed = recorded(self.output)
        self.assertEqual(keys, {turn_key("p1", "s2", GUARD, 1)})
        self.assertEqual(failed, {conversation_key("p1", "s2", GUARD)})
        self.assertEqual(await run_all(plan, self.output, broken, timeout=30), 0, "a failed conversation is not run again")

    def test_the_plan_skips_scripts_a_pair_cannot_fill(self) -> None:
        plan = plan_run([PAIR], [Script(id="s", turns=["{gap9}"])], [GUARD, JUDGE])

        self.assertEqual(plan.conversations, [])
        self.assertEqual(plan.skipped, [("p1", "s")])


async def _explode(context, **models):
    raise RuntimeError("boom")
