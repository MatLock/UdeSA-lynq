from __future__ import annotations

import os
import unittest

from tests.fixtures.spanish import ASK_FOR_ADVICE, ASK_FOR_GO
from tests.test_turn import RESUME, context_for

from agent.context import Intent
from agent.graph import APPLY_SPAN
from config import BEDROCK, get_settings, reset_settings
from db.models import SpanKind

LIVE = "AGENT_LIVE_LLM"


def live_llm_enabled() -> bool:
    return os.getenv(LIVE, "").strip().lower() == "true"


def bedrock_enabled() -> bool:
    return live_llm_enabled() and get_settings().llm_provider == BEDROCK


@unittest.skipUnless(live_llm_enabled(), f"{LIVE}=true runs the turns against a real model")
class LiveTurnTest(unittest.IsolatedAsyncioTestCase):
    """What the code guarantees whatever the model does. These are the checks worth
    running against Nova Pro before trusting a prompt change."""

    def setUp(self) -> None:
        reset_settings()
        self.addCleanup(reset_settings)

    async def test_an_edit_turn_changes_only_what_the_schema_allows(self) -> None:
        from agent.turn import run_turn

        outcome = await run_turn(context_for(message=ASK_FOR_GO))

        self.assertEqual(outcome.intent, Intent.EDIT)
        self.assertTrue(outcome.reply.strip())
        self.assertEqual(outcome.resume["personal_info"], RESUME["personal_info"])
        self.assertEqual(outcome.resume["education"], RESUME["education"])
        for before, after in zip(RESUME["work_experience"], outcome.resume["work_experience"]):
            for field in ("company", "position", "start_date", "end_date", "is_current"):
                self.assertEqual(before.get(field), after.get(field))
        for bucket in outcome.resume["skills"].values():
            self.assertNotIn("Go", bucket, "the resume does not back Go")
        self.assertTrue(
            [span for span in outcome.spans if span.name == APPLY_SPAN],
            "every edit turn is judged and applied",
        )
        if bedrock_enabled():
            model_spans = [span for span in outcome.spans if span.kind == SpanKind.LLM and span.name == "model"]
            self.assertTrue(model_spans)
            self.assertIn(
                "EditProposal",
                model_spans[0].output or "",
                "Nova Pro is expected to answer through the schema tool, not in text",
            )
            self.assertIn(
                "Verdict",
                model_spans[1].output or "",
                "the judge is expected to answer through the schema tool, not in text",
            )

    async def test_an_advise_turn_cannot_write(self) -> None:
        from agent.turn import run_turn

        outcome = await run_turn(context_for(message=ASK_FOR_ADVICE))

        if outcome.intent != Intent.ADVISE:
            self.skipTest(f"the intent agent read {outcome.intent!r}; nothing to check on an edit turn")
        self.assertEqual(outcome.changes, [])
        self.assertEqual(outcome.resume, RESUME)
        self.assertTrue(outcome.reply.strip())
