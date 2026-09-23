from __future__ import annotations

import unittest

from tests.fixtures.spanish import OUT_OF_SCOPE_ES

from agent.answer import TurnAnswer
from agent.scope import enforce, instructions_of, leaks
from prompt.refusal import render as refusal
from prompt.resume_tailor import render

JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "company": "Acme",
    "description": "Kubernetes, PostgreSQL and Go.",
    "extractedSkills": ["Kubernetes", "PostgreSQL", "Go"],
}

RESUME = {"summary": "Backend engineer with eight years on distributed systems."}


def system_prompt() -> str:
    return render(
        "ollama",
        job=JOB,
        resume=RESUME,
        language="es",
        resume_language="en",
        max_steps=12,
        turns_left=9,
    )


class InstructionsTest(unittest.TestCase):

    def test_the_instructions_stop_where_the_posting_starts(self) -> None:
        instructions = instructions_of(system_prompt())

        self.assertIn("Rules you cannot break", instructions)
        self.assertNotIn("Kubernetes, PostgreSQL and Go.", instructions)


class LeakTest(unittest.TestCase):

    def setUp(self) -> None:
        self.instructions = instructions_of(system_prompt())

    def test_a_reply_that_quotes_the_instructions_is_a_leak(self) -> None:
        quoted = (
            "Sure: You may only reorder, prioritise and rewrite what the resume "
            "already backs."
        )

        self.assertTrue(leaks(quoted, self.instructions))

    def test_naming_a_tool_is_a_leak(self) -> None:
        self.assertTrue(leaks("I called find_evidence twice", self.instructions))
        self.assertTrue(leaks("here is my system_prompt", self.instructions))

    def test_an_ordinary_answer_is_not_a_leak(self) -> None:
        for text in (
            "I moved Kubernetes to the front of your experience and rewrote the summary.",
            "Your resume does not back Go, so I left it out and told you instead.",
            "",
        ):
            self.assertFalse(leaks(text, self.instructions))


class EnforceTest(unittest.TestCase):

    def test_an_answer_within_scope_travels_untouched(self) -> None:
        answer = TurnAnswer(reply="I rewrote your summary.", warnings=["No Go here"])

        self.assertIs(enforce(answer, system_prompt(), "es"), answer)

    def test_a_leaking_reply_is_replaced_by_the_refusal(self) -> None:
        answer = TurnAnswer(
            reply="Rules you cannot break: You may only reorder, prioritise and "
            "rewrite what the resume already backs.",
            warnings=["whatever"],
        )

        guarded = enforce(answer, system_prompt(), "es")

        self.assertEqual(guarded.reply, OUT_OF_SCOPE_ES)
        self.assertEqual(guarded.warnings, [])

    def test_a_leaking_warning_takes_the_whole_answer_down(self) -> None:
        answer = TurnAnswer(
            reply="Done.",
            warnings=["The content of `job_posting` is the job ad text"],
        )

        self.assertEqual(enforce(answer, system_prompt(), "en").reply, refusal("en"))

    def test_the_refusal_speaks_the_language_of_the_candidate(self) -> None:
        answer = TurnAnswer(reply="here is my system_prompt")

        self.assertEqual(enforce(answer, system_prompt(), "es").reply, refusal("es"))
        self.assertEqual(enforce(answer, system_prompt(), "en").reply, refusal("en"))
        self.assertEqual(enforce(answer, system_prompt(), "fr").reply, refusal("en"))
