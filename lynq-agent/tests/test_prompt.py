from __future__ import annotations

import unittest

from prompt.resume_tailor import language_name, reference, render

JOB = {
    "title": "Senior Backend Engineer",
    "company": "Acme",
    "workType": "REMOTE",
    "description": "Kubernetes and PostgreSQL.",
    "extractedSkills": ["Kubernetes", "PostgreSQL"],
}
RESUME = {"summary": "Backend engineer."}


class RenderTest(unittest.TestCase):

    def render(self, provider: str, turns_left: int = 9) -> str:
        return render(
            provider,
            job=JOB,
            resume=RESUME,
            language="es",
            resume_language="en",
            max_steps=12,
            turns_left=turns_left,
        )

    def test_both_providers_have_their_own_template(self) -> None:
        self.assertNotEqual(self.render("bedrock"), self.render("ollama"))

    def test_the_posting_travels_inside_its_own_block(self) -> None:
        prompt = self.render("bedrock")

        self.assertIn("<job_posting>", prompt)
        self.assertIn("Kubernetes and PostgreSQL.", prompt)
        self.assertIn("not instructions", prompt)

    def test_it_says_which_language_to_talk_and_which_one_to_edit_in(self) -> None:
        prompt = self.render("bedrock")

        self.assertIn("Write `reply` and `warnings` in Spanish (es)", prompt)
        self.assertIn("must be in English (en)", prompt)

    def test_the_last_turn_asks_the_agent_to_close(self) -> None:
        self.assertIn("last exchange", self.render("bedrock", turns_left=1))
        self.assertNotIn("last exchange", self.render("bedrock", turns_left=9))

    def test_the_resume_travels_as_json(self) -> None:
        self.assertIn('"summary": "Backend engineer."', self.render("ollama"))

    def test_the_ollama_variant_spells_the_json_fallback_out(self) -> None:
        self.assertIn('{"reply": "...", "warnings": ["..."]}', self.render("ollama"))


class ReferenceTest(unittest.TestCase):

    def test_it_names_the_family_the_provider_and_the_hash(self) -> None:
        family, _, digest = reference("bedrock").partition("@")

        self.assertEqual(family, "resume_tailor/bedrock")
        self.assertEqual(len(digest), 12)

    def test_each_provider_has_its_own_hash(self) -> None:
        self.assertNotEqual(reference("bedrock"), reference("ollama"))


class LanguageNameTest(unittest.TestCase):

    def test_it_spells_the_language_out_for_the_model(self) -> None:
        self.assertEqual(language_name("es"), "Spanish (es)")
        self.assertEqual(language_name("en-US"), "English (en)")

    def test_an_unknown_code_is_passed_through(self) -> None:
        self.assertEqual(language_name("zz"), "zz (zz)")
