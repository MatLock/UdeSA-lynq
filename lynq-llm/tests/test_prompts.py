"""Tests for ``render_key_extractor_prompt``."""

from __future__ import annotations

import unittest
from pathlib import Path

from jinja2 import UndefinedError

from llm_client import LLMProvider
from prompt import skill_enhance as prompts_module
from prompt.resume_extractor import render_resume_extractor_prompt
from prompt.skill_enhance import render_key_extractor_prompt


class RenderKeyExtractorPromptTests(unittest.TestCase):
    """The renderer injects the job data into the provider template."""

    def test_ollama_template_contains_job_data_without_chat_tokens(self) -> None:
        prompt = render_key_extractor_prompt(
            LLMProvider.OLLAMA,
            job_title="Data Engineer",
            work_type="REMOTE",
            job_description="Build ETL pipelines.",
        )

        self.assertIn("Data Engineer", prompt)
        self.assertIn("REMOTE", prompt)
        self.assertIn("Build ETL pipelines.", prompt)
        # Plain text, no model-specific chat tokens: Ollama applies the
        # chat template of whichever model is configured.
        self.assertNotIn("<|", prompt)

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

    def test_every_template_requests_similarity_tags(self) -> None:
        # Both providers must ask for the "similarity_tags" list, always in English, so the
        # job posting and the resume end up sharing one matching vocabulary.
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            with self.subTest(provider=provider):
                prompt = render_key_extractor_prompt(
                    provider,
                    job_title="Backend Engineer",
                    work_type="REMOTE",
                    job_description="Event driven services.",
                )

                self.assertIn('"similarity_tags"', prompt)
                self.assertIn("English", prompt)
                self.assertIn("Asynchronous Messaging", prompt)

    def test_templates_cover_non_technical_domains(self) -> None:
        # Generalization is not a software-only idea: the templates carry
        # examples from other fields so a non-tech posting is tagged too.
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            with self.subTest(provider=provider):
                prompt = render_key_extractor_prompt(
                    provider,
                    job_title="Enfermero de Guardia",
                    work_type="IN_OFFICE",
                    job_description="Triage y atención de pacientes.",
                )

                self.assertIn("Emergency Patient Care", prompt)
                self.assertIn("Accounting Software", prompt)

    def test_missing_variable_raises_under_strict_undefined(self) -> None:
        # The environment uses StrictUndefined, so rendering the template
        # without all variables must raise rather than emit empty strings.
        template = prompts_module._env.get_template("job_post_skill_extraction/ollama.jinja")

        with self.assertRaises(UndefinedError):
            template.render(job_title="only title")


class ResumeExtractorSkillRulesTests(unittest.TestCase):
    """The resume parser is told how to read skills that resumes write in many shapes."""

    def test_templates_extract_the_items_under_a_heading_not_the_heading(self) -> None:
        # A resume that writes "Cloud / DevOps: Terraform, Azure" once parsed into
        # the skill "Cloud / DevOps": the labels matched no posting at all.
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            with self.subTest(provider=provider):
                prompt = render_resume_extractor_prompt(provider, resume_text="CV")

                self.assertIn("never the heading", prompt)
                self.assertIn("Cloud / DevOps", prompt)

    def test_templates_count_domain_techniques_as_technical_skills(self) -> None:
        # A food-quality resume once parsed with no technical skills at all.
        for provider in (LLMProvider.OLLAMA, LLMProvider.BEDROCK):
            with self.subTest(provider=provider):
                prompt = render_resume_extractor_prompt(provider, resume_text="CV")

                self.assertIn("HACCP", prompt)
                self.assertIn("ISO 9001", prompt)


class TemplatesAreModelAgnosticTests(unittest.TestCase):
    """No template may embed a model's chat special tokens.

    The clients send the rendered prompt as a plain user turn and let the
    provider apply the chat template of the configured model, so switching
    models (Qwen, Llama, Mistral, a Bedrock one) never means editing a prompt.
    Hard-coding tokens like ``<|im_start|>`` or ``<|begin_of_text|>`` would tie
    every template back to one model, which is what this guards against.
    """

    # tests/ -> parents[1] is the module root.
    _PROMPTS_DIR = Path(__file__).resolve().parents[1] / "resources" / "prompts"

    def test_no_template_contains_chat_special_tokens(self) -> None:
        templates = sorted(self._PROMPTS_DIR.glob("*/*.jinja"))
        self.assertTrue(templates, "no prompt templates were found")

        for template in templates:
            with self.subTest(template=template.name, feature=template.parent.name):
                self.assertNotIn("<|", template.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()