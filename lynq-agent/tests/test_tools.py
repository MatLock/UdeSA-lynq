from __future__ import annotations

import unittest

from tests.fixtures.spanish import PROSE

from agent.context import TurnContext, build_turn_state, use_turn_state
from agent.tools import STEP_LIMIT_MESSAGE, apply_edit, find_evidence
from db.models import SpanKind

JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "extractedSkills": ["Kubernetes", "PostgreSQL", "Go"],
}

RESUME = {
    "personal_info": {"full_name": "Ada Lovelace", "email": "ada@example.com"},
    "summary": "Backend engineer with eight years on distributed systems and Postgres.",
    "work_experience": [
        {
            "company": "Globex",
            "position": "Semi Senior",
            "start_date": "2018-01",
            "end_date": "2020-12",
            "is_current": False,
            "description": "Maintained a Java monolith.",
            "achievements": ["Cut the nightly batch from four hours to forty minutes"],
            "technologies": ["Java"],
        },
        {
            "company": "Acme",
            "position": "Backend Engineer",
            "start_date": "2021-01",
            "end_date": None,
            "is_current": True,
            "description": "Built services deployed on Kubernetes.",
            "achievements": [],
            "technologies": ["Kubernetes"],
        },
    ],
    "education": [{"institution": "UBA", "degree": "Systems", "description": "Thesis."}],
    "skills": {"technical": ["Java", "Postgres"], "tools": ["Jenkins"], "soft": []},
}

SPANISH = PROSE


def state_for(language: str = "es", resume_language: str = "es", max_steps: int = 12):
    context = TurnContext(
        conversation_id="conversation-1",
        run_token="token-1",
        language=language,
        resume_language=resume_language,
        job_snapshot=JOB,
        base_resume=RESUME,
        current_resume=RESUME,
        history=[],
        message="go ahead",
        max_steps=max_steps,
        turns_left=9,
    )
    return build_turn_state(context)


def hits_of(found, claim=None):
    if claim is None:
        return found[0]["hits"]
    return next(entry["hits"] for entry in found if entry["claim"] == claim)


class FindEvidenceTest(unittest.IsolatedAsyncioTestCase):

    async def test_it_returns_the_wording_of_the_resume_not_of_the_posting(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke({"claims": ["PostgreSQL"]})

        hits = hits_of(found)
        self.assertEqual([hit["matched"] for hit in hits], ["Postgres", "Postgres"])
        self.assertIn("summary", [hit["path"] for hit in hits])

    async def test_it_finds_a_skill_buried_in_prose(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke({"claims": ["Kubernetes"]})

        hits = hits_of(found)
        self.assertIn("work_experience[1].description", [hit["path"] for hit in hits])

    async def test_it_returns_nothing_when_the_resume_does_not_back_the_claim(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke({"claims": ["Go"]})

        self.assertEqual(hits_of(found), [])

    async def test_it_never_looks_at_personal_info(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke({"claims": ["Lovelace"]})

        self.assertEqual(hits_of(found), [])

    async def test_one_call_answers_every_claim_it_was_given(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke(
                {"claims": ["PostgreSQL", "Kubernetes", "Go", "Lovelace"]}
            )

        self.assertEqual(
            [entry["claim"] for entry in found],
            ["PostgreSQL", "Kubernetes", "Go", "Lovelace"],
        )
        self.assertNotEqual(hits_of(found, "PostgreSQL"), [])
        self.assertNotEqual(hits_of(found, "Kubernetes"), [])
        self.assertEqual(hits_of(found, "Go"), [])
        self.assertEqual(hits_of(found, "Lovelace"), [])

    async def test_a_batch_remembers_the_evidence_of_every_claim(self) -> None:
        state = state_for()
        with use_turn_state(state):
            await find_evidence.ainvoke({"claims": ["Kubernetes", "PostgreSQL"]})

        self.assertEqual(state.evidence["kubernetes"], "Kubernetes")
        self.assertEqual(state.evidence["postgres"], "Postgres")

    async def test_a_claim_asked_twice_is_looked_up_once(self) -> None:
        state = state_for()
        with use_turn_state(state):
            found = await find_evidence.ainvoke(
                {"claims": ["Kubernetes", "kubernetes ", "Kubernetes"]}
            )

        self.assertEqual([entry["claim"] for entry in found], ["Kubernetes"])

    async def test_an_empty_claim_list_is_rejected(self) -> None:
        state = state_for()
        with use_turn_state(state):
            answer = await find_evidence.ainvoke({"claims": ["", "   "]})

        self.assertEqual(
            answer, "REJECTED: claims must hold at least one non-empty string"
        )

    async def test_too_many_claims_in_one_call_are_rejected(self) -> None:
        state = state_for()
        with use_turn_state(state):
            answer = await find_evidence.ainvoke(
                {"claims": [f"skill {number}" for number in range(21)]}
            )

        self.assertEqual(answer, "REJECTED: at most 20 claims per call")

    async def test_what_it_finds_is_remembered_for_this_turn(self) -> None:
        state = state_for()
        with use_turn_state(state):
            await find_evidence.ainvoke({"claims": ["Kubernetes"]})

        self.assertEqual(state.evidence["kubernetes"], "Kubernetes")


class ApplyEditTest(unittest.IsolatedAsyncioTestCase):

    async def edit(self, state, section, op, payload) -> str:
        with use_turn_state(state):
            return await apply_edit.ainvoke(
                {"section": section, "op": op, "payload": payload}
            )

    async def test_personal_info_is_immutable(self) -> None:
        state = state_for()
        answer = await self.edit(state, "personal_info", "rewrite", {"full_name": "X"})

        self.assertEqual(answer, "REJECTED: personal_info is immutable")

    async def test_dates_are_immutable(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "rewrite", {"index": 0, "start_date": "2010-01"}
        )

        self.assertEqual(answer, "REJECTED: dates are immutable")

    async def test_new_entries_are_not_allowed(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "add", {"company": "Initech"}
        )

        self.assertEqual(answer, "REJECTED: new entries are not allowed")

    async def test_a_reorder_that_grows_the_section_is_not_allowed(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "reorder", {"order": [0, 1, 2]}
        )

        self.assertEqual(
            answer,
            "REJECTED: order must list each of the 2 positions of work_experience "
            "exactly once, from 0 to 1",
        )

    async def test_a_reorder_that_misses_a_position_says_how_many_there_are(self) -> None:
        state = state_for()
        answer = await self.edit(state, "work_experience", "reorder", {"order": [1]})

        self.assertIn("2 positions of work_experience", answer)
        self.assertEqual(state.changes, [])


    async def test_a_skill_without_evidence_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "skills", "replace", {"technical": ["Go"]})

        self.assertEqual(answer, "REJECTED: no evidence in base resume")
        self.assertEqual(state.changes, [])

    async def test_a_skill_enters_with_the_wording_of_the_resume(self) -> None:
        state = state_for()
        with use_turn_state(state):
            await find_evidence.ainvoke({"claims": ["Kubernetes"]})
        answer = await self.edit(
            state, "skills", "replace", {"technical": ["PostgreSQL", "Kubernetes"]}
        )

        self.assertEqual(answer, "OK")
        self.assertEqual(state.resume["skills"]["technical"], ["Postgres", "Kubernetes"])

    async def test_it_rewrites_the_summary(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "summary", "rewrite", {"text": "Backend engineer on Kubernetes."}
        )

        self.assertEqual(answer, "OK")
        self.assertEqual(state.resume["summary"], "Backend engineer on Kubernetes.")
        self.assertEqual(state.changes[0]["section"], "summary")

    async def test_a_rewritten_entry_records_which_fields_and_which_entry(self) -> None:
        state = state_for()
        answer = await self.edit(
            state,
            "work_experience",
            "rewrite",
            {"index": 1, "description": "Ran the Kubernetes platform."},
        )

        self.assertEqual(answer, "OK")
        change = state.changes[0]
        self.assertEqual(change["section"], "work_experience")
        self.assertEqual(change["kind"], "rewrite")
        self.assertEqual(change["fields"], ["description"])
        self.assertEqual(change["index"], 1)

    async def test_a_skills_rewrite_records_the_buckets_it_touched(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "skills", "replace", {"technical": ["Kubernetes"]}
        )

        self.assertEqual(answer, "OK")
        change = state.changes[0]
        self.assertEqual(change["section"], "skills")
        self.assertEqual(change["fields"], ["technical"])
        self.assertIsNone(change["index"])

    async def test_rewriting_the_summary_with_what_it_already_says_changes_nothing(self) -> None:
        state = state_for()
        answer = await self.edit(state, "summary", "rewrite", {"text": RESUME["summary"]})

        self.assertEqual(answer, "OK")
        self.assertEqual(state.changes, [])

    async def test_it_reorders_the_experience(self) -> None:
        state = state_for()
        answer = await self.edit(state, "work_experience", "reorder", {"order": [1, 0]})

        self.assertEqual(answer, "OK")
        self.assertEqual(
            [entry["company"] for entry in state.resume["work_experience"]],
            ["Acme", "Globex"],
        )

    async def test_only_prose_fields_are_editable(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "rewrite", {"index": 0, "position": "Architect"}
        )

        self.assertEqual(answer, "REJECTED: field is not editable")

    async def test_an_index_outside_the_section_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "rewrite", {"index": 7, "description": "x"}
        )

        self.assertEqual(answer, "REJECTED: index out of range")

    async def test_an_unknown_section_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "hobbies", "rewrite", {"text": "chess"})

        self.assertEqual(answer, "REJECTED: unknown section")

    async def test_an_unknown_op_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "summary", "translate", {"text": "hola"})

        self.assertEqual(answer, "REJECTED: unknown op for this section")

    async def test_a_payload_without_text_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "summary", "rewrite", {"text": "   "})

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_prose_written_in_the_language_of_the_chat_is_rejected(self) -> None:
        state = state_for(language="es", resume_language="en")
        answer = await self.edit(state, "summary", "rewrite", {"text": SPANISH})

        self.assertEqual(
            answer, "REJECTED: payload language (es) does not match resume language (en)"
        )

    async def test_prose_in_the_language_of_the_resume_is_applied(self) -> None:
        state = state_for(language="es", resume_language="es")
        answer = await self.edit(state, "summary", "rewrite", {"text": SPANISH})

        self.assertEqual(answer, "OK")

    async def test_short_payloads_are_never_language_checked(self) -> None:
        state = state_for(language="es", resume_language="en")
        answer = await self.edit(state, "summary", "rewrite", {"text": "Kubernetes y Java"})

        self.assertEqual(answer, "OK")


class StepLimitTest(unittest.IsolatedAsyncioTestCase):

    async def test_the_tools_stop_editing_once_the_soft_cap_is_reached(self) -> None:
        state = state_for(max_steps=2)
        state.steps = 2

        with use_turn_state(state):
            evidence = await find_evidence.ainvoke({"claims": ["Kubernetes"]})
            edit = await apply_edit.ainvoke(
                {"section": "summary", "op": "rewrite", "payload": {"text": "x"}}
            )

        self.assertEqual(evidence, STEP_LIMIT_MESSAGE)
        self.assertEqual(edit, STEP_LIMIT_MESSAGE)
        self.assertEqual(state.changes, [])

    async def test_the_soft_cap_leaves_one_span_behind(self) -> None:
        state = state_for(max_steps=1)
        state.steps = 1

        with use_turn_state(state):
            await find_evidence.ainvoke({"claims": ["Kubernetes"]})
            await find_evidence.ainvoke({"claims": ["Postgres"]})

        limits = [span for span in state.spans if span.kind == SpanKind.LIMIT]
        self.assertEqual(len(limits), 1)
        self.assertEqual(limits[0].name, "max_steps")


class OutsideATurnTest(unittest.IsolatedAsyncioTestCase):

    async def test_the_tools_refuse_to_run_without_a_turn(self) -> None:
        with self.assertRaises(RuntimeError):
            await find_evidence.ainvoke({"claims": ["Kubernetes"]})


class MoreApplyEditTest(unittest.IsolatedAsyncioTestCase):

    async def edit(self, state, section, op, payload) -> str:
        with use_turn_state(state):
            return await apply_edit.ainvoke(
                {"section": section, "op": op, "payload": payload}
            )

    async def test_it_rewrites_the_description_of_one_experience(self) -> None:
        state = state_for()
        answer = await self.edit(
            state,
            "work_experience",
            "rewrite",
            {
                "index": 1,
                "description": "Built services on Kubernetes.",
                "achievements": ["Took deploys from weekly to daily"],
            },
        )

        self.assertEqual(answer, "OK")
        self.assertEqual(
            state.resume["work_experience"][1]["description"],
            "Built services on Kubernetes.",
        )

    async def test_a_description_written_in_the_language_of_the_chat_is_rejected(self) -> None:
        state = state_for(language="es", resume_language="en")
        answer = await self.edit(
            state, "work_experience", "rewrite", {"index": 0, "description": SPANISH}
        )

        self.assertEqual(
            answer, "REJECTED: payload language (es) does not match resume language (en)"
        )

    async def test_an_index_that_is_not_a_number_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "rewrite", {"index": "first", "description": "x"}
        )

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_a_rewrite_without_fields_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "work_experience", "rewrite", {"index": 0})

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_an_order_that_is_not_a_list_of_numbers_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "work_experience", "reorder", {"order": ["first", "second"]}
        )

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_an_order_that_repeats_an_entry_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "work_experience", "reorder", {"order": [0, 0]})

        self.assertIn("exactly once", answer)

    async def test_editing_a_section_the_resume_does_not_have_yet_is_out_of_range(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "projects", "rewrite", {"index": 0, "description": "x"}
        )

        self.assertEqual(answer, "REJECTED: index out of range")

    async def test_an_unknown_op_on_an_experience_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "work_experience", "translate", {"index": 0})

        self.assertEqual(answer, "REJECTED: unknown op for this section")

    async def test_an_unknown_op_on_the_skills_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "skills", "sort", {"technical": []})

        self.assertEqual(answer, "REJECTED: unknown op for this section")

    async def test_a_skills_payload_that_is_not_a_list_of_names_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "skills", "replace", {"technical": "Java"})

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_a_skills_payload_without_a_known_bucket_is_rejected(self) -> None:
        state = state_for()
        answer = await self.edit(state, "skills", "replace", {"hard": ["Java"]})

        self.assertEqual(answer, "REJECTED: invalid payload")

    async def test_replacing_the_skills_with_the_same_ones_changes_nothing(self) -> None:
        state = state_for()
        answer = await self.edit(
            state, "skills", "replace", {"technical": ["Java", "Postgres"]}
        )

        self.assertEqual(answer, "OK")
        self.assertEqual(state.changes, [])

    async def test_skills_written_as_a_flat_list_are_still_evidence(self) -> None:
        flat = dict(RESUME, skills=["Java", "Postgres"])
        context = TurnContext(
            conversation_id="conversation-1",
            run_token="token-1",
            language="es",
            resume_language="es",
            job_snapshot=JOB,
            base_resume=flat,
            current_resume=flat,
            history=[],
            message="go ahead",
            max_steps=12,
            turns_left=9,
        )
        state = build_turn_state(context)

        answer = await self.edit(state, "skills", "replace", {"technical": ["Java"]})

        self.assertEqual(answer, "OK")

    async def test_text_that_langdetect_cannot_read_is_applied(self) -> None:
        state = state_for(language="es", resume_language="en")
        answer = await self.edit(
            state, "summary", "rewrite", {"text": "1234567890 " * 12}
        )

        self.assertEqual(answer, "OK")


class EvidenceCapTest(unittest.IsolatedAsyncioTestCase):

    async def test_it_stops_after_ten_hits(self) -> None:
        crowded = {
            "summary": "Java",
            "work_experience": [
                {"company": f"C{index}", "description": "Java", "technologies": ["Java"]}
                for index in range(8)
            ],
            "skills": {"technical": ["Java"]},
        }
        context = TurnContext(
            conversation_id="conversation-1",
            run_token="token-1",
            language="es",
            resume_language="es",
            job_snapshot=JOB,
            base_resume=crowded,
            current_resume=crowded,
            history=[],
            message="go ahead",
            max_steps=12,
            turns_left=9,
        )
        state = build_turn_state(context)

        with use_turn_state(state):
            found = await find_evidence.ainvoke({"claims": ["Java"]})

        self.assertEqual(len(hits_of(found)), 10)
