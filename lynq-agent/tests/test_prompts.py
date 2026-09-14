from __future__ import annotations

import unittest

from tests.support import JOB, base_resume

from prompt.resume_tailor import (
    render_greeting,
    render_system_prompt,
    without_personal_info,
)

PROVIDERS = ("ollama", "bedrock")


class GreetingTest(unittest.TestCase):

    def test_the_greeting_is_a_template_and_names_the_posting(self):
        greeting = render_greeting("es", title="Backend Engineer", company="Acme")

        self.assertIn("Backend Engineer", greeting)
        self.assertIn("Acme", greeting)

    def test_the_greeting_follows_the_ui_language(self):
        english = render_greeting("en", title="Backend Engineer", company="Acme")

        self.assertIn("resume", english)

    def test_a_posting_without_company_still_greets(self):
        greeting = render_greeting("es", title="Backend Engineer", company=None)

        self.assertIn("Backend Engineer", greeting)
        self.assertNotIn("None", greeting)

    def test_an_unknown_language_falls_back_instead_of_failing(self):
        self.assertIn("Backend", render_greeting("pt", title="Backend", company=None))


class SystemPromptTest(unittest.TestCase):

    def _render(self, provider: str, turns_left: int = 5) -> str:
        return render_system_prompt(
            provider,
            job=JOB,
            resume=base_resume(),
            language="es",
            turns_left=turns_left,
        )

    def test_both_provider_variants_render(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                self.assertTrue(self._render(provider).strip())

    def test_the_posting_reaches_the_prompt(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                prompt = self._render(provider)
                self.assertIn("Senior Backend Engineer", prompt)
                self.assertIn("Kubernetes", prompt)

    def test_the_candidate_identity_never_reaches_the_model(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                prompt = self._render(provider)
                self.assertNotIn("Ada Lovelace", prompt)
                self.assertNotIn("ada@example.com", prompt)
                self.assertNotIn("+54 11 5555 5555", prompt)

    def test_the_resume_content_does_reach_the_model(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                prompt = self._render(provider)
                self.assertIn("Globant", prompt)
                self.assertIn("Jenkins", prompt)

    def test_the_last_turn_asks_the_agent_to_close(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                self.assertIn("last turn", self._render(provider, turns_left=1))
                self.assertNotIn("last turn", self._render(provider, turns_left=5))

    def test_the_conversation_language_is_stated(self):
        for provider in PROVIDERS:
            with self.subTest(provider=provider):
                self.assertIn("es", self._render(provider))

    def test_stripping_personal_info_leaves_the_rest_untouched(self):
        stripped = without_personal_info(base_resume())

        self.assertNotIn("personal_info", stripped)
        self.assertEqual(len(stripped["work_experience"]), 2)

    def test_stripping_does_not_mutate_the_original(self):
        resume = base_resume()
        without_personal_info(resume)

        self.assertIn("personal_info", resume)


if __name__ == "__main__":
    unittest.main()
