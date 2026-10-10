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

    def test_the_gaps_are_asked_about_in_english(self) -> None:
        self.assertEqual(
            render(JOB, "en", ["Spring Boot", "Kafka", "Go"]),
            "I read the posting for Senior Backend Engineer at Acme. Should I put "
            "together a version of your resume aimed at it? The posting also asks for "
            "Spring Boot, Kafka and Go, which your resume does not mention: if you have "
            "experience with any of them, tell me which, in which job and what you did, "
            "and I will bring it in.",
        )

    def test_one_gap_reads_in_the_singular(self) -> None:
        greeting = render(JOB, "en", ["Spring Boot"])

        self.assertIn("asks for Spring Boot, which your resume does not mention: if you have experience with it, tell me in which job", greeting)

    def test_the_gaps_are_asked_about_in_spanish(self) -> None:
        self.assertIn("Spring Boot y Kafka", render(JOB, "es", ["Spring Boot", "Kafka"]))
        self.assertIn("Spring Boot,", render(JOB, "es", ["Spring Boot"]))

    def test_no_more_than_a_few_gaps_are_greeted(self) -> None:
        greeting = render(JOB, "en", ["A", "B", "C", "D", "E"])

        self.assertIn("A, B, C and D, which", greeting)
        self.assertNotIn(", E", greeting)

    def test_a_posting_with_no_title_still_greets(self) -> None:
        self.assertIn("this job", render({"company": "Acme"}, "en"))
        self.assertIn("este puesto", render({"company": "Acme"}, "es"))
