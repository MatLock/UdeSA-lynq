"""Runs the scripted conversations of the corpus through the arms and appends
one JSON line per turn to the output. Resumable: turns already in the output
are skipped. The models come from the environment, the same variables the
service reads (LLM_PROVIDER, BEDROCK_MODEL_ID, BEDROCK_INTENT_MODEL_ID,
BEDROCK_JUDGE_MODEL_ID, or OLLAMA_MODEL...), so an arm runs on exactly the
model the service would run on.

    LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 \\
    python scripts/run_eval.py --arms react guard judge --output evaluation/runs/pilot.jsonl
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src"))

from evaluation.arms import ARMS  # noqa: E402
from evaluation.corpus import load_pairs, load_scripts  # noqa: E402
from evaluation.runner import Models, plan_run, run_all  # noqa: E402

_MODULE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_PAIRS = os.path.join(_MODULE_ROOT, "evaluation", "corpus", "pairs.jsonl")
DEFAULT_SCRIPTS = os.path.join(_MODULE_ROOT, "evaluation", "corpus", "scripts.json")


def parse(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pairs", default=DEFAULT_PAIRS, help="JSONL of (job, base_resume) pairs")
    parser.add_argument("--scripts", default=DEFAULT_SCRIPTS, help="JSON of scripted conversations")
    parser.add_argument("--arms", nargs="+", default=list(ARMS), choices=ARMS)
    parser.add_argument("--output", required=True, help="JSONL the turns are appended to")
    parser.add_argument("--only-pairs", nargs="*", default=None, help="pair ids to run")
    parser.add_argument("--only-scripts", nargs="*", default=None, help="script ids to run")
    parser.add_argument("--limit", type=int, default=None, help="at most this many pairs")
    parser.add_argument("--parallel", type=int, default=1, help="conversations in flight at once")
    parser.add_argument("--timeout", type=int, default=None, help="seconds per turn")
    parser.add_argument("--dry-run", action="store_true", help="print the plan and stop")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    arguments = parse(argv)
    pairs = load_pairs(arguments.pairs)
    scripts = load_scripts(arguments.scripts)
    if arguments.only_pairs:
        pairs = [p for p in pairs if p.id in set(arguments.only_pairs)]
    if arguments.only_scripts:
        scripts = [s for s in scripts if s.id in set(arguments.only_scripts)]
    if arguments.limit:
        pairs = pairs[: arguments.limit]

    plan = plan_run(pairs, scripts, arguments.arms)
    turns = sum(len(entry[3]) for entry in plan.conversations)
    print(f"{len(pairs)} pairs x {len(scripts)} scripts x {len(arguments.arms)} arms: "
          f"{len(plan.conversations)} conversations, {turns} turns; skipped {len(plan.skipped)} unfillable")
    for pair_id, script_id in plan.skipped:
        print(f"  skipped {pair_id} / {script_id}")
    if arguments.dry_run:
        return 0

    models = Models.from_settings()
    print(f"models: {models.names}")
    written = asyncio.run(run_all(
        plan, arguments.output, models, timeout=arguments.timeout, parallel=arguments.parallel
    ))
    print(f"wrote {written} turns to {arguments.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
