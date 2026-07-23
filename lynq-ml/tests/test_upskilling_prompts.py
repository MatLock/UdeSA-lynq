"""Tests for ``render_upskilling_prompt``."""

from __future__ import annotations

import json
import unittest

from jinja2 import UndefinedError

from llm_client import LLMProvider
from prompt import upskilling_suggestion as prompts_module
from prompt.upskilling_suggestion import render_upskilling_prompt

_INPUT_JSON = json.dumps(
    {
        "job": {"description": "Backend role", "skills": ["Java", "Kubernetes"]},
        "candidate": {"description": "Junior dev", "skills": ["Java"]},
    },
    indent=2,
)


class RenderUpskillingPromptTests(unittest.TestCase):
    """The renderer injects the input JSON into the provider template."""

    def test_ollama_template_contains_input_and_chat_tokens(self) -> None:
        prompt = render_upskilling_prompt(LLMProvider.OLLAMA, input_json=_INPUT_JSON)

        self.assertIn("Backend role", prompt)
        self.assertIn("Junior dev", prompt)
        # ollama.jinja embeds llama chat special tokens (sent with raw=True).
        self.assertIn("<|begin_of_text|>", prompt)

    def test_openai_template_contains_input(self) -> None:
        prompt = render_upskilling_prompt(LLMProvider.OPENAI, input_json=_INPUT_JSON)

        self.assertIn("Backend role", prompt)
        self.assertIn("Junior dev", prompt)
        # openai.jinja is plain text: no llama chat tokens.
        self.assertNotIn("<|begin_of_text|>", prompt)

    def test_both_templates_declare_the_same_output_schema(self) -> None:
        for provider in (LLMProvider.OLLAMA, LLMProvider.OPENAI):
            prompt = render_upskilling_prompt(provider, input_json=_INPUT_JSON)
            self.assertIn('"outcome"', prompt)
            self.assertIn('"search_queries"', prompt)

    def test_provider_selects_distinct_templates(self) -> None:
        ollama = render_upskilling_prompt(LLMProvider.OLLAMA, input_json=_INPUT_JSON)
        openai = render_upskilling_prompt(LLMProvider.OPENAI, input_json=_INPUT_JSON)

        self.assertNotEqual(ollama, openai)

    def test_missing_variable_raises_under_strict_undefined(self) -> None:
        # The environment uses StrictUndefined, so rendering without input_json
        # must raise rather than silently emit an empty string.
        template = prompts_module._env.get_template(
            "upskilling_suggestion/ollama.jinja"
        )

        with self.assertRaises(UndefinedError):
            template.render()


if __name__ == "__main__":
    unittest.main()
