from __future__ import annotations

import unittest
import uuid
from decimal import Decimal

from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, LLMResult

from tests.support import base_resume  # noqa: F401

from agent.callbacks import MAX_SPAN_TEXT, TraceCollector, truncate
from db.models import SpanKind


def _llm_result(prompt_tokens: int, completion_tokens: int) -> LLMResult:
    message = AIMessage(
        content="listo",
        usage_metadata={
            "input_tokens": prompt_tokens,
            "output_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        },
    )
    return LLMResult(generations=[[ChatGeneration(message=message)]])


class TraceCollectorTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.collector = TraceCollector(Decimal("0.8"), Decimal("3.2"))

    async def test_an_llm_span_carries_the_reported_tokens_and_its_cost(self):
        run_id = uuid.uuid4()
        await self.collector.on_chat_model_start({}, [], run_id=run_id)
        await self.collector.on_llm_end(_llm_result(1000, 200), run_id=run_id)

        span = self.collector.spans[0]
        self.assertEqual(span.kind, SpanKind.LLM)
        self.assertEqual(span.prompt_tokens, 1000)
        self.assertEqual(span.completion_tokens, 200)
        self.assertEqual(span.cost_usd, Decimal("0.00144"))
        self.assertIsNotNone(span.latency_ms)

    async def test_a_tool_span_pairs_its_input_with_its_output(self):
        run_id = uuid.uuid4()
        await self.collector.on_tool_start(
            {"name": "apply_edit"}, '{"section": "summary"}', run_id=run_id
        )
        await self.collector.on_tool_end("OK", run_id=run_id)

        span = self.collector.spans[0]
        self.assertEqual(span.kind, SpanKind.TOOL)
        self.assertEqual(span.name, "apply_edit")
        self.assertIn("summary", span.input)
        self.assertEqual(span.output, "OK")

    async def test_a_failing_tool_keeps_its_error_on_the_span(self):
        run_id = uuid.uuid4()
        await self.collector.on_tool_start({"name": "find_evidence"}, "x", run_id=run_id)
        await self.collector.on_tool_error(RuntimeError("boom"), run_id=run_id)

        self.assertEqual(self.collector.spans[0].error, "boom")

    async def test_a_failing_llm_leaves_an_error_span(self):
        run_id = uuid.uuid4()
        await self.collector.on_chat_model_start({}, [], run_id=run_id)
        await self.collector.on_llm_error(RuntimeError("bedrock down"), run_id=run_id)

        self.assertEqual(self.collector.spans[0].kind, SpanKind.ERROR)

    def test_the_soft_cap_leaves_a_limit_span_so_the_trace_explains_itself(self):
        self.collector.record_limit("tool_steps=12, max_steps=12")

        span = self.collector.spans[0]
        self.assertEqual(span.kind, SpanKind.LIMIT)
        self.assertEqual(span.name, "max_steps")
        self.assertIn("max_steps=12", span.output)

    def test_a_resume_sized_payload_is_truncated_before_it_reaches_the_row(self):
        truncated = truncate("x" * (MAX_SPAN_TEXT + 500))

        self.assertLess(len(truncated), MAX_SPAN_TEXT + 100)
        self.assertIn("truncados", truncated)

    def test_steps_are_numbered_in_the_order_they_happened(self):
        self.collector.record_limit("a")
        self.collector.record_limit("b")

        self.assertEqual([s.step for s in self.collector.spans], [1, 2])


if __name__ == "__main__":
    unittest.main()
