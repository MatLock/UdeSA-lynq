from __future__ import annotations

import unittest

from langchain_core.messages import AIMessage

from agent.answer import TurnAnswer, from_result, unwrap


class UnwrapTest(unittest.TestCase):

    def test_it_reads_a_fenced_json_object(self) -> None:
        answer = unwrap('```json\n{"reply": "listo", "warnings": ["no Go"]}\n```')

        self.assertEqual(answer.reply, "listo")
        self.assertEqual(answer.warnings, ["no Go"])

    def test_it_reads_a_json_object_surrounded_by_chatter(self) -> None:
        answer = unwrap('Here you go: {"reply": "listo"} hope it helps')

        self.assertEqual(answer.reply, "listo")

    def test_plain_prose_becomes_the_reply(self) -> None:
        answer = unwrap("Reordené tu experiencia.")

        self.assertEqual(answer.reply, "Reordené tu experiencia.")
        self.assertEqual(answer.warnings, [])

    def test_a_json_that_is_not_a_turn_answer_is_kept_as_prose(self) -> None:
        answer = unwrap('{"section": "summary"}')

        self.assertEqual(answer.reply, '{"section": "summary"}')


class FromResultTest(unittest.TestCase):

    def test_it_takes_the_structured_response_when_the_tool_call_arrived(self) -> None:
        answer = from_result(
            {"structured_response": TurnAnswer(reply="listo", warnings=["no Go"])}
        )

        self.assertEqual(answer.reply, "listo")

    def test_it_validates_a_structured_response_that_came_as_a_dict(self) -> None:
        answer = from_result({"structured_response": {"reply": "listo"}})

        self.assertEqual(answer.reply, "listo")

    def test_a_structured_response_that_does_not_validate_falls_back(self) -> None:
        answer = from_result(
            {
                "structured_response": {"warnings": "not a list"},
                "messages": [AIMessage(content="listo")],
            }
        )

        self.assertEqual(answer.reply, "listo")

    def test_it_falls_back_to_the_last_message_when_there_is_none(self) -> None:
        answer = from_result(
            {"messages": [AIMessage(content='{"reply": "listo", "warnings": []}')]}
        )

        self.assertEqual(answer.reply, "listo")

    def test_it_reads_the_text_parts_of_a_block_message(self) -> None:
        answer = from_result(
            {"messages": [AIMessage(content=[{"type": "text", "text": "listo"}])]}
        )

        self.assertEqual(answer.reply, "listo")

    def test_an_empty_result_does_not_explode(self) -> None:
        self.assertEqual(from_result({}).reply, "")
