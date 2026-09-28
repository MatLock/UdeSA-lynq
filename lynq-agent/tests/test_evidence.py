from __future__ import annotations

import unittest

from agent.evidence import backing_for, hits_for, searchable, skill_names

RESUME = {
    "personal_info": {"full_name": "Ada Lovelace", "headline": "Kubernetes wizard"},
    "summary": "Backend engineer on distributed systems and Postgres.",
    "work_experience": [
        {
            "company": "Acme",
            "position": "Backend Engineer",
            "description": "Migrated the payments services to k8s.",
            "technologies": ["Java"],
        }
    ],
    "skills": {"technical": ["Postgres"], "tools": ["Docker"], "soft": []},
}


class SearchableTest(unittest.TestCase):

    def test_personal_info_is_never_searched(self) -> None:
        paths = [path for path, _ in searchable(RESUME)]

        self.assertNotIn("personal_info.headline", paths)
        self.assertIn("summary", paths)
        self.assertIn("work_experience[0].description", paths)


class HitsTest(unittest.TestCase):

    def test_a_hit_says_where_and_with_which_wording(self) -> None:
        self.assertEqual(
            hits_for(RESUME, "Kubernetes"),
            [{"path": "work_experience[0].description", "matched": "k8s"}],
        )

    def test_a_claim_the_resume_does_not_carry_has_no_hit(self) -> None:
        self.assertEqual(hits_for(RESUME, "Go"), [])

    def test_the_headline_in_personal_info_is_not_evidence(self) -> None:
        self.assertEqual(hits_for({"personal_info": {"headline": "Go expert"}}, "Go"), [])


class BackingTest(unittest.TestCase):

    def test_skills_and_technologies_are_backing(self) -> None:
        self.assertEqual(skill_names(RESUME), ["Postgres", "Docker", "Java"])
        self.assertEqual(backing_for(RESUME, "PostgreSQL"), "Postgres")
        self.assertEqual(backing_for(RESUME, "java"), "Java")

    def test_the_prose_backs_a_skill_the_list_forgot(self) -> None:
        self.assertEqual(backing_for(RESUME, "Kubernetes"), "k8s")

    def test_the_backing_is_the_wording_of_the_resume_not_of_the_claim(self) -> None:
        self.assertEqual(backing_for(RESUME, "k8s"), "k8s")
        self.assertNotEqual(backing_for(RESUME, "PostgreSQL"), "PostgreSQL")

    def test_nothing_backs_what_is_not_there(self) -> None:
        self.assertIsNone(backing_for(RESUME, "Rust"))
        self.assertIsNone(backing_for(RESUME, "JavaScript"))
