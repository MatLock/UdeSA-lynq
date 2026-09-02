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

    def test_ollama_template_contains_input_without_chat_tokens(self) -> None:
        prompt = render_upskilling_prompt(LLMProvider.OLLAMA, input_json=_INPUT_JSON)

        self.assertIn("Backend role", prompt)
        self.assertIn("Junior dev", prompt)
        # Plain text, no model-specific chat tokens: Ollama applies the
        # chat template of whichever model is configured.
        self.assertNotIn("<|", prompt)

    def test_bedrock_template_contains_input(self) -> None:
        prompt = render_upskilling_prompt(LLMProvider.BEDROCK, input_json=_INPUT_JSON)

        self.assertIn("Backend role", prompt)
        self.assertIn("Junior dev", prompt)
        # bedrock.jinja is plain text too: no chat special tokens.
        self.assertNotIn("<|", prompt)

    def test_both_templates_declare_the_same_output_schema(self) -> None:
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            prompt = render_upskilling_prompt(provider, input_json=_INPUT_JSON)
            self.assertIn('"outcome"', prompt)
            self.assertIn('"reasons"', prompt)
            self.assertIn('"search_queries"', prompt)

    def test_language_code_is_resolved_to_name_in_prompt(self) -> None:
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            prompt = render_upskilling_prompt(
                provider, input_json=_INPUT_JSON, language="es"
            )
            self.assertIn("Spanish", prompt)

    def test_language_defaults_to_english_when_omitted(self) -> None:
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            prompt = render_upskilling_prompt(provider, input_json=_INPUT_JSON)
            self.assertIn("English", prompt)

    def test_provider_selects_distinct_templates(self) -> None:
        ollama = render_upskilling_prompt(LLMProvider.OLLAMA, input_json=_INPUT_JSON)
        bedrock = render_upskilling_prompt(LLMProvider.BEDROCK, input_json=_INPUT_JSON)

        self.assertNotEqual(ollama, bedrock)

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
