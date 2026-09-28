from __future__ import annotations

import unittest

from tests.fixtures.spanish import JUDGE_REASON_ES, REJECTED_SUMMARY_ES, REJECTION_OPENING_ES, UNKNOWN_ENTRY_ES

from agent.apply import UNKNOWN_ENTRY, Rejection
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
