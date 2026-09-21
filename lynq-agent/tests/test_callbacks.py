from __future__ import annotations

import json
import unittest
from uuid import uuid4

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, LLMResult

from agent.callbacks import TraceCollector
from agent.context import TurnContext, build_turn_state
from db.models import SpanKind

PROMPT_REFERENCE = "resume_tailor/bedrock@0123456789ab"


def state_for():
    context = TurnContext(
        conversation_id="conversation-1",
        run_token="token-1",
        language="es",
        resume_language="en",
        job_snapshot={"extractedSkills": []},
        base_resume={"summary": "Backend engineer."},
        current_resume={"summary": "Backend engineer."},
        history=[],
        message="go ahead",
        max_steps=12,
        turns_left=9,
        resume_version_id="version-1",
    )
    return build_turn_state(context)


def answer(message: AIMessage) -> LLMResult:
    return LLMResult(generations=[[ChatGeneration(message=message)]])


class TraceCollectorTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        self.state = state_for()
        self.collector = TraceCollector(self.state, PROMPT_REFERENCE, "version-1")

    async def test_the_model_span_carries_tokens_latency_and_the_tool_calls(self) -> None:
        run = uuid4()
        await self.collector.on_chat_model_start(
            {}, [[SystemMessage("rules"), HumanMessage("go ahead")]], run_id=run
        )
        await self.collector.on_llm_end(
            answer(
                AIMessage(
                    content="",
                    tool_calls=[
                        {"name": "find_evidence", "args": {"claim": "Go"}, "id": "1"}
                    ],
                    usage_metadata={
                        "input_tokens": 1200,
                        "output_tokens": 40,
                        "total_tokens": 1240,
                        "input_token_details": {"cache_read": 900},
                    },
                )
            ),
            run_id=run,
        )

        span = self.state.spans[0]
        self.assertEqual(span.kind, SpanKind.LLM)
        self.assertEqual(span.step, 1)
        self.assertEqual(span.prompt_tokens, 1200)
        self.assertEqual(span.completion_tokens, 40)
        self.assertEqual(span.cached_prompt_tokens, 900)
        self.assertIsNotNone(span.latency_ms)
        self.assertIn("find_evidence", span.output)

    async def test_the_model_span_never_carries_the_system_prompt(self) -> None:
        run = uuid4()
        await self.collector.on_chat_model_start(
            {}, [[SystemMessage("the whole resume and the whole posting"), HumanMessage("go")]],
            run_id=run,
        )
        await self.collector.on_llm_end(answer(AIMessage(content="done")), run_id=run)

        payload = json.loads(self.state.spans[0].input)
        self.assertEqual([message["role"] for message in payload["messages"]], ["human"])
        self.assertEqual(payload["resume_version_id"], "version-1")
        self.assertEqual(payload["prompt"], PROMPT_REFERENCE)

    async def test_a_tool_span_keeps_its_name_and_its_answer(self) -> None:
        run = uuid4()
        await self.collector.on_tool_start(
            {"name": "apply_edit"}, '{"section": "summary"}', run_id=run
        )
        await self.collector.on_tool_end(
            ToolMessage(content="OK", tool_call_id="1"), run_id=run
        )

        span = self.state.spans[0]
        self.assertEqual(span.kind, SpanKind.TOOL)
        self.assertEqual(span.name, "apply_edit")
        self.assertEqual(span.output, "OK")

    async def test_a_tool_that_answers_an_empty_list_is_not_serialized_as_its_message(self) -> None:
        run = uuid4()
        await self.collector.on_tool_start({"name": "find_evidence"}, "{}", run_id=run)
        await self.collector.on_tool_end(
            ToolMessage(content=[], tool_call_id="1"), run_id=run
        )

        self.assertEqual(self.state.spans[0].output, "[]")

    async def test_an_error_of_the_model_becomes_an_error_span(self) -> None:
        run = uuid4()
        await self.collector.on_llm_start({}, ["prompt"], run_id=run)
        await self.collector.on_llm_error(RuntimeError("bedrock is down"), run_id=run)

        span = self.state.spans[0]
        self.assertEqual(span.kind, SpanKind.ERROR)
        self.assertEqual(span.error, "bedrock is down")

    async def test_an_error_of_a_tool_becomes_an_error_span(self) -> None:
        run = uuid4()
        await self.collector.on_tool_start({"name": "apply_edit"}, "{}", run_id=run)
        await self.collector.on_tool_error(RuntimeError("boom"), run_id=run)

        self.assertEqual(self.state.spans[0].kind, SpanKind.ERROR)

    async def test_every_step_is_counted_once(self) -> None:
        for _ in range(3):
            run = uuid4()
            await self.collector.on_chat_model_start({}, [[HumanMessage("go")]], run_id=run)
            await self.collector.on_llm_end(answer(AIMessage(content="ok")), run_id=run)

        self.assertEqual(self.state.steps, 3)
        self.assertEqual([span.step for span in self.state.spans], [1, 2, 3])

    async def test_a_generation_without_a_message_leaves_an_empty_output(self) -> None:
        from langchain_core.outputs import Generation

        run = uuid4()
        await self.collector.on_llm_start({}, ["prompt"], run_id=run)
        await self.collector.on_llm_end(
            LLMResult(generations=[[Generation(text="plain")]]), run_id=run
        )

        self.assertEqual(self.state.spans[0].output, "")

    async def test_an_end_without_its_start_is_ignored(self) -> None:
        await self.collector.on_llm_end(answer(AIMessage(content="ok")), run_id=uuid4())
        await self.collector.on_tool_end("OK", run_id=uuid4())

        self.assertEqual(self.state.spans, [])
