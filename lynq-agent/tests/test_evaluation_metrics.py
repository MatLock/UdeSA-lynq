from __future__ import annotations

import csv
import os
import tempfile
import unittest

from evaluation.annotation import ANNOTATION_COLUMNS, KEY_COLUMNS, Label, export, load_labels, load_runs
from evaluation.arms import GUARD, JUDGE, REACT
from evaluation.metrics import (
    cohen_kappa,
    inter_annotator,
    invalid_unit_rate,
    paired_bootstrap,
    render_markdown,
    summarize,
    system_agreement,
)


def unit(id_, applied=True, kind="ok", faults=()):
    return {"id": id_, "section": "summary", "label": "", "original": "a", "proposed": "b",
            "applied": applied, "system_kind": kind, "faults": list(faults)}


def record(arm, pair, turn=1, units=(), rejected=(), faults=(), error=None, cost="0.01", calls=2):
    return {
        "key": f"{pair}|s|{arm}|{turn}", "pair": pair, "script": "s", "arm": arm, "turn": turn,
        "language": "es", "resume_language": "es", "candidate_said": ["hi"], "job_skills": ["X"],
        "error": error, "intent": "edit", "units": list(units), "rejected": list(rejected),
        "faults": list(faults), "changes": [1] if units else [], "model_calls": calls, "tool_calls": 0,
        "tool_rejections": 0, "passes": 1, "cost_usd": cost, "latency_ms": 100, "wall_ms": 120,
        "gaps_before": ["X", "Y"], "gaps_after": ["Y"] if units else ["X", "Y"],
    }


RECORDS = [
    record(REACT, "p1", units=[unit("r1"), unit("r2")], faults=["entries_reordered"], cost="0.03", calls=6),
    record(REACT, "p2", units=[unit("r3")]),
    record(JUDGE, "p1", units=[unit("j1")], rejected=[unit("j2", applied=False, kind="invented")]),
    record(JUDGE, "p2", units=[unit("j3")], rejected=[unit("j4", applied=False, kind="padding")]),
    record(GUARD, "p1", units=[unit("g1")]),
    record(GUARD, "p2", error="boom"),
]
LABELS = {
    "r1": Label("r1", "invented"), "r2": Label("r2", "valid"), "r3": Label("r3", "reordered"),
    "j1": Label("j1", "valid"), "j2": Label("j2", "invented"), "j3": Label("j3", "valid"), "j4": Label("j4", "valid"),
    "g1": Label("g1", "valid"),
}


class SummaryTest(unittest.TestCase):

    def test_the_summary_counts_what_the_code_can_count(self) -> None:
        summary = summarize(RECORDS)

        self.assertEqual(list(summary), [REACT, GUARD, JUDGE])
        self.assertEqual(summary[REACT]["applied_units"], 3)
        self.assertEqual(summary[REACT]["faults_by_kind"], {"entries_reordered": 1})
        self.assertEqual(summary[JUDGE]["rejected_by_kind"], {"invented": 1, "padding": 1})
        self.assertEqual(summary[GUARD]["failed_turns"], 1)
        self.assertAlmostEqual(summary[REACT]["cost_usd_per_turn"], 0.02)
        self.assertAlmostEqual(summary[REACT]["gap_coverage_mean"], 0.5)

    def test_labels_add_the_invalid_rates(self) -> None:
        summary = summarize(RECORDS, LABELS)

        self.assertAlmostEqual(summary[REACT]["invalid_rate"], 2 / 3)
        self.assertEqual(summary[REACT]["invalid_by_kind"], {"invented": 1, "reordered": 1})
        self.assertEqual(summary[JUDGE]["invalid_rate"], 0.0)
        self.assertEqual(summary[JUDGE]["wrongly_rejected"], 1)
        self.assertEqual(summary[REACT]["edit_turns_with_invalid"], 2)

    def test_the_paired_bootstrap_compares_the_same_conversations(self) -> None:
        result = paired_bootstrap(RECORDS, LABELS, JUDGE, REACT, invalid_unit_rate, resamples=200)

        self.assertEqual(result["conversations"], 2)
        self.assertAlmostEqual(result["difference"], 0 - 2 / 3)
        low, high = result["ci95"]
        self.assertLessEqual(low, result["difference"])
        self.assertGreaterEqual(high, result["difference"])
        self.assertIsNone(paired_bootstrap(RECORDS, LABELS, JUDGE, "nope", invalid_unit_rate))

    def test_the_judge_is_scored_against_the_human(self) -> None:
        agreement = system_agreement(RECORDS, LABELS, JUDGE)

        self.assertEqual(agreement["parts"], 4)
        self.assertEqual(agreement["true_rejections"], 1)
        self.assertEqual(agreement["false_rejections"], 1)
        self.assertEqual(agreement["missed_invalid"], 0)
        self.assertEqual(agreement["precision"], 0.5)
        self.assertEqual(agreement["recall"], 1.0)

    def test_kappa(self) -> None:
        self.assertEqual(cohen_kappa(["a", "b", "a"], ["a", "b", "a"]), 1.0)
        self.assertAlmostEqual(cohen_kappa(["a", "a", "b", "b"], ["a", "b", "a", "b"]), 0.0)
        self.assertIsNone(cohen_kappa([], []))
        two = inter_annotator(LABELS, {**LABELS, "r2": Label("r2", "padding")})
        self.assertEqual(two["units"], 8)
        self.assertLess(two["agreement_kind"], 1.0)

    def test_the_markdown_has_one_column_per_arm(self) -> None:
        text = render_markdown(summarize(RECORDS, LABELS), [], [system_agreement(RECORDS, LABELS, JUDGE)])

        self.assertIn("| Metric | react | guard | judge |", text)
        self.assertIn("Invalid rate over applied parts", text)
        self.assertIn("| judge | 4 |", text)


class AnnotationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.directory = tempfile.mkdtemp()
        self.sheet = os.path.join(self.directory, "sheet.csv")
        self.key = os.path.join(self.directory, "key.csv")

    def test_the_sheet_hides_the_arm_and_the_key_keeps_it(self) -> None:
        rows = export(RECORDS, self.sheet, self.key)

        self.assertEqual(rows, 8)
        with open(self.sheet, encoding="utf-8", newline="") as file:
            sheet = list(csv.DictReader(file))
        with open(self.key, encoding="utf-8", newline="") as file:
            key = list(csv.DictReader(file))
        self.assertEqual(tuple(sheet[0]), ANNOTATION_COLUMNS)
        self.assertEqual(tuple(key[0]), KEY_COLUMNS)
        self.assertNotIn("arm", sheet[0])
        self.assertEqual({row["arm"] for row in key}, {REACT, JUDGE, GUARD})
        self.assertEqual([row["unit_id"] for row in sheet], [row["unit_id"] for row in key])

    def test_labels_load_and_a_typo_is_refused(self) -> None:
        export(RECORDS, self.sheet, self.key)
        with open(self.sheet, encoding="utf-8", newline="") as file:
            rows = list(csv.DictReader(file))
        rows[0]["label"] = "Valid"
        rows[1]["label"] = "invented"
        with open(self.sheet, "w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=ANNOTATION_COLUMNS)
            writer.writeheader()
            writer.writerows(rows)

        labels = load_labels(self.sheet)

        self.assertEqual(len(labels), 2)
        self.assertTrue(labels[rows[0]["unit_id"]].valid)

        rows[2]["label"] = "invnted"
        with open(self.sheet, "w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=ANNOTATION_COLUMNS)
            writer.writeheader()
            writer.writerows(rows)
        with self.assertRaises(ValueError):
            load_labels(self.sheet)

    def test_a_rerun_key_keeps_its_last_record(self) -> None:
        path = os.path.join(self.directory, "runs.jsonl")
        import json
        with open(path, "w", encoding="utf-8") as file:
            file.write(json.dumps(record(GUARD, "p9", units=[unit("a")])) + "\n")
            file.write(json.dumps(record(GUARD, "p9", units=[unit("b")])) + "\n")

        [only] = load_runs(path)

        self.assertEqual(only["units"][0]["id"], "b")
