from __future__ import annotations

import argparse
import json
import socket
import sys
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

from seed.loader import STEPS, Loader
from seed.lynq import LynqApi, LynqApiError
from seed.settings import SeedConfigError, Settings

CORPUS_PATH = Path(__file__).resolve().parent.parent / "corpus.json"


def log(message: str) -> None:
    print(f"{datetime.now():%H:%M:%S} {message}", flush=True)


def steps_argument(value: str) -> tuple[str, ...]:
    chosen = tuple(part.strip() for part in value.split(",") if part.strip())
    unknown = [part for part in chosen if part not in STEPS]
    if unknown:
        raise argparse.ArgumentTypeError(f"unknown steps {unknown}; choose from {', '.join(STEPS)}")
    return chosen


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m seed", description="Load the synthetic demo corpus into Lynq")
    parser.add_argument("--corpus", type=Path, default=CORPUS_PATH)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check", help="check that the services answer and the environment is set")
    load = commands.add_parser("load", help="load the corpus through the Lynq endpoints")
    load.add_argument("--steps", type=steps_argument, default=STEPS)
    load.add_argument("--refresh-externals", action="store_true",
                      help="enhance and ingest every external job post again, even the ones already in Lynq")
    replay = commands.add_parser("replay", help="replay the backend events and start the analytics snapshot")
    replay.add_argument("--no-snapshot", action="store_true")
    return parser


def reachable(url: str) -> bool:
    parsed = urlparse(url)
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    try:
        with socket.create_connection((parsed.hostname, port), timeout=2):
            return True
    except OSError:
        return False


def read_corpus(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def command_check(settings: Settings, args: argparse.Namespace) -> int:
    failures = 0
    for name, url in {**settings.service_urls(), **settings.dependency_urls()}.items():
        up = reachable(url)
        failures += 0 if up else 1
        log(f"{'ok  ' if up else 'DOWN'} {name:<17} {url}")
    for name, getter in (("LYNQ_INTERNAL_TOKEN", settings.internal_token),
                         ("LYNQ_SEED_PASSWORD", settings.seed_password)):
        try:
            getter()
            log(f"ok   {name} is set")
        except SeedConfigError as error:
            failures += 1
            log(f"MISS {error}")
    corpus = read_corpus(args.corpus)
    log(f"ok   corpus {args.corpus.name}: {corpus['meta']['counts']}")
    return 1 if failures else 0


def command_load(settings: Settings, args: argparse.Namespace) -> int:
    corpus = read_corpus(args.corpus)
    password = settings.seed_password()
    settings.internal_token()
    log(f"corpus: {corpus['meta']['counts']}")
    loader = Loader(LynqApi(settings), password, corpus, log, refresh_externals=args.refresh_externals)
    report = loader.run(tuple(args.steps))
    log("load finished\n" + report.summary())
    demo = corpus.get("demo") or {}
    for label, candidate in (demo.get("candidates") or {}).items():
        log(f"demo {label}: {candidate['account']} ({candidate['full_name']}, {candidate['position']})")
    if demo.get("company"):
        log(f"demo company: {demo['company']['account']} ({demo['company']['name']}); "
            f"most applied job post {demo.get('popular_job')}, job post with no applicants {demo.get('empty_job')}")
    return 1 if report.errors else 0


def command_replay(settings: Settings, args: argparse.Namespace) -> int:
    api = LynqApi(settings)
    log(f"replay: {api.replay_events()}")
    if not args.no_snapshot:
        try:
            api.snapshot()
            log("analytics snapshot started; it runs in the background inside lynq-analytics")
        except LynqApiError as error:
            if error.status != 409:
                raise
            log("an analytics snapshot is already running")
    return 0


COMMANDS = {"check": command_check, "load": command_load, "replay": command_replay}


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    settings = Settings()
    try:
        settings.ensure_local()
        return COMMANDS[args.command](settings, args)
    except (SeedConfigError, LynqApiError, FileNotFoundError) as error:
        log(f"error: {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
