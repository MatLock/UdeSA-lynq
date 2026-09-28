from __future__ import annotations

import unittest

from langdetect import DetectorFactory

from tests.fixtures.spanish import PROSE as SPANISH_PROSE
from tests.test_turn import RESUME, context_for

from agent import guard
from agent.schemas import EditProposal, EntryEdit, SkillsEdit
from agent.state import build_turn_state

DetectorFactory.seed = 0

BASE_SUMMARY = RESUME["summary"]


def state_for(**overrides):
    return build_turn_state(context_for(**overrides))


def proposal(**parts) -> EditProposal:
    return EditProposal(reply="done", **parts)


class SummaryGuardTest(unittest.TestCase):

    def test_a_rewrite_that_says_the_same_better_goes_in(self) -> None:
        state = state_for()
        text = "Backend engineer, eight years on distributed systems, Postgres and Kubernetes."

        rejections = guard.apply(state, proposal(summary=text))

        self.assertEqual(rejections, [])
        self.assertEqual(state.resume["summary"], text)
        self.assertEqual(
            state.changes,
            [{"section": "summary", "kind": "rewrite", "detail": "rewrote the summary", "fields": [], "index": None}],
        )

    def test_a_number_the_resume_does_not_carry_is_an_invention(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary="Backend engineer with 12 years on distributed systems."))

        self.assertEqual(rejections, [f"summary: {guard.UNBACKED_NUMBER} (12)"])
        self.assertEqual(state.resume["summary"], BASE_SUMMARY)
        self.assertEqual(state.changes, [])

    def test_a_number_the_resume_does_carry_is_fine(self) -> None:
        state = state_for(base_resume={**RESUME, "summary": "Backend engineer, 8 years."})

        rejections = guard.apply(state, proposal(summary="Backend engineer with 8 years on Postgres."))

        self.assertEqual(rejections, [])

    def test_a_posting_skill_the_resume_does_not_back_is_an_invention(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary="Backend engineer writing services in Go."))

        self.assertEqual(rejections, [f"summary: {guard.UNBACKED_SKILL} (Go)"])

    def test_a_posting_skill_the_prose_backs_may_enter_the_summary(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary="Backend engineer on Kubernetes and PostgreSQL."))

        self.assertEqual(rejections, [])

    def test_a_rewrite_that_doubles_the_original_is_padding(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary=BASE_SUMMARY + " " + "Also more. " * 30))

        self.assertEqual(rejections, [f"summary: {guard.TOO_LONG}"])

    def test_a_rewrite_in_the_language_of_the_chat_is_not_the_resume(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary=SPANISH_PROSE))

        self.assertEqual(rejections, [f"summary: {guard.LANGUAGE_MISMATCH} (es, not en)"])

    def test_the_same_text_again_is_not_a_change(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary=BASE_SUMMARY))

        self.assertEqual(rejections, [])
        self.assertEqual(state.changes, [])

    def test_blank_text_keeps_the_summary(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(summary="   "))

        self.assertEqual(rejections, [])
        self.assertEqual(state.changes, [])


class EntryGuardTest(unittest.TestCase):

    def test_an_entry_is_named_by_company_and_position(self) -> None:
        state = state_for()
        edit = EntryEdit(company="acme", position="backend engineer", description="Built and ran services on Kubernetes for product teams.")

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [])
        self.assertEqual(state.resume["work_experience"][1]["description"], edit.description)
        self.assertEqual(state.changes[0]["section"], "work_experience")
        self.assertEqual(state.changes[0]["index"], 1)
        self.assertEqual(state.changes[0]["fields"], ["description"])

    def test_a_paraphrased_position_still_finds_the_entry_by_its_company(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Acme", position="Backend Developer", description="Ran services on Kubernetes.")

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [])
        self.assertEqual(state.changes[0]["index"], 1)

    def test_an_entry_the_resume_does_not_have_cannot_be_written(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Initech", position="CTO", description="Led everything.")

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [f"work_experience CTO at Initech: {guard.UNKNOWN_ENTRY}"])
        self.assertEqual(state.resume["work_experience"], RESUME["work_experience"])

    def test_the_hard_facts_of_an_entry_never_move(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Globex", position="Semi Senior", description="Kept a Java monolith healthy.")

        guard.apply(state, proposal(entries=[edit]))

        entry = state.resume["work_experience"][0]
        self.assertEqual((entry["company"], entry["position"], entry["start_date"], entry["end_date"]), ("Globex", "Semi Senior", "2018-01", "2020-12"))
        self.assertEqual([e["company"] for e in state.resume["work_experience"]], ["Globex", "Acme"])

    def test_a_technology_moving_into_a_job_that_never_used_it_is_an_invention(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Globex", position="Semi Senior", description="Maintained a Java monolith backed by Postgres.")

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [f"work_experience Semi Senior at Globex: {guard.UNBACKED_SKILL} (PostgreSQL)"])

    def test_an_achievement_with_a_result_the_resume_never_stated_is_an_invention(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Acme", position="Backend Engineer", achievements=["Cut latency 40%."])

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [f"work_experience Backend Engineer at Acme: {guard.UNBACKED_NUMBER} (40)"])

    def test_the_years_of_the_entry_may_appear_in_its_prose(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Globex", position="Semi Senior", description="Maintained a Java monolith from 2018.")

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [])

    def test_achievements_replace_the_list_and_are_recorded(self) -> None:
        state = state_for()
        edit = EntryEdit(company="Acme", position="Backend Engineer", achievements=["Shipped services on Kubernetes.", " Kept them running. "])

        rejections = guard.apply(state, proposal(entries=[edit]))

        self.assertEqual(rejections, [])
        self.assertEqual(state.resume["work_experience"][1]["achievements"], ["Shipped services on Kubernetes.", "Kept them running."])
        self.assertEqual(state.changes[0]["fields"], ["achievements"])

    def test_an_entry_with_nothing_written_is_rejected(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer")]))

        self.assertEqual(rejections, [f"work_experience Backend Engineer at Acme: {guard.EMPTY_TEXT}"])


class SkillsGuardTest(unittest.TestCase):

    def test_a_skill_enters_with_the_wording_of_the_resume(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(skills=SkillsEdit(technical=["Java", "PostgreSQL", "Kubernetes"])))

        self.assertEqual(rejections, [])
        self.assertEqual(state.resume["skills"]["technical"], ["Java", "Postgres", "Kubernetes"])
        self.assertEqual(state.resume["skills"]["tools"], ["Jenkins"])
        self.assertEqual(state.changes, [{"section": "skills", "kind": "replace", "detail": "rewrote the technical skills", "fields": ["technical"], "index": None}])

    def test_a_skill_nothing_backs_rejects_the_bucket(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(skills=SkillsEdit(technical=["Java", "Go"])))

        self.assertEqual(rejections, [f"skills.technical: {guard.NO_EVIDENCE} (Go)"])
        self.assertEqual(state.resume["skills"], RESUME["skills"])

    def test_a_skill_the_resume_lists_cannot_be_dropped(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(skills=SkillsEdit(technical=["Kubernetes", "Postgres"])))

        self.assertEqual(rejections, [f"skills.technical: {guard.DROPPED_SKILL} (Java)"])
        self.assertEqual(state.resume["skills"], RESUME["skills"])

    def test_a_bucket_may_be_reordered_and_grown(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(skills=SkillsEdit(technical=["Kubernetes", "PostgreSQL", "Java"])))

        self.assertEqual(rejections, [])
        self.assertEqual(state.resume["skills"]["technical"], ["Kubernetes", "Postgres", "Java"])

    def test_a_short_summary_in_the_language_of_the_chat_is_still_caught(self) -> None:
        state = state_for()

        rejections = guard.apply(
            state,
            proposal(summary="Experiencia de ocho anios como ingeniero backend, especializado en Kubernetes y Postgres."),
        )

        self.assertEqual(rejections, [f"summary: {guard.LANGUAGE_MISMATCH} (es, not en)"])

    def test_two_spellings_of_one_technology_enter_once(self) -> None:
        state = state_for()

        guard.apply(state, proposal(skills=SkillsEdit(technical=["Java", "Postgres", "PostgreSQL"])))

        self.assertEqual(state.resume["skills"]["technical"], ["Java", "Postgres"])

    def test_the_same_buckets_again_are_not_a_change(self) -> None:
        state = state_for()

        rejections = guard.apply(state, proposal(skills=SkillsEdit(technical=["Java", "Postgres"])))

        self.assertEqual(rejections, [])
        self.assertEqual(state.changes, [])


class IndependenceTest(unittest.TestCase):

    def test_a_part_that_fails_does_not_hold_back_a_part_that_passes(self) -> None:
        state = state_for()

        rejections = guard.apply(
            state,
            proposal(summary="Backend engineer with 12 years.", skills=SkillsEdit(technical=["Kubernetes", "Java", "Postgres"])),
        )

        self.assertEqual(rejections, [f"summary: {guard.UNBACKED_NUMBER} (12)"])
        self.assertEqual(state.resume["summary"], BASE_SUMMARY)
        self.assertEqual(state.resume["skills"]["technical"], ["Kubernetes", "Java", "Postgres"])

    def test_a_part_rewritten_twice_in_a_turn_is_one_change(self) -> None:
        state = state_for()

        guard.apply(state, proposal(summary="Backend engineer on Postgres."))
        guard.apply(state, proposal(summary="Backend engineer on Postgres and Kubernetes."))

        self.assertEqual(len(state.changes), 1)
        self.assertEqual(state.resume["summary"], "Backend engineer on Postgres and Kubernetes.")

    def test_personal_info_is_pinned_back_on_the_way_out(self) -> None:
        state = state_for()

        self.assertNotIn("personal_info", state.resume)
        self.assertEqual(state.serialize_resume()["personal_info"], RESUME["personal_info"])
