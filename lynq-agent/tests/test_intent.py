from __future__ import annotations

import json
import unittest

from langchain_core.messages import AIMessage

from tests.fixtures.spanish import ASK_FOR_ADVICE, ASK_FOR_GO, GO_AHEAD, GREETING
from tests.support import bedrock_error, breaking, intending, scripted
from tests.test_turn import context_for

from agent.context import INTENT_SPAN, Intent
from agent.intent import EXCHANGE_MESSAGES, classify, read, recent_exchange
from db.models import MessageRole, SpanKind
from prompt.intent import reference, render

PROVIDERS = ("bedrock", "ollama")


class ReadTest(unittest.TestCase):

    def test_the_two_words_are_read_as_they_are(self) -> None:
        self.assertEqual(read("edit"), Intent.EDIT)
        self.assertEqual(read("advise"), Intent.ADVISE)

    def test_a_word_wrapped_in_noise_is_still_read(self) -> None:
        self.assertEqual(read("`advise`"), Intent.ADVISE)
        self.assertEqual(read("Edit."), Intent.EDIT)
        self.assertEqual(read("The intent is: advise\n"), Intent.ADVISE)

    def test_an_answer_that_says_neither_is_not_read(self) -> None:
        self.assertIsNone(read(""))
        self.assertIsNone(read("I am not sure what they want"))
        self.assertIsNone(read("editorial"))


class ExchangeTest(unittest.TestCase):

    def test_the_message_of_this_turn_is_not_repeated_in_the_exchange(self) -> None:
        context = context_for(
            message=GO_AHEAD,
            history=[
                (MessageRole.ASSISTANT, GREETING),
                (MessageRole.USER, GO_AHEAD),
            ],
        )

        self.assertEqual(recent_exchange(context), [(MessageRole.ASSISTANT, GREETING)])

    def test_the_exchange_opens_on_what_the_agent_said(self) -> None:
        context = context_for(
            message=GO_AHEAD,
            history=[
                (MessageRole.USER, ASK_FOR_GO),
                (MessageRole.ASSISTANT, GREETING),
            ],
        )

        self.assertEqual(
            [role for role, _ in recent_exchange(context)], [MessageRole.ASSISTANT]
        )

    def test_only_the_last_messages_travel(self) -> None:
        history = [
            (MessageRole.ASSISTANT, f"turn {number}") for number in range(10)
        ]

        context = context_for(message=GO_AHEAD, history=history)

        self.assertEqual(len(recent_exchange(context)), EXCHANGE_MESSAGES)


class ClassifyTest(unittest.IsolatedAsyncioTestCase):

    async def test_a_request_is_read_as_an_edit(self) -> None:
        context = context_for(message=ASK_FOR_GO)

        intent = await classify(context, "ollama", intending(Intent.EDIT))

        self.assertEqual(intent, Intent.EDIT)

    async def test_a_question_is_read_as_advice(self) -> None:
        context = context_for(message=ASK_FOR_ADVICE)

        intent = await classify(context, "ollama", intending(Intent.ADVISE))

        self.assertEqual(intent, Intent.ADVISE)

    async def test_the_decision_leaves_a_span_of_its_own_before_the_loop(self) -> None:
        context = context_for(message=ASK_FOR_ADVICE)

        await classify(context, "ollama", intending(Intent.ADVISE))

        span = context.spans[0]
        self.assertEqual(span.name, INTENT_SPAN)
        self.assertEqual(span.kind, SpanKind.LLM)
        self.assertEqual(span.step, 0)
        self.assertEqual(span.output, Intent.ADVISE)
        self.assertEqual(
            json.loads(span.input),
            {"message": ASK_FOR_ADVICE, "prompt": reference("ollama"), "model": "qwen2.5:7b"},
        )

    async def test_the_message_of_the_candidate_is_never_quoted_back(self) -> None:
        context = context_for(message=ASK_FOR_ADVICE)
        model = intending(Intent.ADVISE)

        await classify(context, "ollama", model)

        prompt = model.prompts[0][0].content
        self.assertIn(ASK_FOR_ADVICE, prompt)
        self.assertNotIn("Ada Lovelace", prompt)

    async def test_an_answer_that_comes_in_blocks_is_still_read(self) -> None:
        context = context_for(message=ASK_FOR_ADVICE)
        blocks = AIMessage(content=[{"type": "text", "text": Intent.ADVISE}])

        intent = await classify(context, "ollama", scripted(blocks))

        self.assertEqual(intent, Intent.ADVISE)

    async def test_an_answer_that_says_neither_falls_back_to_editing(self) -> None:
        context = context_for(message=ASK_FOR_GO)

        intent = await classify(
            context, "ollama", scripted(AIMessage(content="no idea"))
        )

        self.assertEqual(intent, Intent.EDIT)
        self.assertEqual(context.spans[0].output, Intent.EDIT)

    async def test_a_model_that_breaks_never_takes_the_turn_down(self) -> None:
        context = context_for(message=ASK_FOR_GO)
        model = breaking(bedrock_error(), 1, AIMessage(content=Intent.ADVISE))

        intent = await classify(context, "ollama", model)

        self.assertEqual(intent, Intent.EDIT)
        self.assertEqual(context.spans[0].kind, SpanKind.ERROR)
        self.assertIn("ModelErrorException", context.spans[0].error)


class PromptTest(unittest.TestCase):

    def render(self, provider: str) -> str:
        return render(
            provider,
            [(MessageRole.ASSISTANT, GREETING)],
            ASK_FOR_ADVICE,
        )

    def test_both_providers_have_their_own_template(self) -> None:
        self.assertNotEqual(self.render("bedrock"), self.render("ollama"))

    def test_it_asks_for_one_of_the_two_words(self) -> None:
        for provider in PROVIDERS:
            prompt = self.render(provider)

            self.assertIn("Answer with one word and nothing else", prompt)
            self.assertIn(f"`{Intent.EDIT}`", prompt)
            self.assertIn(f"`{Intent.ADVISE}`", prompt)

    def test_the_ollama_variant_spells_the_format_out(self) -> None:
        self.assertIn("Your whole answer is one lowercase word", self.render("ollama"))
        self.assertNotIn(
            "Your whole answer is one lowercase word", self.render("bedrock")
        )

    def test_naming_a_change_is_not_asking_for_one(self) -> None:
        for provider in PROVIDERS:
            self.assertIn(
                "naming a change is not asking for it", self.render(provider)
            )

    def test_a_yes_to_a_skill_asked_about_is_an_edit_and_a_no_is_not(self) -> None:
        for provider in PROVIDERS:
            prompt = self.render(provider)

            self.assertIn("A yes to that — alone, or with where they used the skill", prompt)
            self.assertIn("is `edit`", prompt)
            self.assertIn("A no,", prompt)

    def test_the_exchange_and_the_message_travel_in_their_own_blocks(self) -> None:
        for provider in PROVIDERS:
            prompt = self.render(provider)

            self.assertIn(f"assistant: {GREETING}", prompt)
            self.assertIn(f"<message>\n{ASK_FOR_ADVICE}\n</message>", prompt)

    def test_it_names_the_family_the_provider_and_the_hash(self) -> None:
        family, _, digest = reference("bedrock").partition("@")

        self.assertEqual(family, "intent/bedrock")
        self.assertEqual(len(digest), 12)

    def test_each_provider_has_its_own_hash(self) -> None:
        self.assertNotEqual(reference("bedrock"), reference("ollama"))


if __name__ == "__main__":
    unittest.main()
