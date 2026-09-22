from __future__ import annotations

import unittest

from prompt.greeting import DEFAULT_LANGUAGE, languages, render

JOB = {"id": "job-1", "title": "Senior Backend Engineer", "company": "Acme"}


class GreetingTest(unittest.TestCase):

    def test_there_is_one_template_per_language_the_ui_speaks(self) -> None:
        self.assertEqual(languages(), {"en", "es"})

    def test_it_greets_in_english_naming_the_job_and_the_company(self) -> None:
        self.assertEqual(
            render(JOB, "en"),
            "I read the posting for Senior Backend Engineer at Acme. Should I put "
            "together a version of your resume aimed at it?",
        )

    def test_it_greets_in_spanish_when_that_is_the_locale(self) -> None:
        greeting = render(JOB, "es")

        self.assertIn("Senior Backend Engineer", greeting)
        self.assertIn("Acme", greeting)
        self.assertNotIn("resume", greeting)

    def test_a_locale_with_a_region_still_finds_its_template(self) -> None:
        self.assertEqual(render(JOB, "es-AR"), render(JOB, "es"))

    def test_a_language_with_no_template_falls_back_to_english(self) -> None:
        self.assertEqual(render(JOB, "pt"), render(JOB, DEFAULT_LANGUAGE))

    def test_a_posting_with_no_company_reads_naturally(self) -> None:
        self.assertEqual(
            render({"title": "Backend Engineer"}, "en"),
            "I read the posting for Backend Engineer. Should I put together a "
            "version of your resume aimed at it?",
        )

    def test_a_posting_with_no_title_still_greets(self) -> None:
        self.assertIn("this job", render({"company": "Acme"}, "en"))
        self.assertIn("este puesto", render({"company": "Acme"}, "es"))
