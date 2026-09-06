"""Tests for ``rich_text_blocks`` and the template macro built on it."""

from __future__ import annotations

import unittest

from model.resume_extractor import Resume
from renderer.resume_template import _env
from renderer.rich_text import rich_text_blocks


class RichTextBlocksTests(unittest.TestCase):
    """Prose is split into the blocks the candidate wrote into it."""

    def test_plain_text_is_a_single_paragraph(self) -> None:
        blocks = rich_text_blocks("Backend engineer with six years of experience.")

        self.assertEqual(
            [{"type": "paragraph", "text": "Backend engineer with six years of experience."}],
            blocks,
        )

    def test_each_line_of_a_bullet_run_becomes_its_own_entry(self) -> None:
        blocks = rich_text_blocks("- Cut p99 latency by 40%\n- Migrated the ledger to Kafka")

        self.assertEqual(
            [{
                "type": "list",
                "entries": ["Cut p99 latency by 40%", "Migrated the ledger to Kafka"],
            }],
            blocks,
        )

    def test_adjacent_bullets_collapse_into_one_list(self) -> None:
        blocks = rich_text_blocks("Led the team.\n- One\n- Two\nMentored two juniors.")

        self.assertEqual(
            [
                {"type": "paragraph", "text": "Led the team."},
                {"type": "list", "entries": ["One", "Two"]},
                {"type": "paragraph", "text": "Mentored two juniors."},
            ],
            blocks,
        )

    def test_every_bullet_glyph_a_resume_is_written_with_is_recognised(self) -> None:
        for glyph in ("-", "*", "•", "·", "–", "—", "▪", "●", "◦", "⁃"):
            with self.subTest(glyph=glyph):
                self.assertEqual(
                    [{"type": "list", "entries": ["Owned the ledger"]}],
                    rich_text_blocks(f"{glyph} Owned the ledger"),
                )

    def test_a_hyphenated_line_is_prose_not_a_bullet(self) -> None:
        # The marker is a glyph followed by a space; without it the line is text
        # that merely starts with a dash, and must not lose its first character.
        blocks = rich_text_blocks("-40% latency after the rewrite")

        self.assertEqual(
            [{"type": "paragraph", "text": "-40% latency after the rewrite"}], blocks
        )

    def test_blank_lines_separate_blocks_without_becoming_empty_paragraphs(self) -> None:
        blocks = rich_text_blocks("First.\n\n\nSecond.")

        self.assertEqual(
            [
                {"type": "paragraph", "text": "First."},
                {"type": "paragraph", "text": "Second."},
            ],
            blocks,
        )

    def test_a_bullet_with_nothing_after_the_glyph_is_dropped(self) -> None:
        blocks = rich_text_blocks("- Owned the ledger\n-   \n- Mentored two juniors")

        self.assertEqual(
            [{
                "type": "list",
                "entries": ["Owned the ledger", "Mentored two juniors"],
            }],
            blocks,
        )

    def test_empty_text_renders_nothing_at_all(self) -> None:
        for text in (None, "", "   ", "\n\n"):
            with self.subTest(text=text):
                self.assertEqual([], rich_text_blocks(text))


class RichTextTemplateTests(unittest.TestCase):
    """Both PDF templates render the structure instead of collapsing it."""

    DESCRIPTION = "Led the payments team.\n- Cut p99 latency by 40%\n- Migrated to Kafka"

    def _render(self, variant: str) -> str:
        resume = Resume.model_validate({
            "personal_info": {"full_name": "Jane Doe"},
            "summary": "Backend engineer.\n- Payments\n- Event-driven systems",
            "work_experience": [{
                "company": "LYNQ",
                "position": "Senior Backend Engineer",
                "description": self.DESCRIPTION,
            }],
        })
        return _env.get_template(f"{variant}/index.html").render(resume=resume, photo_url=None)

    def test_a_bulleted_description_becomes_a_list(self) -> None:
        for variant in ("classic", "modern"):
            with self.subTest(variant=variant):
                html = self._render(variant)

                self.assertIn('<p class="desc">Led the payments team.</p>', html)
                self.assertIn('<ul class="desc desc--list">', html)
                self.assertIn("<li>Cut p99 latency by 40%</li>", html)
                self.assertIn("<li>Migrated to Kafka</li>", html)
                # The whole thing on one line is exactly the bug this prevents.
                self.assertNotIn(self.DESCRIPTION, html)

    def test_a_bulleted_summary_becomes_a_list(self) -> None:
        for variant in ("classic", "modern"):
            with self.subTest(variant=variant):
                html = self._render(variant)

                self.assertIn('<ul class="summary summary--list">', html)
                self.assertIn("<li>Payments</li>", html)

    def test_the_prose_is_still_escaped(self) -> None:
        resume = Resume.model_validate({
            "personal_info": {"full_name": "Jane Doe"},
            "work_experience": [{
                "company": "LYNQ",
                "position": "Engineer",
                "description": "- Owned <script>alert(1)</script>",
            }],
        })

        html = _env.get_template("classic/index.html").render(resume=resume, photo_url=None)

        self.assertNotIn("<script>", html)
        self.assertIn("&lt;script&gt;", html)


if __name__ == "__main__":
    unittest.main()
