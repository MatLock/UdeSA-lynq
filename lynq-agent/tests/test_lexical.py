from __future__ import annotations

import unittest

from agent.lexical import claim_words, find_match, matches, normalize


class NormalizeTest(unittest.TestCase):

    def test_it_drops_accents_case_and_symbols(self) -> None:
        self.assertEqual(normalize("Node.js"), "nodejs")
        self.assertEqual(normalize("C#"), "c")
        self.assertEqual(normalize("Programación"), "programacion")


class MatchesTest(unittest.TestCase):

    def test_long_claims_match_by_prefix_in_both_directions(self) -> None:
        self.assertTrue(matches("postgres", "postgresql"))
        self.assertTrue(matches("postgresql", "postgres"))

    def test_short_claims_need_the_exact_word(self) -> None:
        self.assertTrue(matches("go", "go"))
        self.assertFalse(matches("golang", "go"))
        self.assertFalse(matches("aws", "awsome"))

    def test_nothing_matches_an_empty_side(self) -> None:
        self.assertFalse(matches("", "go"))
        self.assertFalse(matches("go", ""))


class FindMatchTest(unittest.TestCase):

    def test_it_returns_the_wording_of_the_resume(self) -> None:
        self.assertEqual(find_match("Postgres", claim_words("PostgreSQL")), "Postgres")

    def test_it_finds_a_claim_inside_prose(self) -> None:
        self.assertEqual(
            find_match("Ran services on Kubernetes clusters.", claim_words("kubernetes")),
            "Kubernetes",
        )

    def test_it_finds_a_claim_of_several_words(self) -> None:
        self.assertEqual(
            find_match("I know machine learning basics", claim_words("Machine Learning")),
            "machine learning",
        )

    def test_it_returns_nothing_when_the_resume_does_not_say_it(self) -> None:
        self.assertIsNone(find_match("Backend engineer.", claim_words("Rust")))

    def test_an_empty_claim_matches_nothing(self) -> None:
        self.assertIsNone(find_match("Backend engineer.", claim_words("")))
