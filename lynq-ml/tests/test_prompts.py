"""Tests for ``render_key_extractor_prompt``."""

from __future__ import annotations

import unittest

from jinja2 import UndefinedError

from llm_client import LLMProvider
from prompt import skill_enhance as prompts_module
from prompt.skill_enhance import render_key_extractor_prompt


class RenderKeyExtractorPromptTests(unittest.TestCase):
    """The renderer injects the job data into the provider template."""

    def test_ollama_template_contains_job_data_and_chat_tokens(self) -> None:
        prompt = render_key_extractor_prompt(
            LLMProvider.OLLAMA,
            job_title="Data Engineer",
            work_type="REMOTE",
            job_description="Build ETL pipelines.",
        )

        self.assertIn("Data Engineer", prompt)
        self.assertIn("REMOTE", prompt)
        self.assertIn("Build ETL pipelines.", prompt)
        # ollama.jinja embeds llama chat special tokens (sent with raw=True).
        self.assertIn("<|begin_of_text|>", prompt)

    def test_bedrock_template_contains_job_data(self) -> None:
        prompt = render_key_extractor_prompt(
            LLMProvider.BEDROCK,
            job_title="Frontend Developer",
            work_type="IN_OFFICE",
            job_description="React and TypeScript.",
        )

        self.assertIn("Frontend Developer", prompt)
        self.assertIn("IN_OFFICE", prompt)
        self.assertIn("React and TypeScript.", prompt)

    def test_provider_selects_distinct_templates(self) -> None:
        kwargs = dict(
            job_title="X", work_type="REMOTE", job_description="Y"
        )
        ollama = render_key_extractor_prompt(LLMProvider.OLLAMA, **kwargs)
        bedrock = render_key_extractor_prompt(LLMProvider.BEDROCK, **kwargs)

        self.assertNotEqual(ollama, bedrock)

    def test_missing_variable_raises_under_strict_undefined(self) -> None:
        # The environment uses StrictUndefined, so rendering the template
        # without all variables must raise rather than emit empty strings.
        template = prompts_module._env.get_template("job_post_skill_extraction/ollama.jinja")

        with self.assertRaises(UndefinedError):
            template.render(job_title="only title")


if __name__ == "__main__":
    unittest.main()