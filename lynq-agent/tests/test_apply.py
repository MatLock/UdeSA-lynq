from __future__ import annotations

import unittest

from tests.test_turn import RESUME, context_for

from agent import apply
from agent.schemas import EditProposal, EntryEdit, SkillsEdit
from agent.state import build_turn_state


def state_for(**overrides):
    return build_turn_state(context_for(**overrides))


def proposal(**parts) -> EditProposal:
    return EditProposal(reply="done", **parts)


class PlanTest(unittest.TestCase):

    def test_every_part_is_resolved_beside_the_text_it_replaces(self) -> None:
        state = state_for()
        parts, rejections = apply.plan(state, proposal(
            summary="Backend engineer on Postgres.",
            entries=[EntryEdit(company="acme", position="backend engineer", description="Ran services on Kubernetes.")],
            skills=SkillsEdit(technical=["Java", "Postgres", "Kubernetes"]),
        ))

        self.assertEqual(rejections, [])
        self.assertEqual([p.id for p in parts], ["summary", "entry:0", "skills:technical"])
        self.assertEqual(parts[0].original, RESUME["summary"])
        self.assertEqual((parts[1].index, parts[1].label, parts[1].original), (1, "Backend Engineer at Acme", "Built services deployed on Kubernetes."))
        self.assertEqual((parts[2].original, parts[2].proposed), ("Java, Postgres", "Java, Postgres, Kubernetes"))

    def test_an_entry_with_nothing_written_is_not_a_part(self) -> None:
        parts, rejections = apply.plan(state_for(), proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer")]))

        self.assertEqual((parts, rejections), ([], []))

    def test_an_entry_the_resume_does_not_have_is_the_codes_own_rejection(self) -> None:
        parts, rejections = apply.plan(state_for(), proposal(entries=[EntryEdit(company="Initech", position="CTO", description="Led everything.")]))

        self.assertEqual(parts, [])
        self.assertEqual(rejections, [f"work_experience CTO at Initech: {apply.UNKNOWN_ENTRY}"])
        self.assertEqual(rejections[0].kind, apply.UNKNOWN_ENTRY)

    def test_a_paraphrased_position_still_finds_the_entry_by_its_company(self) -> None:
        parts, _ = apply.plan(state_for(), proposal(entries=[EntryEdit(company="Acme", position="Backend Developer", description="x")]))

        self.assertEqual(parts[0].index, 1)

    def test_the_original_of_an_entry_is_the_base_resume_not_the_edited_one(self) -> None:
        edited = {**RESUME, "work_experience": [RESUME["work_experience"][0], {**RESUME["work_experience"][1], "description": "Already rewritten once."}]}
        parts, _ = apply.plan(state_for(current_resume=edited), proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer", description="x")]))

        self.assertEqual(parts[0].original, "Built services deployed on Kubernetes.")


class CommitTest(unittest.TestCase):

    def test_only_the_approved_parts_enter(self) -> None:
        state = state_for()
        parts, _ = apply.plan(state, proposal(summary="Backend engineer on Postgres.", skills=SkillsEdit(technical=["Java", "Postgres", "Kubernetes"])))

        apply.commit(state, parts, {"skills:technical"})

        self.assertEqual(state.resume["summary"], RESUME["summary"])
        self.assertEqual(state.resume["skills"]["technical"], ["Java", "Postgres", "Kubernetes"])
        self.assertEqual(state.resume["skills"]["tools"], ["Jenkins"])
        self.assertEqual(state.changes, [{"section": "skills", "kind": "replace", "detail": "rewrote the technical skills", "fields": ["technical"], "index": None}])

    def test_an_entry_rewrite_keeps_the_hard_facts_and_records_its_index(self) -> None:
        state = state_for()
        parts, _ = apply.plan(state, proposal(entries=[EntryEdit(company="Globex", position="Semi Senior", description="Kept a Java monolith healthy.", achievements=["Shipped it."])]))

        apply.commit(state, parts, {"entry:0"})

        entry = state.resume["work_experience"][0]
        self.assertEqual((entry["company"], entry["position"], entry["start_date"], entry["end_date"]), ("Globex", "Semi Senior", "2018-01", "2020-12"))
        self.assertEqual((entry["description"], entry["achievements"]), ("Kept a Java monolith healthy.", ["Shipped it."]))
        self.assertEqual([e["company"] for e in state.resume["work_experience"]], ["Globex", "Acme"])
        self.assertEqual((state.changes[0]["index"], state.changes[0]["fields"]), (0, ["achievements", "description"]))

    def test_the_same_text_again_is_not_a_change(self) -> None:
        state = state_for()
        parts, _ = apply.plan(state, proposal(summary=RESUME["summary"], skills=SkillsEdit(technical=["Java", "Postgres"])))

        apply.commit(state, parts, {"summary", "skills:technical"})

        self.assertEqual(state.changes, [])

    def test_personal_info_is_never_in_the_resume_being_edited(self) -> None:
        state = state_for()

        self.assertNotIn("personal_info", state.resume)
        self.assertEqual(state.serialize_resume()["personal_info"], RESUME["personal_info"])
