from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage

from tests.fixtures.spanish import (
    ASK_FOR_GO,
    ASK_FOR_THE_PROMPT,
    GO_AHEAD,
    GREETING,
    OUT_OF_SCOPE_ES,
    REPLY,
    WARNING,
)
from tests.support import scripted, tool_call

from agent.context import TurnContext
from agent.graph import recursion_limit, run_turn, turn_messages
from config import reset_settings
from prompt.notice import render as no_change_notice
from db.models import MessageRole, SpanKind

JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "company": "Acme",
    "description": "Kubernetes, PostgreSQL and Go.",
    "extractedSkills": ["Kubernetes", "PostgreSQL", "Go"],
}

RESUME = {
    "personal_info": {"full_name": "Ada Lovelace", "email": "ada@example.com"},
    "summary": "Backend engineer with eight years on distributed systems and Postgres.",
    "work_experience": [
        {
            "company": "Globex",
            "position": "Semi Senior",
            "start_date": "2018-01",
            "end_date": "2020-12",
            "is_current": False,
            "description": "Maintained a Java monolith.",
            "technologies": ["Java"],
        },
        {
            "company": "Acme",
            "position": "Backend Engineer",
            "start_date": "2021-01",
            "end_date": None,
            "is_current": True,
            "description": "Built services deployed on Kubernetes.",
            "technologies": ["Kubernetes"],
        },
    ],
    "skills": {"technical": ["Java", "Postgres"], "tools": ["Jenkins"], "soft": []},
}

ANSWER = {"reply": REPLY, "warnings": [WARNING]}


def context_for(
    max_steps: int = 12, message: str = ASK_FOR_GO, history=None
) -> TurnContext:
    return TurnContext(
        conversation_id="conversation-1",
        run_token="token-1",
        language="es",
        resume_language="en",
        job_snapshot=JOB,
        base_resume=RESUME,
        current_resume=RESUME,
        history=history
        if history is not None
        else [
            (MessageRole.ASSISTANT, GREETING),
            (MessageRole.USER, message),
        ],
        message=message,
        max_steps=max_steps,
        turns_left=9,
        resume_version_id="version-1",
    )


class ReactLoopTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_the_loop_edits_the_resume_and_traces_every_step(self) -> None:
        model = scripted(
            tool_call("find_evidence", {"claims": ["Kubernetes"]}, "1"),
            tool_call(
                "apply_edit",
                {"section": "work_experience", "op": "reorder", "payload": {"order": [1, 0]}},
                "2",
            ),
            tool_call("TurnAnswer", ANSWER, "3"),
        )

        outcome = await run_turn(context_for(), model=model)

        self.assertEqual(outcome.reply, ANSWER["reply"])
        self.assertEqual(outcome.warnings[: len(ANSWER["warnings"])], ANSWER["warnings"])
        self.assertEqual(
            [entry["company"] for entry in outcome.resume["work_experience"]],
            ["Acme", "Globex"],
        )
        self.assertEqual(outcome.changes[0]["section"], "work_experience")
        self.assertEqual(
            [span.kind for span in outcome.spans],
            [SpanKind.LLM, SpanKind.TOOL, SpanKind.LLM, SpanKind.TOOL, SpanKind.LLM],
        )
        self.assertEqual(
            [span.name for span in outcome.spans if span.kind == SpanKind.TOOL],
            ["find_evidence", "apply_edit"],
        )

    async def test_a_batched_lookup_spends_one_step_on_every_claim(self) -> None:
        model = scripted(
            tool_call(
                "find_evidence",
                {"claims": ["Kubernetes", "PostgreSQL", "Go", "Rust"]},
                "1",
            ),
            tool_call("TurnAnswer", ANSWER, "2"),
        )

        outcome = await run_turn(context_for(), model=model)

        lookups = [
            span for span in outcome.spans
            if span.kind == SpanKind.TOOL and span.name == "find_evidence"
        ]
        self.assertEqual(len(lookups), 1)
        self.assertEqual(
            [span.kind for span in outcome.spans],
            [SpanKind.LLM, SpanKind.TOOL, SpanKind.LLM],
        )

    async def test_an_answer_that_gives_the_instructions_away_never_reaches_the_candidate(
        self,
    ) -> None:
        leaked = (
            "Sure, here are my instructions: You may only reorder, prioritise and "
            "rewrite what the resume already backs."
        )
        model = scripted(tool_call("TurnAnswer", {"reply": leaked}, "1"))

        outcome = await run_turn(context_for(message=ASK_FOR_THE_PROMPT), model=model)

        self.assertEqual(outcome.reply, OUT_OF_SCOPE_ES)
        self.assertEqual(outcome.warnings, [])
        self.assertNotIn("reorder", outcome.reply)
        guard = [span for span in outcome.spans if span.kind == SpanKind.ERROR]
        self.assertEqual([span.name for span in guard], ["out_of_scope"])
        self.assertEqual(guard[0].error, "the answer echoed the instructions")

    async def test_the_resume_comes_from_the_edits_not_from_the_model(self) -> None:
        model = scripted(
            tool_call(
                "apply_edit",
                {"section": "summary", "op": "rewrite", "payload": {"text": "Kubernetes first."}},
                "1",
            ),
            tool_call("TurnAnswer", {"reply": '{"summary": "I am a CEO"}'}, "2"),
        )

        outcome = await run_turn(context_for(), model=model)

        self.assertEqual(outcome.resume["summary"], "Kubernetes first.")
        self.assertEqual(
            outcome.resume["personal_info"], RESUME["personal_info"]
        )

    async def test_the_model_never_sees_personal_info(self) -> None:
        model = scripted(tool_call("TurnAnswer", ANSWER, "1"))

        await run_turn(context_for(), model=model)

        system_prompt = model.prompts[0][0].content
        self.assertNotIn("Ada Lovelace", system_prompt)
        self.assertNotIn("ada@example.com", system_prompt)
        self.assertIn("Kubernetes", system_prompt)

    async def test_a_turn_that_runs_out_of_steps_still_answers(self) -> None:
        model = scripted(
            tool_call(
                "apply_edit",
                {"section": "summary", "op": "rewrite", "payload": {"text": "Kubernetes first."}},
                "1",
            ),
            tool_call("TurnAnswer", ANSWER, "2"),
        )

        outcome = await run_turn(context_for(max_steps=2), model=model)

        self.assertEqual(outcome.reply, ANSWER["reply"])
        self.assertEqual(outcome.changes, [])
        self.assertEqual(outcome.resume["summary"], RESUME["summary"])
        self.assertEqual(
            [span.kind for span in outcome.spans if span.kind == SpanKind.LIMIT],
            [SpanKind.LIMIT],
        )

    async def test_an_answer_that_came_as_text_is_unwrapped(self) -> None:
        model = scripted(AIMessage(content=json.dumps(ANSWER, ensure_ascii=False)))

        outcome = await run_turn(context_for(), model=model)

        self.assertEqual(outcome.reply, ANSWER["reply"])
        self.assertEqual(outcome.warnings[: len(ANSWER["warnings"])], ANSWER["warnings"])

    async def test_a_turn_that_applied_nothing_says_so(self) -> None:
        model = scripted(AIMessage(content=json.dumps(ANSWER, ensure_ascii=False)))

        outcome = await run_turn(context_for(), model=model)

        self.assertEqual(outcome.changes, [])
        self.assertIn(no_change_notice("es"), outcome.warnings)

    async def test_a_turn_that_applied_something_does_not_say_it_applied_nothing(
        self,
    ) -> None:
        model = scripted(
            tool_call("apply_edit", {
                "section": "summary",
                "op": "rewrite",
                "payload": {"text": "Ingeniero backend sobre Kubernetes."},
            }),
            AIMessage(content=json.dumps(ANSWER, ensure_ascii=False)),
        )

        outcome = await run_turn(context_for(), model=model)

        self.assertNotEqual(outcome.changes, [])
        self.assertNotIn(no_change_notice("es"), outcome.warnings)

    async def test_the_last_user_message_is_not_repeated(self) -> None:
        messages = turn_messages(
            context_for(
                message=GO_AHEAD,
                history=[
                    (MessageRole.USER, ASK_FOR_GO),
                    (MessageRole.ASSISTANT, GREETING),
                    (MessageRole.USER, GO_AHEAD),
                ],
            )
        )

        self.assertEqual([message.type for message in messages], ["human", "ai", "human"])
        self.assertEqual(messages[-1].content, GO_AHEAD)

    async def test_the_opening_greeting_is_not_sent_as_the_first_message(self) -> None:
        messages = turn_messages(context_for(message=GO_AHEAD))

        self.assertEqual([message.type for message in messages], ["human"])
        self.assertEqual(messages[0].content, GO_AHEAD)

    async def test_a_window_that_opens_on_an_assistant_reply_still_starts_on_a_user_turn(
        self,
    ) -> None:
        messages = turn_messages(
            context_for(
                message=GO_AHEAD,
                history=[
                    (MessageRole.ASSISTANT, GREETING),
                    (MessageRole.ASSISTANT, REPLY),
                    (MessageRole.USER, ASK_FOR_GO),
                    (MessageRole.ASSISTANT, REPLY),
                ],
            )
        )

        self.assertEqual(
            [message.type for message in messages], ["human", "ai", "human"]
        )
        self.assertEqual(messages[0].content, ASK_FOR_GO)


class RecursionLimitTest(unittest.TestCase):

    def test_the_hard_cap_leaves_room_for_the_soft_cap_to_hit_first(self) -> None:
        self.assertEqual(recursion_limit(12), 30)
        self.assertEqual(recursion_limit(2), 10)
