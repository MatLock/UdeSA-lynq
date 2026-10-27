"""The tables of the evaluation: one column per arm, the paired differences
with their bootstrap intervals, the judge and the guard against the human, and
the agreement between two annotators when two filled sheets are given.

    python scripts/eval_metrics.py evaluation/runs/pilot.jsonl \\
        --labels evaluation/runs/pilot-annotation-A.csv [evaluation/runs/pilot-annotation-B.csv] \\
        --json evaluation/runs/pilot-metrics.json

Without --labels only what the code can count is reported: structural faults,
gap coverage, calls, cost and latency.
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src"))

from evaluation.annotation import load_labels, load_runs  # noqa: E402
from evaluation.arms import GUARD, JUDGE, REACT  # noqa: E402
from evaluation.metrics import (  # noqa: E402
    STATISTICS,
    as_json,
    inter_annotator,
    paired_bootstrap,
    render_markdown,
    summarize,
    system_agreement,
)

COMPARISONS = ((JUDGE, REACT), (JUDGE, GUARD), (GUARD, REACT))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("runs", help="JSONL written by run_eval.py")
    parser.add_argument("--labels", nargs="*", default=[], help="one or two filled annotation sheets")
    parser.add_argument("--resamples", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--json", default=None, help="also write every number here")
    arguments = parser.parse_args(argv)

    records = load_runs(arguments.runs)
    sheets = [load_labels(path) for path in arguments.labels]
    labels = sheets[0] if sheets else None

    summary = summarize(records, labels)
    comparisons, agreements, annotators = [], [], None
    if labels is not None:
        for name, statistic in STATISTICS.items():
            for a, b in COMPARISONS:
                result = paired_bootstrap(records, labels, a, b, statistic, arguments.resamples, arguments.seed)
                if result is not None:
                    comparisons.append({"statistic": name, **result})
        for arm in (JUDGE, GUARD):
            result = system_agreement(records, labels, arm)
            if result is not None:
                agreements.append(result)
        if len(sheets) == 2:
            annotators = inter_annotator(sheets[0], sheets[1])
    else:
        for a, b in COMPARISONS:
            for name in ("faulty_turn_rate", "cost_per_turn"):
                result = paired_bootstrap(records, {}, a, b, STATISTICS[name], arguments.resamples, arguments.seed)
                if result is not None:
                    comparisons.append({"statistic": name, **result})

    print(render_markdown(summary, comparisons, agreements, annotators))
    if arguments.json:
        with open(arguments.json, "w", encoding="utf-8") as file:
            file.write(as_json({
                "summary": summary, "comparisons": comparisons,
                "agreements": agreements, "annotators": annotators,
            }))
        print(f"numbers written to {arguments.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
