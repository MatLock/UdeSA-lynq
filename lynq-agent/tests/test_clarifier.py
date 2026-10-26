from __future__ import annotations

import json
import unittest

from langchain_core.messages import AIMessage

from tests.fixtures.spanish import AMBIGUOUS, CLARIFICATION, GREETING
from tests.support import bedrock_error, breaking, scripted
from tests.test_turn import context_for

from agent.clarifier import NOTHING_ASKED, clarify
from agent.context import CLARIFY_SPAN
from agent.state import build_turn_state
from db.models import MessageRole, SpanKind
from prompt.clarify import reference, render

PROVIDERS = ("bedrock", "ollama")
USAGE = {"input_tokens": 120, "output_tokens": 30, "total_tokens": 150}


def ambiguous_turn():
    context = context_for(message=AMBIGUOUS)
    return context, build_turn_state(context)


class ClarifyTest(unittest.IsolatedAsyncioTestCase):

    async def test_the_question_is_what_the_model_wrote(self) -> None:
        context, state = ambiguous_turn()

        question = await clarify(
            context, state, "ollama", scripted(AIMessage(content=f"  {CLARIFICATION}\n"))
        )

        self.assertEqual(question, CLARIFICATION)

    async def test_it_leaves_a_span_priced_with_the_intent_model(self) -> None:
        context, state = ambiguous_turn()

        await clarify(
            context,
            state,
            "ollama",
            scripted(AIMessage(content=CLARIFICATION, usage_metadata=USAGE)),
        )

        span = state.spans[-1]
        self.assertEqual(span.name, CLARIFY_SPAN)
        self.assertEqual(span.kind, SpanKind.LLM)
        self.assertEqual(span.output, CLARIFICATION)
        self.assertEqual(span.prompt_tokens, 120)
        self.assertIsNotNone(span.cost_usd)
        self.assertEqual(
            json.loads(span.input),
            {"message": AMBIGUOUS, "prompt": reference("ollama"), "model": "qwen2.5:7b"},
        )

    async def test_the_prompt_carries_the_message_and_not_the_resume(self) -> None:
        context, state = ambiguous_turn()
        model = scripted(AIMessage(content=CLARIFICATION))

        await clarify(context, state, "ollama", model)

        prompt = model.prompts[0][0].content
        self.assertIn(AMBIGUOUS, prompt)
        self.assertIn("Spanish (es)", prompt)
        self.assertNotIn("Ada Lovelace", prompt)

    async def test_an_empty_answer_fails_the_turn_with_an_error_span(self) -> None:
        context, state = ambiguous_turn()

        with self.assertRaises(ValueError):
            await clarify(context, state, "ollama", scripted(AIMessage(content="  ")))

        self.assertEqual(state.spans[-1].kind, SpanKind.ERROR)
        self.assertEqual(state.spans[-1].error, NOTHING_ASKED)

    async def test_a_model_that_breaks_leaves_an_error_span(self) -> None:
        context, state = ambiguous_turn()
        model = breaking(bedrock_error(), 1, AIMessage(content=CLARIFICATION))

        with self.assertRaises(Exception):
            await clarify(context, state, "ollama", model)

        self.assertEqual(state.spans[-1].name, CLARIFY_SPAN)
        self.assertEqual(state.spans[-1].kind, SpanKind.ERROR)


class PromptTest(unittest.TestCase):

    def render(self, provider: str) -> str:
        return render(provider, [(MessageRole.ASSISTANT, GREETING)], AMBIGUOUS, "es")

    def test_both_providers_have_their_own_template(self) -> None:
        self.assertNotEqual(self.render("bedrock"), self.render("ollama"))

    def test_it_asks_instead_of_guessing(self) -> None:
        for provider in PROVIDERS:
            prompt = " ".join(self.render(provider).split())

            self.assertIn("You do not guess, you change nothing", prompt)
            self.assertIn("ask which one they meant", prompt)
            self.assertIn("ask them to say it again in other words", prompt)
            self.assertIn("in Spanish (es)", prompt)

    def test_the_exchange_and_the_message_travel_in_their_own_blocks(self) -> None:
        for provider in PROVIDERS:
            prompt = self.render(provider)

            self.assertIn(f"assistant: {GREETING}", prompt)
            self.assertIn(f"<message>\n{AMBIGUOUS}\n</message>", prompt)

    def test_the_ollama_variant_spells_the_format_out(self) -> None:
        self.assertIn("Your whole answer is the question", self.render("ollama"))
        self.assertNotIn("Your whole answer is the question", self.render("bedrock"))

    def test_it_names_the_family_the_provider_and_the_hash(self) -> None:
        family, _, digest = reference("bedrock").partition("@")

        self.assertEqual(family, "clarify/bedrock")
        self.assertEqual(len(digest), 12)


if __name__ == "__main__":
    unittest.main()
