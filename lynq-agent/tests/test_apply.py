from __future__ import annotations

import unittest

from tests.fixtures.spanish import CONFIRM_SPRING_BOOT, CONFIRMED_SPRING_BOOT, GO_AHEAD, YES
from tests.test_turn import JOB, RESUME, context_for

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
        parts, _ = apply.plan(state_for(), proposal(entries=[EntryEdit(company="Acme", position="Backend Developer", description="Ran services on Kubernetes.")]))

        self.assertEqual(parts[0].index, 1)

    def test_the_original_of_an_entry_is_the_base_resume_not_the_edited_one(self) -> None:
        edited = {**RESUME, "work_experience": [RESUME["work_experience"][0], {**RESUME["work_experience"][1], "description": "Already rewritten once."}]}
        parts, _ = apply.plan(state_for(current_resume=edited), proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer", description="Ran services on Kubernetes.")]))

        self.assertEqual(parts[0].original, "Built services deployed on Kubernetes.")


class StructureGuardTest(unittest.TestCase):

    ORIGINAL = "Led the platform team.\n- Cut p99 latency by 40%\n- Moved the ledger to Kafka"

    def entry(self, description="", achievements=()):
        return proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer", description=description, achievements=list(achievements))])

    def state(self):
        acme = {**RESUME["work_experience"][1], "description": self.ORIGINAL, "achievements": ["Cut p99 latency by 40%", "Shipped it"]}
        resume = {**RESUME, "work_experience": [RESUME["work_experience"][0], acme]}
        return state_for(base_resume=resume, current_resume=resume)

    def test_a_line_for_line_rewrite_passes_to_the_judge(self) -> None:
        parts, rejections = apply.plan(self.state(), self.entry("Ran the platform team.\n- Brought p99 latency down 40%\n- Took the ledger to Kafka"))

        self.assertEqual(rejections, [])
        self.assertEqual([p.id for p in parts], ["entry:0"])

    def test_lines_may_be_added_after_the_last_original_one(self) -> None:
        parts, rejections = apply.plan(self.state(), self.entry(self.ORIGINAL + "\n- Ran it on Kubernetes"))

        self.assertEqual(rejections, [])
        self.assertEqual(len(parts), 1)

    def test_fewer_lines_is_cut_before_the_judge_sees_it(self) -> None:
        parts, rejections = apply.plan(self.state(), self.entry("Led the platform team.\n- Cut p99 latency by 40% and moved the ledger to Kafka"))

        self.assertEqual(parts, [])
        self.assertEqual(rejections[0].kind, apply.CUT)
        self.assertIn("the original has 3 lines and the proposal 2", rejections[0])
        self.assertTrue(rejections[0].startswith("work_experience Backend Engineer at Acme: "))

    def test_a_number_that_disappears_is_cut(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry("Led the platform team.\n- Cut p99 latency a lot\n- Moved the ledger to Kafka"))

        self.assertEqual(rejections[0].kind, apply.CUT)
        self.assertIn("numbers of the original are missing: 40%", rejections[0])

    def test_a_line_moved_as_it_was_is_moved(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry("Led the platform team.\n- Moved the ledger to Kafka\n- Cut p99 latency by 40%"))

        self.assertEqual(rejections[0].kind, apply.MOVED)
        self.assertIn("line 2 of the proposal is line 3 of the original", rejections[0])

    def test_achievements_are_one_per_item_and_measured_like_the_description(self) -> None:
        parts, rejections = apply.plan(self.state(), self.entry(achievements=["Cut p99 latency by 40%\nShipped it\n- Ran it on Kubernetes"]))

        self.assertEqual(rejections, [])
        self.assertEqual(parts[0].fields["achievements"], ["Cut p99 latency by 40%", "Shipped it", "Ran it on Kubernetes"])

    def test_a_dropped_achievement_is_cut(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry(achievements=["Cut p99 latency by 40%"]))

        self.assertEqual(rejections[0].kind, apply.CUT)
        self.assertIn("the original has 2 lines and the proposal 1", rejections[0])

    def test_a_summary_that_loses_a_number_is_cut(self) -> None:
        resume = {**RESUME, "summary": "Backend engineer, 8 years, 3 teams."}
        _, rejections = apply.plan(state_for(base_resume=resume, current_resume=resume), proposal(summary="Backend engineer with 8 years on distributed systems."))

        self.assertEqual([r.kind for r in rejections], [apply.CUT])
        self.assertIn("numbers of the original are missing: 3", rejections[0])

    def test_a_bucket_missing_a_skill_is_cut(self) -> None:
        _, rejections = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["Postgres", "Kubernetes"])))

        self.assertEqual(rejections[0].kind, apply.CUT)
        self.assertEqual(str(rejections[0]), "skills.technical: skills of the bucket are missing: Java")

    def test_a_bucket_that_moves_a_skill_or_inserts_before_the_last_one_is_moved(self) -> None:
        _, moved = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["Postgres", "Java"])))
        _, inserted = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["Java", "Kubernetes", "Postgres"])))

        self.assertEqual((moved[0].kind, inserted[0].kind), (apply.MOVED, apply.MOVED))

    def test_a_technology_that_disappears_from_a_text_is_cut(self) -> None:
        _, rejections = apply.plan(state_for(), proposal(summary="Backend engineer with eight years on distributed systems and Kubernetes."))

        self.assertEqual(rejections[0].kind, apply.CUT)
        self.assertIn("technologies of the original are missing: Postgres", rejections[0])

    def test_a_new_skill_neither_the_resume_nor_the_candidate_names_is_unbacked(self) -> None:
        _, rejections = apply.plan(state_for(message=GO_AHEAD), proposal(skills=SkillsEdit(technical=["Java", "Postgres", "Go"])))

        self.assertEqual(rejections[0].kind, apply.UNBACKED)
        self.assertEqual(str(rejections[0]), "skills.technical: neither the resume nor the candidate names these: Go")

    def test_a_new_skill_the_candidate_said_they_have_is_backed(self) -> None:
        parts, rejections = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["Java", "Postgres", "Go"])))

        self.assertEqual((rejections, parts[0].names), ([], ["Java", "Postgres", "Go"]))

    def test_what_the_candidate_said_earlier_in_the_conversation_still_backs(self) -> None:
        state = state_for(message=GO_AHEAD, statements=[CONFIRM_SPRING_BOOT])
        parts, rejections = apply.plan(state, proposal(skills=SkillsEdit(technical=["Java", "Postgres", "Spring Boot"])))

        self.assertEqual((rejections, parts[0].names), ([], ["Java", "Postgres", "Spring Boot"]))

    def test_a_fact_the_proposal_records_as_confirmed_backs_it(self) -> None:
        state = state_for(message=YES)
        parts, rejections = apply.plan(state, proposal(confirmed=[CONFIRMED_SPRING_BOOT], skills=SkillsEdit(technical=["Java", "Postgres", "Spring Boot"])))

        self.assertEqual((rejections, parts[0].names), ([], ["Java", "Postgres", "Spring Boot"]))

    def test_a_fact_confirmed_in_an_earlier_turn_backs_an_entry_line(self) -> None:
        state = state_for(message=GO_AHEAD)
        state.confirm([CONFIRMED_SPRING_BOOT])
        _, rejections = apply.plan(state, proposal(entries=[EntryEdit(company="Acme", position="Backend Engineer", description="Built services deployed on Kubernetes.\n- Built microservices on Spring Boot")]))

        self.assertEqual(rejections, [])

    def test_a_name_is_the_same_name_however_its_spaces_and_dots_are_drawn(self) -> None:
        self.assertEqual(apply.unbacked_names("- Built it on Spring Boot and Node.js", "tengo springboot y nodejs"), [])
        self.assertEqual(apply.unbacked_names("- Built it on Spring Boot", "tengo Spring"), ["Boot"])

    def test_a_new_skill_an_entry_lists_is_backed(self) -> None:
        parts, rejections = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["Java", "Postgres", "Kubernetes"])))

        self.assertEqual((rejections, parts[0].names), ([], ["Java", "Postgres", "Kubernetes"]))

    def test_the_judge_reads_the_whole_entry_on_both_sides(self) -> None:
        parts, _ = apply.plan(self.state(), self.entry("Ran the platform team.\n- Brought p99 latency down 40%\n- Took the ledger to Kafka"))

        self.assertEqual(parts[0].original, self.ORIGINAL + "\nCut p99 latency by 40%\nShipped it")
        self.assertEqual(parts[0].proposed, "Ran the platform team.\n- Brought p99 latency down 40%\n- Took the ledger to Kafka\nCut p99 latency by 40%\nShipped it")
        self.assertEqual(parts[0].fields, {"description": "Ran the platform team.\n- Brought p99 latency down 40%\n- Took the ledger to Kafka"})

    def test_a_name_the_entry_does_not_state_is_unbacked(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry(self.ORIGINAL + "\n- Utilized AWS services including Lambdas and Cognito."))

        self.assertEqual(rejections[0].kind, apply.UNBACKED)
        self.assertIn("names neither the resume states here nor the candidate said, so they cannot enter: AWS, Lambdas, Cognito", rejections[0])

    def test_a_name_from_another_entry_does_not_back_this_one(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry(self.ORIGINAL + "\n- Kept the Java monolith alive"))

        self.assertEqual(rejections[0].kind, apply.UNBACKED)
        self.assertIn("Java", rejections[0])

    def test_a_name_the_entry_states_may_be_brought_out(self) -> None:
        _, rejections = apply.plan(self.state(), self.entry(self.ORIGINAL + "\n- Ran it all on Kubernetes. Kafka carried the events"))

        self.assertEqual(rejections, [])

    def test_the_first_word_of_a_sentence_is_not_a_name(self) -> None:
        self.assertEqual(apply.names_in("- Designed REST APIs. Built Kafka consumers, 3 of them"), ["REST", "APIs", "Kafka", "3"])

    def test_a_name_glued_to_a_word_by_a_dot_is_still_a_name(self) -> None:
        self.assertEqual(apply.names_in("- Utilized C# and.NET Core, then Node.js"), ["C#", "NET", "Core", "Node.js"])
        self.assertEqual(apply.unbacked_names("- Utilized C# and.NET Core", "Built it on C# and .NET Core"), [])
        self.assertEqual(apply.unbacked_names("- Utilized C# and.NET Core", "Built it on Java"), ["C#", "NET", "Core"])

    def test_a_summary_may_only_name_what_the_resume_names(self) -> None:
        _, unbacked = apply.plan(state_for(), proposal(summary="Backend engineer with eight years on distributed systems and Postgres, proficient in PostgreSQL and JUnit."))
        _, backed = apply.plan(state_for(), proposal(summary="Backend engineer with eight years on distributed systems and Postgres, deploying on Kubernetes with Jenkins."))

        self.assertEqual(unbacked[0].kind, apply.UNBACKED)
        self.assertIn("PostgreSQL, JUnit", unbacked[0])
        self.assertEqual(backed, [])

    def test_a_number_the_resume_does_not_have_is_unbacked_and_dates_do_not_vouch_for_it(self) -> None:
        _, rejections = apply.plan(state_for(), proposal(summary="Backend engineer with 12 years on distributed systems and Postgres."))

        self.assertEqual(rejections[0].kind, apply.UNBACKED)
        self.assertIn("cannot enter: 12", rejections[0])

    def test_a_plural_is_the_same_name(self) -> None:
        self.assertEqual(apply.unbacked_names("- Built REST APIs on Lambda", "Designed a REST API. Ran Lambdas."), [])

    def test_a_bucket_grown_at_its_end_passes_whatever_the_case_of_the_names(self) -> None:
        parts, rejections = apply.plan(state_for(), proposal(skills=SkillsEdit(technical=["java", "Postgres", "Kubernetes"])))

        self.assertEqual(rejections, [])
        self.assertEqual(parts[0].names, ["java", "Postgres", "Kubernetes"])


class GapsTest(unittest.TestCase):

    def test_a_posting_skill_named_nowhere_is_a_gap(self) -> None:
        self.assertEqual(apply.gaps_in(JOB["extractedSkills"], RESUME, []), ["PostgreSQL", "Go"])

    def test_a_skill_the_candidate_mentioned_is_no_longer_a_gap(self) -> None:
        self.assertEqual(apply.gaps_in(JOB["extractedSkills"], RESUME, [CONFIRM_SPRING_BOOT, "Go"]), ["PostgreSQL"])

    def test_another_spelling_of_what_the_resume_has_still_counts_as_a_gap_for_the_code(self) -> None:
        self.assertIn("PostgreSQL", apply.gaps_in(["PostgreSQL"], RESUME, []))


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
