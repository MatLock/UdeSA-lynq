from __future__ import annotations

import unittest

from tests.fixtures.spanish import CUT_ES, JUDGE_REASON_ES, MOVED_ES, UNBACKED_ES, REJECTED_SUMMARY_ES, REJECTION_OPENING_ES, UNKNOWN_ENTRY_ES

from agent.apply import CUT, MOVED, UNBACKED, UNKNOWN_ENTRY, Rejection
from agent.judge import UNJUDGED
from prompt.rejection import render


class RejectionNoticeTest(unittest.TestCase):

    def test_the_judges_reason_is_already_in_the_candidates_language(self) -> None:
        notice = render("es", [Rejection("summary", "", "invented", JUDGE_REASON_ES)])

        self.assertEqual(notice, f"{REJECTION_OPENING_ES} {REJECTED_SUMMARY_ES}, {JUDGE_REASON_ES}.")

    def test_the_codes_own_rejections_are_translated(self) -> None:
        notice = render("es", [Rejection("work_experience", "CTO at Initech", UNKNOWN_ENTRY, UNKNOWN_ENTRY)])

        self.assertIn(f"(CTO at Initech), {UNKNOWN_ENTRY_ES}", notice)
        self.assertNotIn(UNKNOWN_ENTRY, notice)

    def test_the_structure_guards_rejections_are_translated_and_their_detail_stays_out(self) -> None:
        notice = render("es", [
            Rejection("work_experience", "Backend Engineer at Acme", CUT, "the original has 3 lines and the proposal 2"),
            Rejection("skills", "technical", MOVED, "the bucket keeps its skills in their order"),
            Rejection("skills", "tools", UNBACKED, "the resume names these nowhere: Go"),
        ])

        self.assertIn(f"(Backend Engineer at Acme), {CUT_ES}", notice)
        self.assertIn(f"(technical), {MOVED_ES}", notice)
        self.assertIn(f"(tools), {UNBACKED_ES}", notice)
        self.assertNotIn("the original has", notice)

    def test_several_rejections_are_one_notice(self) -> None:
        notice = render(
            "en",
            [
                Rejection("summary", "", "invented", "The resume says eight years, not twelve."),
                Rejection("skills", "technical", UNJUDGED, UNJUDGED),
            ],
        )

        self.assertEqual(
            notice,
            "Part of what was proposed did not enter the resume: the summary, The resume says "
            "eight years, not twelve.; the skills (technical), the judge gave no verdict on it.",
        )

    def test_an_unknown_language_falls_back_to_english(self) -> None:
        self.assertTrue(render("pt", [Rejection("summary", "", "padding", "x")]).startswith("Part of what"))
