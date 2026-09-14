from __future__ import annotations

import unittest
from decimal import Decimal
from unittest.mock import patch

from langchain_core.messages import AIMessage

from tests.support import JOB, base_resume

from agent import graph
from agent.context import TurnContext
from agent.editor import ResumeEditor
from agent.graph import AgentError, TurnAnswer, _answer_from, _history_messages, run_turn
from client import LynqMlClient
from llm import ModelHandle


def _context(max_steps: int = 12) -> TurnContext:
    base = base_resume()
    return TurnContext(
        conversation_id="conv-1",
        job_snapshot=JOB,
        base_resume=base,
        editor=ResumeEditor(base, base_resume()),
        max_steps=max_steps,
        language="es",
        input_price_per_1m=Decimal("0"),
        output_price_per_1m=Decimal("0"),
    )


class FakeAgent:

    def __init__(self, result, on_invoke=None) -> None:
        self.result = result
        self.config = None
        self.on_invoke = on_invoke

    async def ainvoke(self, state, config=None):
        self.config = config
        if self.on_invoke is not None:
            self.on_invoke()
        return self.result


class AnswerParsingTest(unittest.TestCase):

    def test_a_structured_answer_is_used_as_is(self):
        answer = _answer_from(
            {"structured_response": TurnAnswer(reply="hola", warnings=["x"])}
        )

        self.assertEqual(answer.reply, "hola")
        self.assertEqual(answer.warnings, ["x"])

    def test_a_dict_shaped_answer_is_accepted(self):
        answer = _answer_from(
            {"structured_response": {"reply": "hola", "warnings": ["x"]}}
        )

        self.assertEqual(answer.reply, "hola")

    def test_it_falls_back_to_the_last_message_when_there_is_no_structure(self):
        answer = _answer_from({"messages": [AIMessage(content="texto plano")]})

        self.assertEqual(answer.reply, "texto plano")
        self.assertEqual(answer.warnings, [])

    def test_a_run_with_no_reply_at_all_is_an_error(self):
        with self.assertRaises(AgentError):
            _answer_from({"messages": []})

    def test_history_roles_are_mapped_for_the_model(self):
        mapped = _history_messages(
            [
                {"role": "assistant", "content": "hola"},
                {"role": "user", "content": "dale"},
                {"role": "system", "content": ""},
            ]
        )

        self.assertEqual(mapped, [("assistant", "hola"), ("user", "dale")])


class RunTurnTest(unittest.IsolatedAsyncioTestCase):

    async def _run(self, agent, context=None, turns_left=5):
        context = context or _context()
        with patch.object(graph, "create_agent", return_value=agent):
            return await run_turn(
                context=context,
                handle=ModelHandle(
                    model=object(), provider="ollama", model_id="qwen2.5:7b"
                ),
                ml_client=LynqMlClient("http://ml", "system", 1.0),
                request_uuid="uuid-1",
                message="Dale",
                history=[],
                turns_left=turns_left,
            )

    async def test_the_resume_comes_from_the_edits_not_from_the_model(self):
        context = _context()
        agent = FakeAgent(
            {"structured_response": TurnAnswer(reply="listo", warnings=[])},
            on_invoke=lambda: context.editor.apply(
                "skills", "add", {"bucket": "tools", "name": "Jenkins"}
            ),
        )

        outcome = await self._run(agent, context)

        self.assertIn("Jenkins", outcome.resume["skills"]["tools"])
        self.assertEqual(outcome.changes[0]["section"], "skills")

    async def test_a_turn_without_edits_reports_no_resume(self):
        outcome = await self._run(
            FakeAgent({"structured_response": TurnAnswer(reply="nada que cambiar")})
        )

        self.assertIsNone(outcome.resume)
        self.assertEqual(outcome.changes, [])

    async def test_rejections_surface_as_warnings_for_the_candidate(self):
        context = _context()
        agent = FakeAgent(
            {"structured_response": TurnAnswer(reply="listo", warnings=[])},
            on_invoke=lambda: context.editor.apply(
                "skills", "add", {"bucket": "technical", "name": "Go"}
            ),
        )

        outcome = await self._run(agent, context)

        self.assertTrue(any("Go" in w for w in outcome.warnings))

    async def test_the_hard_cap_leaves_room_for_the_soft_cap_to_bite_first(self):
        context = _context(max_steps=6)
        agent = FakeAgent({"structured_response": TurnAnswer(reply="ok")})

        await self._run(agent, context)

        self.assertEqual(agent.config["recursion_limit"], 18)
        self.assertGreater(agent.config["recursion_limit"], 2 * context.max_steps)

    async def test_a_blowing_up_agent_is_wrapped_as_an_agent_error(self):
        class Exploding:
            async def ainvoke(self, state, config=None):
                raise RuntimeError("recursion limit reached")

        with self.assertRaises(AgentError):
            await self._run(Exploding())


if __name__ == "__main__":
    unittest.main()
