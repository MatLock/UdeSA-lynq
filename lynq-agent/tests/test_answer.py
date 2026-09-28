from __future__ import annotations

import unittest

from langchain_core.messages import AIMessage

from tests.fixtures.spanish import SHORT_REPLY

from agent.answer import text_of, unwrap
from agent.schemas import Advice, EditProposal


class UnwrapTest(unittest.TestCase):

    def test_it_reads_a_fenced_json_object(self) -> None:
        answer = unwrap('```json\n{"reply": "listo", "warnings": ["no Go"]}\n```', EditProposal)

        self.assertEqual(answer.reply, "listo")
        self.assertEqual(answer.warnings, ["no Go"])

    def test_it_reads_a_json_object_surrounded_by_chatter(self) -> None:
        answer = unwrap('Here you go: {"reply": "listo"} hope it helps', Advice)

        self.assertEqual(answer.reply, "listo")

    def test_plain_prose_becomes_the_reply(self) -> None:
        answer = unwrap(SHORT_REPLY, EditProposal)

        self.assertEqual(answer.reply, SHORT_REPLY)
        self.assertEqual(answer.warnings, [])
        self.assertFalse(answer.edits_anything())

    def test_a_json_that_does_not_fit_the_schema_is_kept_as_prose(self) -> None:
        answer = unwrap('{"reply": "listo", "warnings": "not a list"}', Advice)

        self.assertEqual(answer.reply, '{"reply": "listo", "warnings": "not a list"}')

    def test_the_edits_travel_with_the_reply(self) -> None:
        answer = unwrap(
            '{"reply": "listo", "summary": "Backend engineer.", '
            '"entries": [{"company": "Acme", "position": "Dev", "description": "Built it."}], '
            '"skills": {"technical": ["Java"]}}',
            EditProposal,
        )

        self.assertTrue(answer.edits_anything())
        self.assertEqual(answer.entries[0].company, "Acme")
        self.assertEqual(answer.skills.buckets(), {"technical": ["Java"]})


class TextOfTest(unittest.TestCase):

    def test_it_reads_a_string_content(self) -> None:
        self.assertEqual(text_of(AIMessage(content="listo")), "listo")

    def test_it_reads_the_text_parts_of_a_block_message(self) -> None:
        self.assertEqual(
            text_of(AIMessage(content=[{"type": "text", "text": "listo"}])), "listo"
        )

    def test_nothing_does_not_explode(self) -> None:
        self.assertEqual(text_of(None), "")
