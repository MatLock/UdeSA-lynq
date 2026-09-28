from __future__ import annotations

import unittest

from tests.fixtures.spanish import ACCENTED_SKILL, NORMALIZED_SKILL

from agent.lexical import find_match, normalize, same_skill, words


class NormalizeTest(unittest.TestCase):

    def test_it_folds_case_accents_and_symbols(self) -> None:
        self.assertEqual(normalize(ACCENTED_SKILL), NORMALIZED_SKILL)
        self.assertEqual(normalize("Node.js"), "nodejs")
        self.assertEqual(normalize("Spring Boot"), "springboot")

    def test_it_keeps_what_tells_c_sharp_and_c_plus_plus_from_c(self) -> None:
        self.assertEqual(normalize("C#"), "c#")
        self.assertEqual(normalize("C++"), "c++")
        self.assertNotEqual(normalize("C++"), normalize("C"))


class SameSkillTest(unittest.TestCase):

    def test_the_alias_table_decides_what_is_the_same_technology(self) -> None:
        self.assertTrue(same_skill("Postgres", "PostgreSQL"))
        self.assertTrue(same_skill("k8s", "Kubernetes"))
        self.assertTrue(same_skill("Amazon Web Services", "AWS"))
        self.assertTrue(same_skill("React.js", "React"))

    def test_a_prefix_is_not_the_same_technology(self) -> None:
        self.assertFalse(same_skill("JavaScript", "Java"))
        self.assertFalse(same_skill("React", "React Native"))
        self.assertFalse(same_skill("Spring", "Spring Boot"))

    def test_nothing_matches_nothing(self) -> None:
        self.assertFalse(same_skill("", ""))
        self.assertFalse(same_skill("Java", ""))


class FindMatchTest(unittest.TestCase):

    def test_it_returns_the_wording_of_the_text(self) -> None:
        self.assertEqual(find_match("Migrated the payments services to k8s.", "Kubernetes"), "k8s")
        self.assertEqual(find_match("Postgres, Redis and Kafka", "PostgreSQL"), "Postgres")

    def test_a_multi_word_claim_matches_a_window_of_words(self) -> None:
        self.assertEqual(find_match("Built APIs on Spring Boot 3.", "spring boot"), "Spring Boot")
        self.assertEqual(find_match("Built APIs on SpringBoot.", "Spring Boot"), "SpringBoot")

    def test_a_word_that_merely_starts_with_the_claim_is_not_a_match(self) -> None:
        self.assertIsNone(find_match("JavaScript and HTML.", "Java"))
        self.assertIsNone(find_match("Django for the backend.", "Go"))
        self.assertIsNone(find_match("React Native apps.", "React Native Web"))

    def test_a_short_claim_only_matches_its_exact_spelling(self) -> None:
        self.assertEqual(find_match("Services written in Go and Java.", "Go"), "Go")
        self.assertIsNone(find_match("Ready to go live.", "Go"))

    def test_symbols_around_a_word_are_not_part_of_it(self) -> None:
        self.assertEqual(find_match("Worked with (Kubernetes), mostly.", "Kubernetes"), "Kubernetes")
        self.assertEqual(find_match("Languages: C#, C++.", "C#"), "C#")

    def test_an_empty_claim_matches_nothing(self) -> None:
        self.assertIsNone(find_match("Kubernetes", ""))
        self.assertIsNone(find_match("", "Kubernetes"))


class WordsTest(unittest.TestCase):

    def test_it_splits_on_spaces_and_strips_punctuation(self) -> None:
        self.assertEqual(words("Java, Go; and C++!"), ["Java", "Go", "and", "C++"])
