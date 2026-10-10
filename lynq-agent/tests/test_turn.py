from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from botocore.exceptions import ClientError
from langchain_core.messages import AIMessage

from tests.fixtures.spanish import (
    ASK_FOR_ADVICE,
    ASK_FOR_GO,
    CONFIRMED_SPRING_BOOT,
    GO_AHEAD,
    GREETING,
    REPLY,
    WARNING,
    YES,
)
from tests.support import bedrock_error, breaking, intending, scripted, tool_call

from agent.context import INTENT_SPAN, Intent, TurnContext
from agent.graph import APPLY_SPAN
from agent.turn import run_turn, turn_messages
from config import reset_settings
from db.models import MessageRole, SpanKind
from prompt.notice import render as no_change_notice

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
    "education": [{"institution": "UdeSA", "degree": "BSc", "start_date": "2014"}],
    "skills": {"technical": ["Java", "Postgres"], "tools": ["Jenkins"], "soft": []},
}

NEW_SUMMARY = "Backend engineer with eight years on distributed systems, Postgres and Kubernetes."
APPROVE_ALL = {"parts": [{"id": "summary", "ok": True}, {"id": "skills:technical", "ok": True}]}
PROPOSAL = {
    "reply": REPLY,
    "warnings": [WARNING],
    "summary": NEW_SUMMARY,
    "skills": {"technical": ["Java", "Postgres", "Kubernetes"]},
}
ADVICE = {
    "reply": REPLY,
    "warnings": [],
    "recommendations": [
        {"id": 7, "section": "summary", "entry": "", "what": "Name Kubernetes in the summary"},
        {"id": 7, "section": "work_experience", "entry": "Backend Engineer at Acme", "what": "Say what ran on Kubernetes"},
    ],
}


def context_for(message: str = ASK_FOR_GO, history=None, **overrides) -> TurnContext:
    values = dict(
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
        turns_left=9,
        resume_version_id="version-1",
    )
    values.update(overrides)
    return TurnContext(**values)


class EditTurnTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_one_answer_edits_the_resume_and_the_trace_says_how(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, REPLY)
        self.assertEqual(outcome.warnings, [WARNING])
        self.assertEqual(outcome.resume["summary"], NEW_SUMMARY)
        self.assertEqual(
            outcome.resume["skills"]["technical"], ["Java", "Postgres", "Kubernetes"]
        )
        self.assertEqual(
            [(change["section"], change["kind"]) for change in outcome.changes],
            [("summary", "rewrite"), ("skills", "replace")],
        )
        self.assertEqual(outcome.spans[0].name, INTENT_SPAN)
        self.assertEqual(outcome.spans[0].step, 0)
        self.assertEqual(
            [(span.kind, span.name) for span in outcome.spans[1:]],
            [(SpanKind.LLM, "model"), (SpanKind.LLM, "model"), (SpanKind.TOOL, APPLY_SPAN)],
        )
        self.assertEqual(outcome.spans[-1].output, "OK")

    async def test_each_agent_only_ever_holds_its_own_schema(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual([bind["tools"] for bind in model.binds], [["EditProposal"], ["Verdict"]])

    async def test_the_resume_comes_from_the_guard_not_from_the_model(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": '{"summary": "I am a CEO"}', "summary": "Kubernetes first, then Postgres."}, "1"),
            tool_call("Verdict", {"parts": [{"id": "summary", "ok": True}]}, "2"),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.resume["summary"], "Kubernetes first, then Postgres.")
        self.assertEqual(outcome.resume["personal_info"], RESUME["personal_info"])
        self.assertEqual(outcome.resume["education"], RESUME["education"])
        self.assertEqual(
            [entry["company"] for entry in outcome.resume["work_experience"]],
            ["Globex", "Acme"],
        )

    async def test_the_model_never_sees_personal_info(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        await run_turn(context_for(), model=model, intent_model=intending())

        system_prompt = model.prompts[0][0].content
        self.assertNotIn("Ada Lovelace", system_prompt)
        self.assertNotIn("ada@example.com", system_prompt)
        self.assertIn("Kubernetes", system_prompt)

    async def test_the_edit_turn_reads_the_recommendations_of_the_previous_one(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))
        context = context_for(
            message="Do the second one",
            recommendations=[{"id": 2, "section": "summary", "entry": "", "what": "Name Kubernetes"}],
        )

        await run_turn(context, model=model, intent_model=intending())

        self.assertIn("2. [summary] Name Kubernetes", model.prompts[0][0].content)

    async def test_a_model_that_breaks_the_tool_use_protocol_is_retried(self) -> None:
        model = breaking(bedrock_error(), 1, tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, REPLY)
        self.assertEqual(len(model.attempts), 3)

    async def test_a_model_that_keeps_breaking_fails_the_turn(self) -> None:
        reset_settings()
        with patch.dict("os.environ", {"AGENT_MODEL_RETRIES": "1"}, clear=False):
            model = breaking(bedrock_error(), 9, tool_call("EditProposal", PROPOSAL, "1"))

            with self.assertRaises(ClientError):
                await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(len(model.attempts), 2)

    async def test_an_error_bedrock_does_not_own_is_never_retried(self) -> None:
        model = breaking(RuntimeError("the socket died"), 1, tool_call("EditProposal", PROPOSAL, "1"))

        with self.assertRaises(RuntimeError):
            await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(len(model.attempts), 1)

    async def test_an_answer_that_came_as_text_is_unwrapped(self) -> None:
        model = scripted(
            AIMessage(content=json.dumps(PROPOSAL, ensure_ascii=False)),
            AIMessage(content=json.dumps(APPROVE_ALL)),
        )

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.reply, REPLY)
        self.assertEqual(outcome.resume["summary"], NEW_SUMMARY)

    async def test_what_the_candidate_confirmed_comes_back_in_the_outcome_and_backs_the_edit(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": REPLY, "confirmed": [CONFIRMED_SPRING_BOOT], "skills": {"technical": ["Java", "Postgres", "Spring Boot"]}}, "1"),
            tool_call("Verdict", {"parts": [{"id": "skills:technical", "ok": True}]}, "2"),
        )

        outcome = await run_turn(context_for(message=YES), model=model, intent_model=intending())

        self.assertEqual(outcome.confirmed, [CONFIRMED_SPRING_BOOT])
        self.assertEqual(outcome.resume["skills"]["technical"], ["Java", "Postgres", "Spring Boot"])
        self.assertEqual(outcome.spans[-1].output, "OK")

    async def test_the_judge_reads_what_was_asked_and_what_the_candidate_answered(self) -> None:
        model = scripted(
            tool_call("EditProposal", {"reply": REPLY, "confirmed": [CONFIRMED_SPRING_BOOT], "skills": {"technical": ["Java", "Postgres", "Spring Boot"]}}, "1"),
            tool_call("Verdict", {"parts": [{"id": "skills:technical", "ok": True}]}, "2"),
        )

        await run_turn(context_for(message=YES), model=model, intent_model=intending())

        judge_prompt = model.prompts[1][0].content
        self.assertIn(f"CV Tailor asked: {GREETING}", judge_prompt)
        self.assertIn(f"The candidate answered: {YES}", judge_prompt)
        self.assertIn(f"- {CONFIRMED_SPRING_BOOT}", judge_prompt)

    async def test_the_editor_reads_the_candidates_earlier_words_and_the_gaps(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        await run_turn(context_for(statements=[CONFIRMED_SPRING_BOOT]), model=model, intent_model=intending())

        system_prompt = model.prompts[0][0].content
        self.assertIn(f"- {CONFIRMED_SPRING_BOOT}", system_prompt)
        self.assertNotIn(f"- {ASK_FOR_GO}", system_prompt)
        self.assertIn("neither the resume nor the candidate has mentioned: PostgreSQL", system_prompt)
        self.assertNotIn("mentioned: PostgreSQL, Go", system_prompt)

    async def test_a_turn_that_applied_nothing_says_so(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": REPLY}, "1"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.changes, [])
        self.assertIn(no_change_notice("es"), outcome.warnings)

    async def test_a_turn_that_applied_something_does_not_say_it_applied_nothing(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertNotIn(no_change_notice("es"), outcome.warnings)


class AdviseTurnTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        patcher = patch.dict("os.environ", {"LLM_PROVIDER": "ollama"}, clear=False)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(reset_settings)

    async def test_a_question_cannot_change_the_resume(self) -> None:
        model = scripted(tool_call("Advice", ADVICE, "1"))

        outcome = await run_turn(
            context_for(message=ASK_FOR_ADVICE),
            model=model,
            intent_model=intending(Intent.ADVISE),
        )

        self.assertEqual(outcome.intent, Intent.ADVISE)
        self.assertEqual(model.binds[0]["tools"], ["Advice"])
        self.assertEqual(outcome.changes, [])
        self.assertEqual(outcome.resume, RESUME)
        self.assertNotIn(no_change_notice("es"), outcome.warnings)

    async def test_the_recommendations_come_back_numbered_by_the_code(self) -> None:
        model = scripted(tool_call("Advice", ADVICE, "1"))

        outcome = await run_turn(
            context_for(message=ASK_FOR_ADVICE),
            model=model,
            intent_model=intending(Intent.ADVISE),
        )

        self.assertEqual([r["id"] for r in outcome.recommendations], [1, 2])
        self.assertEqual(outcome.recommendations[1]["entry"], "Backend Engineer at Acme")
        self.assertTrue(outcome.reply.startswith(REPLY))
        self.assertTrue(outcome.reply.endswith("\n\n1. Name Kubernetes in the summary\n2. Say what ran on Kubernetes"))

    async def test_what_the_candidate_told_the_advisor_is_confirmed_too(self) -> None:
        model = scripted(tool_call("Advice", {**ADVICE, "confirmed": [CONFIRMED_SPRING_BOOT]}, "1"))

        outcome = await run_turn(
            context_for(message=ASK_FOR_ADVICE),
            model=model,
            intent_model=intending(Intent.ADVISE),
        )

        self.assertEqual(outcome.confirmed, [CONFIRMED_SPRING_BOOT])
        self.assertEqual(outcome.changes, [])

    async def test_an_edit_turn_recommends_through_its_reply_only(self) -> None:
        model = scripted(tool_call("EditProposal", PROPOSAL, "1"), tool_call("Verdict", APPROVE_ALL, "2"))

        outcome = await run_turn(context_for(), model=model, intent_model=intending())

        self.assertEqual(outcome.recommendations, [])


class TurnMessagesTest(unittest.TestCase):

    def test_the_last_user_message_is_not_repeated(self) -> None:
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

    def test_the_opening_greeting_is_not_sent_as_the_first_message(self) -> None:
        messages = turn_messages(context_for(message=GO_AHEAD))

        self.assertEqual([message.type for message in messages], ["human"])
        self.assertEqual(messages[0].content, GO_AHEAD)

    def test_a_window_that_opens_on_an_assistant_reply_still_starts_on_a_user_turn(self) -> None:
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

        self.assertEqual([message.type for message in messages], ["human", "ai", "human"])
        self.assertEqual(messages[0].content, ASK_FOR_GO)
