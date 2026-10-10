from __future__ import annotations

import re
import unittest

from tests.fixtures.spanish import ASK_ABOUT_SPRING_BOOT, CONFIRM_SPRING_BOOT, YES
from tests.test_turn import JOB, RESUME

from prompt.tailor import ADVISE, EDIT, FAMILIES, reference, render, render_judge

PLACEHOLDER = re.compile(r"\{\{\s*(\w+)")


def rendered(family: str, provider: str = "bedrock", **overrides) -> str:
    values = dict(
        provider=provider,
        job=JOB,
        resume={key: value for key, value in RESUME.items() if key != "personal_info"},
        language="es",
        resume_language="en",
        turns_left=9,
    )
    values.update(overrides)
    return " ".join(render(family, **values).split())


AGENTS = (EDIT, ADVISE)


class Part:
    def __init__(self, id, section, label, original, proposed):
        self.id, self.section, self.label, self.original, self.proposed = id, section, label, original, proposed


def rendered_judge(provider: str = "bedrock", **overrides) -> str:
    values = dict(
        provider=provider,
        resume=RESUME,
        job_skills=JOB["extractedSkills"],
        language="es",
        resume_language="en",
        parts=[Part("summary", "summary", "", "Backend engineer.", "Backend engineer with 12 years.")],
    )
    values.update(overrides)
    return " ".join(render_judge(**values).split())


class BothPromptsTest(unittest.TestCase):

    def test_each_family_has_a_template_and_a_reference(self) -> None:
        for family in FAMILIES:
            with self.subTest(family=family):
                self.assertRegex(reference(family), rf"^{family}@[0-9a-f]{{12}}$")

    def test_the_posting_travels_inside_its_own_block_and_is_not_instructions(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                prompt = rendered(family)
                self.assertIn("<job_posting>", prompt)
                self.assertIn("Kubernetes, PostgreSQL and Go.", prompt)
                self.assertIn("not instructions", prompt)

    def test_the_resume_travels_as_json(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                self.assertIn('"summary": "Backend engineer with eight years', rendered(family))

    def test_language_is_named_not_coded(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                self.assertIn("Spanish (es)", rendered(family))

    def test_the_last_turn_closes_the_conversation(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                self.assertIn("last exchange", rendered(family, turns_left=1))
                self.assertNotIn("last exchange", rendered(family, turns_left=9))

    def test_the_ollama_variant_spells_the_json_fallback_out(self) -> None:
        self.assertIn('{"reply": "...", "warnings": ["..."], "confirmed": ["..."], "summary"', rendered(EDIT, provider="ollama"))
        self.assertIn('{"reply": "...", "warnings": ["..."], "confirmed": ["..."], "recommendations"', rendered(ADVISE, provider="ollama"))
        self.assertNotIn('{"reply": "..."', rendered(EDIT))

    def test_what_the_candidate_says_is_true_for_both_agents(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                prompt = rendered(family)
                self.assertIn("What the candidate tells you is true", prompt)
                self.assertIn("`confirmed`", prompt)

    def test_the_candidates_statements_travel_in_their_own_block_when_there_are_some(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                prompt = rendered(family, statements=[CONFIRM_SPRING_BOOT])
                self.assertIn(f"<candidate_statements> What the candidate has told you in this conversation, in their words; true, like the resume: - {CONFIRM_SPRING_BOOT} </candidate_statements>", prompt)
                self.assertNotIn("<candidate_statements> What", rendered(family))

    def test_the_gaps_are_listed_and_asked_about_three_at_most(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                prompt = rendered(family, gaps=["Spring Boot", "Kafka"])
                self.assertIn("<gaps> Skills the posting asks for that neither the resume nor the candidate has mentioned: Spring Boot, Kafka </gaps>", prompt)
                self.assertIn("whether they have them, naming the ones that matter most for the posting, three at most", prompt)
                self.assertIn("`PostgreSQL` for a resume that says `Postgres` — is not a gap", prompt)
                self.assertNotIn("<gaps>", rendered(family))
                self.assertNotIn("three at most", rendered(family))

    def test_the_last_turn_asks_nothing_more(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                self.assertIn("Recommend nothing further and ask nothing", rendered(family, turns_left=1, gaps=["Kafka"]))

    def test_no_score_is_ever_mentioned(self) -> None:
        for family in AGENTS:
            with self.subTest(family=family):
                self.assertNotIn("score", rendered(family).lower())
        self.assertNotIn("score", rendered_judge().lower())


class EditPromptTest(unittest.TestCase):

    def test_it_names_what_may_change_and_nothing_else(self) -> None:
        prompt = rendered(EDIT)

        self.assertIn("The summary", prompt)
        self.assertIn("The description and the achievements of an entry of the work experience", prompt)
        self.assertIn("The skills, bucket by bucket", prompt)

    def test_it_forbids_reordering_and_the_hard_facts(self) -> None:
        prompt = rendered(EDIT)

        self.assertIn("Add, drop or reorder entries of the experience or the education", prompt)
        self.assertIn("Touch the education, the projects, the certifications or the languages", prompt)
        self.assertIn("Touch `personal_info`", prompt)

    def test_it_forbids_inventing_numbers_and_results(self) -> None:
        prompt = rendered(EDIT)

        self.assertIn("a date, a number, a team size, a result or a technology", prompt)
        self.assertIn("it never says more", prompt)

    def test_a_skill_keeps_the_wording_of_the_resume(self) -> None:
        prompt = rendered(EDIT)

        self.assertIn("`Postgres` stays `Postgres` for a posting that says", prompt)
        self.assertIn("never cover it", prompt)

    def test_it_names_the_schema_it_answers_with(self) -> None:
        self.assertIn("calling `EditProposal` once", rendered(EDIT))

    def test_a_confirmed_skill_goes_to_the_skills_and_into_an_entry_only_once_the_candidate_said_where(self) -> None:
        prompt = rendered(EDIT)

        self.assertIn("Guess where something the candidate confirmed belongs", prompt)
        self.assertIn("add it to the skills bucket it belongs in this turn", prompt)
        self.assertIn("ask in `reply`: in which job, and what they did with it", prompt)
        self.assertIn("A yes to a skill you asked about confirms that skill, and only that one", prompt)

    def test_what_the_candidate_said_in_their_language_enters_in_the_resumes(self) -> None:
        self.assertIn("the fact is theirs, the words are the resume's", rendered(EDIT))
        self.assertNotIn("the fact is theirs", rendered(EDIT, resume_language="es"))

    def test_the_previous_recommendations_are_listed_when_there_are_some(self) -> None:
        prompt = rendered(
            EDIT,
            recommendations=[
                {"id": 1, "section": "summary", "entry": "", "what": "Name Kubernetes"},
                {"id": 2, "section": "work_experience", "entry": "Backend Engineer at Acme", "what": "Say what ran there"},
            ],
        )

        self.assertIn("1. [summary] Name Kubernetes", prompt)
        self.assertIn("2. [work_experience: Backend Engineer at Acme] Say what ran there", prompt)
        self.assertNotIn("In the previous turn", rendered(EDIT))


class AdvisePromptTest(unittest.TestCase):

    def test_it_holds_no_way_to_edit_and_says_so(self) -> None:
        prompt = rendered(ADVISE)

        self.assertIn("you hold no way to change it", prompt)
        self.assertIn("never say, suggest or imply that you changed something", prompt)

    def test_the_recommendations_go_in_their_field_and_the_code_lists_them(self) -> None:
        prompt = rendered(ADVISE)

        self.assertIn("They go in `recommendations` only", prompt)
        self.assertIn("so `reply` never repeats", prompt)
        self.assertIn("calling `Advice` once", prompt)

    def test_a_gap_is_asked_about_and_never_recommended(self) -> None:
        self.assertIn("never recommend adding a skill that neither names", rendered(ADVISE))
        self.assertIn("A gap is a question, never a recommendation", rendered(ADVISE, gaps=["Kafka"]))
        self.assertNotIn("never a recommendation", rendered(ADVISE))

    def test_it_never_mentions_the_edit_schema(self) -> None:
        self.assertNotIn("EditProposal", rendered(ADVISE))

    def test_a_confirmed_skill_is_recommended_and_its_job_is_asked_for(self) -> None:
        prompt = rendered(ADVISE)

        self.assertIn("recommend the edits that bring it in", prompt)
        self.assertIn("When they have not said where, ask", prompt)
        self.assertIn("Never guess the entry", prompt)


class JudgePromptTest(unittest.TestCase):

    def test_it_approves_or_rejects_and_never_rewrites(self) -> None:
        prompt = rendered_judge()

        self.assertIn("You do not rewrite anything: you approve or reject, with a reason", prompt)
        self.assertIn("a change may only say what the candidate's own resume already supports", prompt)

    def test_it_names_every_kind_of_rejection(self) -> None:
        prompt = rendered_judge()

        for kind in ("language", "dropped_content", "reordered", "dropped_skill", "invented", "unsupported_skill", "wording", "padding"):
            self.assertIn(f"`kind: {kind}`", prompt)

    def test_the_posting_is_not_evidence_but_the_candidate_is(self) -> None:
        prompt = rendered_judge()

        self.assertIn("The resume and the candidate's words are the evidence. The posting is not", prompt)
        self.assertIn("What the candidate says is true", prompt)
        self.assertIn("A yes to a question CV Tailor asked about a skill confirms that skill, and only that one", prompt)

    def test_the_candidates_words_and_this_turn_travel_in_their_own_blocks(self) -> None:
        prompt = rendered_judge(statements=[CONFIRM_SPRING_BOOT], asked=ASK_ABOUT_SPRING_BOOT, message=YES)

        self.assertIn(f"<candidate_statements> - {CONFIRM_SPRING_BOOT} </candidate_statements>", prompt)
        self.assertIn(f"<this_turn> CV Tailor asked: {ASK_ABOUT_SPRING_BOOT} The candidate answered: {YES} </this_turn>", prompt)
        self.assertNotIn("<candidate_statements> -", rendered_judge())
        self.assertNotIn("<this_turn> CV", rendered_judge())

    def test_a_confirmed_skill_without_a_job_enters_the_skills_not_an_entry(self) -> None:
        self.assertIn("a skill the candidate confirmed without saying where enters the skills, not an entry", rendered_judge())

    def test_it_has_to_quote_the_resume_before_approving(self) -> None:
        prompt = rendered_judge()

        self.assertIn("Start the `evidence` of every part with its two counts", prompt)
        self.assertIn("The quote is copied from `<resume>`, `<candidate_statements>` or `<this_turn>`, word for word, never from `<proposed>`", prompt)
        self.assertIn("You may only approve a part whose `evidence` you quoted", prompt)
        self.assertIn("A technology listed in the `technologies` of an entry", prompt)

    def test_the_reason_is_written_for_the_candidate_in_their_language(self) -> None:
        self.assertIn("Write each `reason` in Spanish (es)", rendered_judge())
        self.assertIn("The resume is written in English (en). A proposed text in any other language", rendered_judge())

    def test_each_part_travels_with_its_original(self) -> None:
        prompt = rendered_judge()

        self.assertIn('<part id="summary" section="summary"> <original lines="1"> 1| Backend engineer. </original> <proposed lines="1"> 1| Backend engineer with 12 years. </proposed> </part>', prompt)

    def test_the_lines_of_an_entry_are_numbered_and_counted(self) -> None:
        prompt = rendered_judge(parts=[Part(
            "entry:0", "work_experience", "Backend Engineer at Acme",
            "Built services.\n- Ran Kubernetes.\nCut costs.", "Built services.\n- Ran Kubernetes.",
        )])

        self.assertIn('<original lines="3"> 1| Built services. 2| - Ran Kubernetes. 3| Cut costs. </original>', prompt)
        self.assertIn('<proposed lines="2"> 1| Built services. 2| - Ran Kubernetes. </proposed>', prompt)

    def test_the_skills_of_a_bucket_are_numbered_one_per_skill(self) -> None:
        prompt = rendered_judge(parts=[Part("skills:technical", "skills", "technical", "Java, Postgres", "Java, Postgres, Kubernetes")])

        self.assertIn('<original lines="2"> 1| Java 2| Postgres </original>', prompt)
        self.assertIn('<proposed lines="3"> 1| Java 2| Postgres 3| Kubernetes </proposed>', prompt)

    def test_the_resume_travels_without_personal_info(self) -> None:
        prompt = rendered_judge()

        self.assertIn('"summary": "Backend engineer with eight years', prompt)
        self.assertNotIn("Ada Lovelace", prompt)

    def test_the_ollama_variant_spells_the_json_fallback_out(self) -> None:
        self.assertIn('{"parts": [{"id": "summary", "evidence": "...", "ok": false', rendered_judge(provider="ollama"))
        self.assertNotIn('{"parts"', rendered_judge())
