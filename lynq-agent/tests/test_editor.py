from __future__ import annotations

import unittest

from tests.support import base_resume

from agent.editor import ResumeEditor


class ResumeEditorTest(unittest.TestCase):

    def setUp(self):
        self.base = base_resume()
        self.editor = ResumeEditor(self.base, base_resume())

    def test_personal_info_cannot_be_touched(self):
        result = self.editor.apply(
            "personal_info", "rewrite", {"full_name": "Otra Persona"}
        )

        self.assertFalse(result.accepted)
        self.assertIn("inmutable", result.message)
        self.assertEqual(
            self.editor.resume["personal_info"]["full_name"], "Ada Lovelace"
        )

    def test_a_skill_backed_only_by_the_prose_is_rescued(self):
        result = self.editor.apply(
            "skills", "add", {"bucket": "tools", "name": "Jenkins"}
        )

        self.assertTrue(result.accepted, result.message)
        self.assertIn("Jenkins", self.editor.resume["skills"]["tools"])
        self.assertEqual(
            result.evidence, ["$.work_experience[0].description"]
        )

    def test_a_skill_with_no_evidence_anywhere_is_refused(self):
        result = self.editor.apply(
            "skills", "add", {"bucket": "technical", "name": "Go"}
        )

        self.assertFalse(result.accepted)
        self.assertIn("no hay evidencia de Go", result.message)
        self.assertNotIn("Go", self.editor.resume["skills"]["technical"])

    def test_the_posting_spelling_of_an_owned_technology_is_allowed(self):
        result = self.editor.apply(
            "skills", "add", {"bucket": "technical", "name": "PostgreSQL"}
        )

        self.assertTrue(result.accepted, result.message)
        technical = self.editor.resume["skills"]["technical"]
        self.assertIn("PostgreSQL", technical)
        self.assertIn("Postgres", technical)

    def test_an_alias_outside_the_curated_table_is_refused(self):
        editor = ResumeEditor(self.base, base_resume())
        editor.apply("skills", "add", {"bucket": "technical", "name": "React"})

        result = editor.apply(
            "skills", "add", {"bucket": "technical", "name": "React Native"}
        )

        self.assertFalse(result.accepted)
        self.assertIn("no hay evidencia de React Native", result.message)

    def test_work_experience_can_be_reordered_but_not_grown(self):
        reordered = self.editor.apply(
            "work_experience", "reorder", {"order": [1, 0]}
        )
        self.assertTrue(reordered.accepted, reordered.message)
        self.assertEqual(
            self.editor.resume["work_experience"][0]["company"], "Mercado Libre"
        )

        grown = self.editor.apply("work_experience", "reorder", {"order": [0, 1, 2]})
        self.assertFalse(grown.accepted)
        self.assertIn("permutacion", grown.message)

    def test_hard_facts_of_an_entry_stay_frozen(self):
        result = self.editor.apply(
            "work_experience",
            "rewrite",
            {"index": 0, "position": "Staff Engineer", "end_date": "2025-01"},
        )

        self.assertFalse(result.accepted)
        self.assertIn("datos duros", result.message)
        self.assertEqual(
            self.editor.resume["work_experience"][0]["position"], "Backend Engineer"
        )

    def test_a_description_can_be_rewritten(self):
        result = self.editor.apply(
            "work_experience",
            "rewrite",
            {"index": 0, "description": "Pipelines en Jenkins sobre Kubernetes."},
        )

        self.assertTrue(result.accepted, result.message)
        self.assertEqual(result.change["section"], "work_experience")
        self.assertIn(
            "Kubernetes", self.editor.resume["work_experience"][0]["description"]
        )

    def test_technologies_added_to_an_entry_need_evidence(self):
        result = self.editor.apply(
            "work_experience",
            "rewrite",
            {"index": 1, "technologies": ["Java", "Rust"]},
        )

        self.assertFalse(result.accepted)
        self.assertIn("Rust", result.message)

    def test_rejections_are_collected_for_the_reply(self):
        self.editor.apply("skills", "add", {"bucket": "technical", "name": "Go"})
        self.editor.apply("personal_info", "rewrite", {"full_name": "X"})

        self.assertEqual(len(self.editor.rejections), 2)
        self.assertEqual(self.editor.applied_changes, [])

    def test_an_unknown_section_is_refused(self):
        result = self.editor.apply("salary", "rewrite", {"text": "mucho"})

        self.assertFalse(result.accepted)
        self.assertIn("seccion desconocida", result.message)

    def test_the_summary_is_rewritten_in_place(self):
        result = self.editor.apply(
            "summary", "rewrite", {"text": "Backend orientado a Kubernetes."}
        )

        self.assertTrue(result.accepted, result.message)
        self.assertEqual(
            self.editor.resume["summary"], "Backend orientado a Kubernetes."
        )
        self.assertEqual(len(self.editor.applied_changes), 1)


if __name__ == "__main__":
    unittest.main()
