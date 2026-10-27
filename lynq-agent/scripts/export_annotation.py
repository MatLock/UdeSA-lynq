"""Turns a run into the sheet the annotators fill, plus the key that maps every
row back to its arm. Give the annotators the sheet only.

    python scripts/export_annotation.py evaluation/runs/pilot.jsonl \\
        --sheet evaluation/runs/pilot-annotation.csv --key evaluation/runs/pilot-key.csv
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src"))

from evaluation.annotation import export, load_runs  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("runs", help="JSONL written by run_eval.py")
    parser.add_argument("--sheet", required=True, help="CSV for the annotators")
    parser.add_argument("--key", required=True, help="CSV that maps unit ids to arms; keep it apart")
    parser.add_argument("--seed", type=int, default=7, help="shuffle seed")
    arguments = parser.parse_args(argv)

    rows = export(load_runs(arguments.runs), arguments.sheet, arguments.key, seed=arguments.seed)
    print(f"{rows} units to annotate in {arguments.sheet}; key in {arguments.key}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
