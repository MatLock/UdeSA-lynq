from __future__ import annotations

import json
import unittest
from decimal import Decimal
from typing import Any

from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from tests.support import JOB, base_resume

from agent.context import TurnContext
from agent.editor import ResumeEditor
from agent.graph import run_turn
from client import LynqMlClient
from db.models import SpanKind
from llm import ModelHandle


class ScriptedChatModel(BaseChatModel):

    replies: list[AIMessage] = []
    calls: int = 0
    stops_on_limit: bool = False

    def bind_tools(self, tools, **kwargs):
        return self

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def _generate(
        self,
        messages,
        stop=None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        last = messages[-1] if messages else None
        was_told_to_stop = self.stops_on_limit and isinstance(
            getattr(last, "content", None), str
        ) and last.content.startswith("STEP_LIMIT_REACHED")

        index = len(self.replies) - 1 if was_told_to_stop else self.calls
        index = min(index, len(self.replies) - 1)
        self.calls += 1
        return ChatResult(generations=[ChatGeneration(message=self.replies[index])])


def _usage(input_tokens: int, output_tokens: int) -> dict:
    return {
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": input_tokens + output_tokens,
    }


def _tool_call(name: str, args: dict, call_id: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": args, "id": call_id}],
        usage_metadata=_usage(1000, 40),
    )


def _final_answer(reply: str, warnings: list[str] | None = None) -> AIMessage:
    return _tool_call(
        "TurnAnswer", {"reply": reply, "warnings": warnings or []}, "answer"
    )


def _final_answer_as_text(reply: str) -> AIMessage:
    return AIMessage(
        content=json.dumps({"reply": reply, "warnings": []}),
        usage_metadata=_usage(900, 20),
    )


class ReactLoopTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.base = base_resume()
        self.context = TurnContext(
            conversation_id="conv-1",
            job_snapshot=JOB,
            base_resume=self.base,
            editor=ResumeEditor(self.base, base_resume()),
            max_steps=12,
            language="es",
            input_price_per_1m=Decimal("0.8"),
            output_price_per_1m=Decimal("3.2"),
        )

    async def test_the_loop_runs_the_tools_and_leaves_a_trace(self):
        outcome = await self._run(
            [
                _tool_call("find_evidence", {"claim": "Jenkins"}, "c1"),
                _tool_call(
                    "apply_edit",
                    {
                        "section": "skills",
                        "op": "add",
                        "payload": {"bucket": "tools", "name": "Jenkins"},
                    },
                    "c2",
                ),
                _final_answer("Rescaté Jenkins de tu experiencia."),
            ]
        )

        self.assertEqual(outcome.reply, "Rescaté Jenkins de tu experiencia.")
        self.assertIn("Jenkins", outcome.resume["skills"]["tools"])
        self.assertEqual(outcome.changes[0]["section"], "skills")

        tool_spans = [s for s in outcome.spans if s.kind == SpanKind.TOOL]
        llm_spans = [s for s in outcome.spans if s.kind == SpanKind.LLM]
        self.assertEqual(
            [s.name for s in tool_spans], ["find_evidence", "apply_edit"]
        )
        self.assertEqual(tool_spans[1].output, "OK")
        self.assertGreaterEqual(len(llm_spans), 2)
        self.assertTrue(all(s.prompt_tokens for s in llm_spans))
        self.assertTrue(all(s.cost_usd is not None for s in llm_spans))

    async def test_a_refused_edit_comes_back_to_the_model_as_an_observation(self):
        outcome = await self._run(
            [
                _tool_call(
                    "apply_edit",
                    {
                        "section": "skills",
                        "op": "add",
                        "payload": {"bucket": "technical", "name": "Go"},
                    },
                    "c1",
                ),
                _final_answer("No encontré Go en tu CV."),
            ]
        )

        rejection = [s for s in outcome.spans if s.name == "apply_edit"][0]
        self.assertTrue(rejection.output.startswith("REJECTED"))
        self.assertIsNone(outcome.resume)
        self.assertTrue(any("Go" in w for w in outcome.warnings))

    async def test_a_model_that_pastes_the_json_instead_of_calling_the_tool(self):
        outcome = await self._run(
            [
                _tool_call("find_evidence", {"claim": "Jenkins"}, "c1"),
                _final_answer_as_text("Reordené tu experiencia."),
            ]
        )

        self.assertEqual(outcome.reply, "Reordené tu experiencia.")
        self.assertNotIn("{", outcome.reply)

    async def test_a_model_that_answers_in_plain_prose_is_taken_as_is(self):
        outcome = await self._run(
            [
                AIMessage(
                    content="Listo, dejé tu CV apuntado al aviso.",
                    usage_metadata=_usage(800, 20),
                )
            ]
        )

        self.assertEqual(outcome.reply, "Listo, dejé tu CV apuntado al aviso.")

    async def test_the_soft_cap_ends_the_turn_with_a_partial_answer(self):
        replies = [
            _tool_call("find_evidence", {"claim": f"skill-{index}"}, f"c{index}")
            for index in range(6)
        ]
        replies.append(_final_answer("Corté acá."))

        outcome = await self._run(replies, max_steps=2, stops_on_limit=True)

        limit_spans = [s for s in outcome.spans if s.kind == SpanKind.LIMIT]
        self.assertEqual(len(limit_spans), 1)
        self.assertEqual(limit_spans[0].name, "max_steps")
        self.assertEqual(self.context.tool_steps, 2)
        self.assertEqual(outcome.reply, "Corté acá.")

    async def _run(
        self, replies: list[AIMessage], max_steps: int = 12, stops_on_limit=False
    ):
        self.context.max_steps = max_steps
        model = ScriptedChatModel(replies=replies, stops_on_limit=stops_on_limit)
        return await run_turn(
            context=self.context,
            handle=ModelHandle(
                model=model, provider="bedrock", model_id="amazon.nova-pro-v1:0"
            ),
            ml_client=LynqMlClient("http://ml", "system", 1.0),
            request_uuid="uuid-1",
            message="Adaptá mi CV",
            history=[],
            turns_left=5,
        )


if __name__ == "__main__":
    unittest.main()
