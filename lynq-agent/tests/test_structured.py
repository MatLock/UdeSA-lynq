from __future__ import annotations

import unittest

from langchain_core.messages import AIMessage, HumanMessage

from tests.support import bedrock_error, breaking, scripted, tool_call

from agent.schemas import Advice, EditProposal
from agent.structured import ask


class AskTest(unittest.IsolatedAsyncioTestCase):

    async def test_the_schema_is_the_only_tool_and_its_call_is_the_answer(self) -> None:
        model = scripted(tool_call("EditProposal", {"reply": "done", "summary": "Better."}, "1"))

        answer = await ask(model, EditProposal, [HumanMessage("go")], callbacks=[], retries=0)

        self.assertIsInstance(answer, EditProposal)
        self.assertEqual(answer.summary, "Better.")
        self.assertEqual(model.binds[0]["tools"], ["EditProposal"])
        self.assertEqual(model.binds[0]["tool_choice"], "any")

    async def test_a_text_answer_is_unwrapped_into_the_schema(self) -> None:
        model = scripted(AIMessage(content='{"reply": "done", "recommendations": [{"what": "x"}]}'))

        answer = await ask(model, Advice, [HumanMessage("go")], callbacks=[], retries=0)

        self.assertEqual(answer.reply, "done")
        self.assertEqual(answer.recommendations[0].what, "x")

    async def test_a_call_to_some_other_tool_leaves_an_empty_answer_not_an_error(self) -> None:
        model = scripted(tool_call("TurnAnswer", {"reply": "done"}, "1"))

        answer = await ask(model, Advice, [HumanMessage("go")], callbacks=[], retries=0)

        self.assertEqual(answer.reply, "")

    async def test_a_bedrock_error_the_service_owns_is_retried(self) -> None:
        model = breaking(bedrock_error(), 1, tool_call("Advice", {"reply": "done"}, "1"))

        answer = await ask(model, Advice, [HumanMessage("go")], callbacks=[], retries=1)

        self.assertEqual(answer.reply, "done")
        self.assertEqual(len(model.attempts), 2)

    async def test_the_retries_are_bounded(self) -> None:
        model = breaking(bedrock_error(), 5, tool_call("Advice", {"reply": "done"}, "1"))

        with self.assertRaises(Exception):
            await ask(model, Advice, [HumanMessage("go")], callbacks=[], retries=2)

        self.assertEqual(len(model.attempts), 3)

    async def test_an_error_that_is_not_bedrocks_is_not_retried(self) -> None:
        model = breaking(RuntimeError("socket"), 1, tool_call("Advice", {"reply": "done"}, "1"))

        with self.assertRaises(RuntimeError):
            await ask(model, Advice, [HumanMessage("go")], callbacks=[], retries=3)

        self.assertEqual(len(model.attempts), 1)
