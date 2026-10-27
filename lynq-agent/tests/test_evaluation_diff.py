from __future__ import annotations

import unittest

from evaluation.diff import (
    ENTRIES_ADDED_OR_DROPPED,
    ENTRIES_REORDERED,
    IMMUTABLE_FIELD_CHANGED,
    OUT_OF_SURFACE_CHANGED,
    PERSONAL_INFO_CHANGED,
    SKILLS_DROPPED,
    SKILLS_REORDERED,
    units_of,
)

BEFORE = {
    "personal_info": {"full_name": "Ada"},
    "summary": "Backend engineer.",
    "work_experience": [
        {"company": "Acme", "position": "Engineer", "start_date": "2021-01", "end_date": None,
         "is_current": True, "description": "Built services.", "achievements": ["Cut latency"],
         "technologies": ["Java"]},
        {"company": "Globex", "position": "Junior", "start_date": "2018-01", "end_date": "2020-12",
         "is_current": False, "description": "Maintained a monolith.", "achievements": [], "technologies": []},
    ],
    "education": [{"institution": "UdeSA", "degree": "BSc"}],
    "skills": {"technical": ["Java", "Postgres"], "tools": ["Docker"], "soft": []},
}


def changed(**changes):
    import copy
    after = copy.deepcopy(BEFORE)
    for key, value in changes.items():
        after[key] = value
    return after


class DiffTest(unittest.TestCase):

    def test_an_unchanged_resume_has_no_units_and_no_faults(self) -> None:
        units, faults = units_of("k", BEFORE, changed())

        self.assertEqual(units, [])
        self.assertEqual(faults, [])

    def test_a_rewritten_summary_is_one_unit(self) -> None:
        units, faults = units_of("k", BEFORE, changed(summary="Backend engineer on Kubernetes."))

        self.assertEqual(len(units), 1)
        self.assertEqual(units[0].section, "summary")
        self.assertEqual(units[0].original, "Backend engineer.")
        self.assertEqual(units[0].proposed, "Backend engineer on Kubernetes.")
        self.assertEqual(faults, [])

    def test_a_rewritten_entry_is_one_unit_named_by_its_position_and_company(self) -> None:
        after = changed()
        after["work_experience"][0]["description"] = "Built services on Kubernetes."

        units, faults = units_of("k", BEFORE, after)

        self.assertEqual([u.label for u in units], ["Engineer at Acme"])
        self.assertIn("Built services.", units[0].original)
        self.assertIn("Cut latency", units[0].original)
        self.assertEqual(faults, [])

    def test_reordered_entries_are_a_fault_and_rewrites_still_match_by_identity(self) -> None:
        after = changed()
        after["work_experience"].reverse()
        after["work_experience"][1]["description"] = "Built many services."

        units, faults = units_of("k", BEFORE, after)

        self.assertEqual(faults, [ENTRIES_REORDERED])
        self.assertEqual([u.label for u in units], ["Engineer at Acme"])

    def test_a_dropped_entry_is_a_fault(self) -> None:
        after = changed()
        del after["work_experience"][1]

        _, faults = units_of("k", BEFORE, after)

        self.assertEqual(faults, [ENTRIES_ADDED_OR_DROPPED])

    def test_a_changed_date_is_a_fault_on_the_entry(self) -> None:
        after = changed()
        after["work_experience"][1]["end_date"] = "2021-06"

        units, faults = units_of("k", BEFORE, after)

        self.assertEqual(faults, [IMMUTABLE_FIELD_CHANGED])
        self.assertEqual(units[0].faults, [IMMUTABLE_FIELD_CHANGED])

    def test_skills_may_grow_at_the_end_but_not_shrink_or_move(self) -> None:
        grown = changed(skills={"technical": ["Java", "Postgres", "Kubernetes"], "tools": ["Docker"], "soft": []})
        shrunk = changed(skills={"technical": ["Java"], "tools": ["Docker"], "soft": []})
        moved = changed(skills={"technical": ["Postgres", "Java"], "tools": ["Docker"], "soft": []})

        units, faults = units_of("k", BEFORE, grown)
        self.assertEqual(faults, [])
        self.assertEqual(units[0].label, "technical")
        self.assertEqual(units[0].proposed, "Java, Postgres, Kubernetes")

        self.assertEqual(units_of("k", BEFORE, shrunk)[1], [SKILLS_DROPPED])
        self.assertEqual(units_of("k", BEFORE, moved)[1], [SKILLS_REORDERED])

    def test_education_and_personal_info_are_out_of_the_surface(self) -> None:
        after = changed(education=[{"institution": "MIT", "degree": "BSc"}], personal_info={"full_name": "Eve"})

        units, faults = units_of("k", BEFORE, after)

        self.assertEqual(set(faults), {OUT_OF_SURFACE_CHANGED, PERSONAL_INFO_CHANGED})
        self.assertEqual([u.section for u in units], ["education"])

    def test_unit_ids_are_stable_for_a_key(self) -> None:
        first, _ = units_of("k", BEFORE, changed(summary="x"))
        again, _ = units_of("k", BEFORE, changed(summary="x"))
        other, _ = units_of("other", BEFORE, changed(summary="x"))

        self.assertEqual(first[0].id, again[0].id)
        self.assertNotEqual(first[0].id, other[0].id)
