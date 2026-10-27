from __future__ import annotations

import os
import unittest

from tests.fixtures.spanish import EVAL_SCRIPT_TURN, EVAL_SCRIPT_TURN_FILLED

from evaluation.corpus import Pair, Script, fill, load_pairs, load_scripts, placeholders

_MODULE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAIRS = os.path.join(_MODULE_ROOT, "evaluation", "corpus", "pairs.jsonl")
SCRIPTS = os.path.join(_MODULE_ROOT, "evaluation", "corpus", "scripts.json")

JOB = {"id": "j", "title": "Analyst", "extractedSkills": ["Excel", "SAP", "Power BI"]}
RESUME = {
    "summary": "Analyst with Excel.",
    "work_experience": [
        {"company": "Acme", "position": "Analyst", "description": "Reports in Excel."},
        {"company": "Globex", "position": "Assistant", "description": "Filing."},
    ],
    "skills": {"technical": ["Excel"], "tools": [], "soft": []},
}


class PlaceholderTest(unittest.TestCase):

    def test_the_gaps_are_the_posting_skills_the_resume_does_not_back(self) -> None:
        pair = Pair(id="p", job=JOB, base_resume=RESUME)

        self.assertEqual(pair.gaps, ["SAP", "Power BI"])
        values = placeholders(pair)
        self.assertEqual(values["gap1"], "SAP")
        self.assertEqual(values["gap2"], "Power BI")
        self.assertEqual(values["company1"], "Acme")
        self.assertEqual(values["position1"], "Analyst")
        self.assertEqual(values["company2"], "Globex")

    def test_a_script_is_filled_with_the_pair_values(self) -> None:
        script = Script(id="s", turns=[EVAL_SCRIPT_TURN])

        self.assertEqual(fill(script, Pair(id="p", job=JOB, base_resume=RESUME)), [EVAL_SCRIPT_TURN_FILLED])

    def test_a_script_the_pair_cannot_fill_is_skipped(self) -> None:
        script = Script(id="s", turns=["Add {gap3}."])

        self.assertIsNone(fill(script, Pair(id="p", job=JOB, base_resume=RESUME)))

    def test_a_script_with_no_placeholders_fits_every_pair(self) -> None:
        script = Script(id="s", turns=["Tailor it."])

        self.assertEqual(fill(script, Pair(id="p", job=JOB, base_resume={})), ["Tailor it."])


class CorpusFilesTest(unittest.TestCase):
    """The committed corpus is data the thesis depends on; these keep it usable."""

    def test_every_pair_loads_without_personal_data(self) -> None:
        pairs = load_pairs(PAIRS)

        self.assertGreaterEqual(len(pairs), 8)
        for pair in pairs:
            self.assertNotIn("personal_info", pair.base_resume, pair.id)
            self.assertTrue(pair.job_skills, pair.id)
            self.assertTrue(pair.entries, pair.id)

    def test_every_pair_fills_every_script(self) -> None:
        pairs, scripts = load_pairs(PAIRS), load_scripts(SCRIPTS)

        self.assertEqual({s.id for s in scripts}, {"tailor", "gap", "paraphrase", "adversarial"})
        for pair in pairs:
            for script in scripts:
                self.assertIsNotNone(fill(script, pair), f"{pair.id} cannot fill {script.id}")

    def test_every_pair_has_at_least_two_gaps(self) -> None:
        for pair in load_pairs(PAIRS):
            self.assertGreaterEqual(len(pair.gaps), 2, pair.id)
