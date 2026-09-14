from __future__ import annotations

import unittest

from tests.support import base_resume

from agent.evidence import evidenced_vocabulary, find_evidence, is_evidenced
from agent.skill_aliases import are_same_technology, normalize


class FindEvidenceTest(unittest.TestCase):

    def setUp(self):
        self.resume = base_resume()

    def test_a_skill_named_only_in_the_prose_is_found_with_its_path(self):
        self.assertEqual(
            find_evidence(self.resume, "Jenkins"),
            ["$.work_experience[0].description"],
        )

    def test_a_skill_in_the_skills_array_is_found(self):
        self.assertIn("$.skills.technical[1]", find_evidence(self.resume, "Postgres"))

    def test_an_alias_finds_what_the_candidate_wrote_under_another_name(self):
        paths = find_evidence(self.resume, "PostgreSQL")

        self.assertIn("$.skills.technical[1]", paths)

    def test_kubernetes_is_found_through_its_abbreviation_in_the_prose(self):
        self.assertEqual(
            find_evidence(self.resume, "Kubernetes"),
            ["$.work_experience[0].description"],
        )

    def test_something_absent_returns_nothing(self):
        self.assertEqual(find_evidence(self.resume, "Rust"), [])

    def test_the_candidate_identity_is_never_searched(self):
        self.assertEqual(find_evidence(self.resume, "Ada Lovelace"), [])
        self.assertEqual(find_evidence(self.resume, "ada@example.com"), [])

    def test_a_substring_of_a_longer_word_does_not_count_as_evidence(self):
        resume = {"summary": "Trabajé con Javascript en el front"}

        self.assertEqual(find_evidence(resume, "Java"), [])

    def test_accents_and_case_do_not_hide_evidence(self):
        resume = {"summary": "Servicios de Facturación electrónica"}

        self.assertTrue(find_evidence(resume, "facturacion"))

    def test_the_evidenced_vocabulary_gathers_skills_and_technologies(self):
        vocabulary = evidenced_vocabulary(self.resume)

        self.assertIn("java", vocabulary)
        self.assertIn("docker", vocabulary)
        self.assertIn("postgresql", vocabulary)
        self.assertNotIn("jenkins", vocabulary)

    def test_is_evidenced_covers_both_the_array_and_the_prose(self):
        self.assertTrue(is_evidenced(self.resume, "Java"))
        self.assertTrue(is_evidenced(self.resume, "Jenkins"))
        self.assertFalse(is_evidenced(self.resume, "Go"))


class SkillAliasTest(unittest.TestCase):

    def test_the_curated_table_links_names_of_the_same_technology(self):
        self.assertTrue(are_same_technology("Postgres", "PostgreSQL"))
        self.assertTrue(are_same_technology("JS", "JavaScript"))
        self.assertTrue(are_same_technology("k8s", "Kubernetes"))

    def test_related_but_different_technologies_are_not_linked(self):
        self.assertFalse(are_same_technology("React", "React Native"))
        self.assertFalse(are_same_technology("Java", "JavaScript"))
        self.assertFalse(are_same_technology("MySQL", "PostgreSQL"))

    def test_normalization_folds_case_accents_and_spacing(self):
        self.assertEqual(normalize("  PostgreSQL  "), "postgresql")
        self.assertEqual(normalize("Facturación"), "facturacion")
        self.assertEqual(normalize("Power  BI"), "power bi")


if __name__ == "__main__":
    unittest.main()
