from __future__ import annotations

import os
import unittest

_MODULE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ROOTS = ("src", "tests", "resources", "scripts", "docs/queries.sql")
SUFFIXES = (".py", ".jinja", ".sql")

EXEMPT = (
    os.path.join("resources", "greetings", "es.jinja"),
    os.path.join("resources", "refusals", "es.jinja"),
    os.path.join("tests", "fixtures"),
    os.path.join("tests", "test_english_only.py"),
)

MARKERS = (
    " el ",
    " la ",
    " que ",
    " para ",
    " con ",
    "ción",
    "ñ",
    "á",
    "é",
    "í",
    "ó",
    "ú",
)


def _exempt(relative: str) -> bool:
    return any(relative.startswith(prefix) for prefix in EXEMPT)


def scanned_files() -> list[str]:
    found: list[str] = []
    for root in ROOTS:
        absolute = os.path.join(_MODULE_ROOT, root)
        if os.path.isfile(absolute):
            found.append(root)
            continue
        for directory, subdirectories, names in os.walk(absolute):
            subdirectories[:] = [
                name for name in subdirectories if name != "__pycache__"
            ]
            for name in names:
                if not name.endswith(SUFFIXES):
                    continue
                path = os.path.relpath(os.path.join(directory, name), _MODULE_ROOT)
                if not _exempt(path):
                    found.append(path)
    return sorted(found)


def spanish_in(path: str) -> list[str]:
    with open(os.path.join(_MODULE_ROOT, path), encoding="utf-8") as file:
        lines = file.read().splitlines()
    return [
        f"{path}:{number}: {line.strip()}"
        for number, line in enumerate(lines, 1)
        for marker in MARKERS
        if marker in line.lower()
    ]


class EnglishOnlyTest(unittest.TestCase):

    def test_the_code_the_prompts_and_the_queries_are_written_in_english(self) -> None:
        offenders = [line for path in scanned_files() for line in spanish_in(path)]

        self.assertEqual(
            offenders,
            [],
            "Spanish belongs in resources/greetings/es.jinja and tests/fixtures only",
        )

    def test_it_looks_at_the_prompts_the_sources_and_the_queries(self) -> None:
        scanned = scanned_files()

        self.assertIn(os.path.join("src", "agent", "graph.py"), scanned)
        self.assertIn(
            os.path.join("resources", "prompts", "resume_tailor", "bedrock.jinja"),
            scanned,
        )
        self.assertIn(os.path.join("resources", "greetings", "en.jinja"), scanned)
        self.assertIn(os.path.join("docs", "queries.sql"), scanned)

    def test_the_two_places_spanish_is_allowed_stay_out_of_the_scan(self) -> None:
        scanned = scanned_files()

        self.assertNotIn(os.path.join("resources", "greetings", "es.jinja"), scanned)
        self.assertNotIn(os.path.join("tests", "fixtures", "spanish.py"), scanned)

    def test_the_markers_do_find_spanish_when_there_is_any(self) -> None:
        self.assertTrue(
            spanish_in(os.path.join("tests", "fixtures", "spanish.py")),
            "the scanner must flag the fixtures it is told to skip",
        )
