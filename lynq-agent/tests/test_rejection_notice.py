from __future__ import annotations

import unittest

from tests.fixtures.spanish import REJECTED_SKILL_ES, REJECTION_OPENING_ES

from agent import guard
from prompt.rejection import render

REASONS = (
    guard.UNBACKED_NUMBER,
    guard.UNBACKED_SKILL,
    guard.TOO_LONG,
    guard.LANGUAGE_MISMATCH,
    guard.UNKNOWN_ENTRY,
    guard.NO_EVIDENCE,
    guard.DROPPED_SKILL,
)


class RejectionNoticeTest(unittest.TestCase):

    def test_the_candidate_reads_the_rejection_in_their_language(self) -> None:
        notice = render("es", [guard.Rejection("skills", "technical", guard.NO_EVIDENCE, "Go")])

        self.assertTrue(notice.startswith(REJECTION_OPENING_ES))
        self.assertIn(f"(technical), {REJECTED_SKILL_ES}: Go", notice)
        self.assertNotIn(guard.NO_EVIDENCE, notice)

    def test_every_reason_of_the_guard_is_translated(self) -> None:
        for language in ("en", "es"):
            for reason in REASONS:
                with self.subTest(language=language, reason=reason):
                    notice = render(language, [guard.Rejection("summary", "", reason)])
                    self.assertNotIn(reason, notice.replace("not written in the language of the resume", ""))

    def test_several_rejections_are_one_notice(self) -> None:
        notice = render(
            "en",
            [
                guard.Rejection("summary", "", guard.UNBACKED_NUMBER, "12"),
                guard.Rejection("work_experience", "Backend Engineer at Acme", guard.UNBACKED_SKILL, "PostgreSQL"),
            ],
        )

        self.assertEqual(
            notice,
            "Part of what was proposed did not enter the resume: the summary, a number the "
            "resume does not back: 12; the experience (Backend Engineer at Acme), a skill of "
            "the posting the resume does not back: PostgreSQL.",
        )

    def test_an_unknown_language_falls_back_to_english(self) -> None:
        self.assertTrue(render("pt", [guard.Rejection("summary", "", guard.TOO_LONG)]).startswith("Part of what"))
