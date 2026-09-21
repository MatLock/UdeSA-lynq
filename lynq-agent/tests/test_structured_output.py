from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage

from tests.support import scripted, tool_call
from tests.test_react_loop import JOB, RESUME, context_for

from agent.answer import TurnAnswer
from agent.context import build_turn_state, use_turn_state
from agent.graph import build_agent, recursion_limit
from config import get_settings, reset_settings
from llm.factory import build_model
from prompt.resume_tailor import render

LIVE = "AGENT_LIVE_LLM"


def live_llm_enabled() -> bool:
    return os.getenv(LIVE, "").strip().lower() == "true"


def bedrock_enabled() -> bool:
    return live_llm_enabled() and os.getenv("LLM_PROVIDER", "").strip().lower() == "bedrock"


def live_system_prompt(context) -> str:
    provider = "bedrock" if get_settings().llm_provider == "bedrock" else "ollama"
    return render(
        provider,
        job=context.job_snapshot,
        resume={key: value for key, value in RESUME.items() if key != "personal_info"},
        language=context.language,
        resume_language=context.resume_language,
        max_steps=context.max_steps,
        turns_left=context.turns_left,
    )


class ToolChoiceTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        self.addCleanup(reset_settings)

    async def test_the_agent_forces_a_tool_call_and_turn_answer_is_one_of_them(self) -> None:
        model = scripted(tool_call("TurnAnswer", {"reply": "listo"}, "1"))

        agent = build_agent("rules", model)
        await agent.ainvoke({"messages": [("user", "go ahead")]})

        binding = model.binds[0]
        self.assertEqual(binding["tool_choice"], "any")
        self.assertEqual(
            sorted(binding["tools"]), ["TurnAnswer", "apply_edit", "find_evidence"]
        )

    async def test_the_tool_call_becomes_the_structured_response(self) -> None:
        model = scripted(
            tool_call("TurnAnswer", {"reply": "listo", "warnings": ["no Go"]}, "1")
        )

        agent = build_agent("rules", model)
        result = await agent.ainvoke({"messages": [("user", "go ahead")]})

        answer = result["structured_response"]
        self.assertIsInstance(answer, TurnAnswer)
        self.assertEqual(answer.reply, "listo")
        self.assertEqual(answer.warnings, ["no Go"])


@unittest.skipUnless(
    bedrock_enabled(),
    f"{LIVE}=true with LLM_PROVIDER=bedrock is what verifies tool_choice on Nova Pro; "
    "Ollama is known to break the tool call format and the loop falls back to the JSON",
)
class BedrockToolChoiceTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        self.addCleanup(reset_settings)

    async def test_a_real_turn_closes_with_the_turn_answer_tool_call(self) -> None:
        context = context_for()
        state = build_turn_state(context)
        agent = build_agent(live_system_prompt(context), build_model())

        with use_turn_state(state):
            result = await agent.ainvoke(
                {"messages": [("user", context.message)]},
                config={"recursion_limit": recursion_limit(context.max_steps)},
            )

        answers = [
            call
            for message in result["messages"]
            if isinstance(message, AIMessage)
            for call in message.tool_calls or []
            if call["name"] == "TurnAnswer"
        ]
        self.assertTrue(
            answers,
            f"{get_settings().llm_model} did not honour tool_choice='any': the turn "
            "never closed with a TurnAnswer tool call. The fallback of the plan "
            "applies: drop response_format, ask for the JSON in the prompt and "
            "validate it with Pydantic plus one retry",
        )
        self.assertIsInstance(result["structured_response"], TurnAnswer)


@unittest.skipUnless(
    live_llm_enabled(), f"{LIVE}=true is what runs a turn against the real model"
)
class LiveTurnTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_settings()
        self.addCleanup(reset_settings)

    async def test_a_real_turn_answers_the_candidate_without_inventing(self) -> None:
        from agent.graph import run_turn

        outcome = await run_turn(context_for())

        self.assertTrue(outcome.reply.strip())
        self.assertNotIn("Go", outcome.resume["skills"]["technical"])
        self.assertEqual(outcome.resume["personal_info"], RESUME["personal_info"])
        self.assertTrue(outcome.spans)
