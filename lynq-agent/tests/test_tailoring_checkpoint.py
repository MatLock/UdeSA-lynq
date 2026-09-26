from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.fixtures.spanish import RESUME as SPANISH_RESUME
from tests.fixtures.spanish import SUMMARY_REWRITE, WARNING
from tests.support import scripted, tool_call
from tests.test_react_loop import JOB, RESUME, context_for

from agent.context import TurnContext
from agent.graph import run_turn
from config import reset_settings
from prompt.notice import render as no_change_notice
from db.models import SpanKind

ENGLISH_REWRITE = (
    "Backend engineer with eight years leading distributed services, database "
    "migrations and product teams at large companies."
)


def spanish_context() -> TurnContext:
    return TurnContext(
        conversation_id="conversation-2",
        run_token="token-2",
        language="en",
        resume_language="es",
        job_snapshot=JOB,
        base_resume=SPANISH_RESUME,
        current_resume=SPANISH_RESUME,
        history=[],
        message="Rewrite my summary for this job",
        max_steps=12,
        turns_left=9,
    )


class CheckpointTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_a_skill_the_resume_does_not_back_never_enters_it(self) -> None:
        model = scripted(
            tool_call("find_evidence", {"claims": ["Go"]}, "1"),
            tool_call(
                "apply_edit",
                {
                    "section": "skills",
                    "op": "replace",
                    "payload": {"technical": ["Java", "Postgres", "Go"]},
                },
                "2",
            ),
            tool_call(
                "TurnAnswer",
                {"reply": "I could not find Go in your resume.", "warnings": [WARNING]},
                "3",
            ),
        )

        outcome = await run_turn(context_for(), model=model)

        self.assertEqual(outcome.resume["skills"], RESUME["skills"])
        self.assertEqual(outcome.changes, [])
        self.assertEqual(outcome.warnings, [WARNING, no_change_notice("es")])

        tools = {span.name: span for span in outcome.spans if span.kind == SpanKind.TOOL}
        self.assertEqual(
            tools["find_evidence"].output, '[{"claim": "Go", "hits": []}]'
        )
        self.assertEqual(
            tools["apply_edit"].output, "REJECTED: no evidence in base resume"
        )

    async def test_the_agent_talks_in_one_language_and_edits_in_the_other(self) -> None:
        model = scripted(
            tool_call(
                "apply_edit",
                {"section": "summary", "op": "rewrite", "payload": {"text": ENGLISH_REWRITE}},
                "1",
            ),
            tool_call(
                "apply_edit",
                {"section": "summary", "op": "rewrite", "payload": {"text": SUMMARY_REWRITE}},
                "2",
            ),
            tool_call("TurnAnswer", {"reply": "I rewrote your summary."}, "3"),
        )

        outcome = await run_turn(spanish_context(), model=model)

        self.assertEqual(outcome.reply, "I rewrote your summary.")
        self.assertEqual(outcome.resume["summary"], SUMMARY_REWRITE)
        self.assertEqual(len(outcome.changes), 1)

        rejections = [
            span.output
            for span in outcome.spans
            if span.kind == SpanKind.TOOL and str(span.output).startswith("REJECTED")
        ]
        self.assertEqual(
            rejections,
            ["REJECTED: payload language (en) does not match resume language (es)"],
        )

    async def test_the_prompt_of_that_turn_asks_for_english_over_a_spanish_resume(
        self,
    ) -> None:
        model = scripted(tool_call("TurnAnswer", {"reply": "Nothing to change."}, "1"))

        await run_turn(spanish_context(), model=model)

        system_prompt = model.prompts[0][0].content
        self.assertIn("Write `reply` and `warnings` in English (en)", system_prompt)
        self.assertIn("must be in Spanish (es)", system_prompt)
        self.assertIn("Never translate the resume", system_prompt)
