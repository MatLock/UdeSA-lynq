from __future__ import annotations

import glob
import os
import re
import unittest

from tests.support import TemporaryDatabase  # noqa: F401

from db.models import Base

_CHANGELOG_DDL = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "changelog", "ddl"
)
_CREATE_TABLE = re.compile(
    r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\);", re.DOTALL
)
_NOT_A_COLUMN = ("CONSTRAINT", "INDEX", "PRIMARY", "UNIQUE", "FOREIGN", "KEY")


def changelog_tables() -> dict[str, set[str]]:
    tables: dict[str, set[str]] = {}
    for path in sorted(glob.glob(os.path.join(_CHANGELOG_DDL, "*.sql"))):
        with open(path, "r", encoding="utf-8") as changeset:
            for name, body in _CREATE_TABLE.findall(changeset.read()):
                tables[name] = {
                    line.split()[0]
                    for line in (raw.strip() for raw in body.splitlines())
                    if line and not line.upper().startswith(_NOT_A_COLUMN)
                }
    return tables


class SchemaParityTest(unittest.TestCase):

    def setUp(self) -> None:
        self.changelog = changelog_tables()
        self.models = {
            name: {column.name for column in table.columns}
            for name, table in Base.metadata.tables.items()
        }

    def test_the_changelog_creates_the_four_tables_of_the_plan(self) -> None:
        self.assertEqual(
            set(self.changelog),
            {"conversation", "message", "resume_version", "trace_span"},
        )

    def test_every_model_has_its_table_in_the_changelog(self) -> None:
        self.assertEqual(set(self.models), set(self.changelog))

    def test_no_column_drifted_between_the_models_and_the_changelog(self) -> None:
        for table in sorted(self.models):
            with self.subTest(table=table):
                self.assertEqual(self.models[table], self.changelog[table])

    def test_the_conversation_keeps_what_makes_a_turn_safe_and_reproducible(self) -> None:
        self.assertLessEqual(
            {
                "run_token",
                "resume_language",
                "job_snapshot",
                "base_resume",
                "input_price_per_1m",
                "output_price_per_1m",
                "max_turns",
                "max_steps",
                "cost_usd",
            },
            self.changelog["conversation"],
        )


if __name__ == "__main__":
    unittest.main()
