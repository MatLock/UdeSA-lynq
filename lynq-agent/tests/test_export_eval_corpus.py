from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest
from unittest.mock import patch

from tests.support import (
    RESUME,
    TemporaryDatabase,
    new_conversation,
    new_resume_version,
)

from db.models import ConversationStatus

_SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "scripts",
    "export_eval_corpus.py",
)


def load_script():
    specification = importlib.util.spec_from_file_location("export_eval_corpus", _SCRIPT)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class AnonymisationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.script = load_script()

    def test_the_same_candidate_hashes_the_same_way_under_one_salt(self) -> None:
        first = self.script.anonymize_user("user-1", "salt")
        again = self.script.anonymize_user("user-1", "salt")

        self.assertEqual(first, again)

    def test_a_different_salt_gives_a_different_hash(self) -> None:
        self.assertNotEqual(
            self.script.anonymize_user("user-1", "salt"),
            self.script.anonymize_user("user-1", "other-salt"),
        )

    def test_the_hash_does_not_carry_the_user_id(self) -> None:
        self.assertNotIn("user-1", self.script.anonymize_user("user-1", "salt"))

    def test_contact_details_are_dropped_from_the_resume(self) -> None:
        stripped = self.script.strip_identity(RESUME)

        self.assertNotIn("personal_info", stripped)
        self.assertIn("summary", stripped)


class ExportTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        self.script = load_script()
        self.database = TemporaryDatabase()
        await self.database.create_schema()
        self.output = os.path.join(tempfile.mkdtemp(), "corpus.jsonl")

    async def asyncTearDown(self) -> None:
        await self.database.dispose()

    async def _store(self, *rows) -> None:
        async with self.database.session_factory() as session:
            session.add_all(rows)
            await session.commit()

    async def _export(self, only_applied: bool = False) -> list[dict]:
        with patch.object(
            self.script, "get_session_factory", return_value=self.database.session_factory
        ):
            with patch.object(self.script, "dispose_engine"):
                await self.script.export(self.output, "salt", only_applied)

        with open(self.output, "r", encoding="utf-8") as corpus:
            return [json.loads(line) for line in corpus]

    async def test_a_tailored_conversation_becomes_one_triple(self) -> None:
        conversation = new_conversation(status=ConversationStatus.APPLIED)
        await self._store(conversation, new_resume_version(conversation, version=1))

        rows = await self._export()

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["job"]["title"], "Senior Backend Engineer")
        self.assertIn("tailored_resume", rows[0])

    async def test_the_exported_triple_carries_no_contact_details(self) -> None:
        conversation = new_conversation(status=ConversationStatus.APPLIED)
        await self._store(conversation, new_resume_version(conversation, version=1))

        row = (await self._export())[0]

        self.assertNotIn("personal_info", row["base_resume"])
        self.assertNotIn("personal_info", row["tailored_resume"])
        self.assertNotEqual(row["candidate"], "user-1")

    async def test_a_conversation_that_never_produced_a_version_is_skipped(self) -> None:
        conversation = new_conversation(status=ConversationStatus.ABANDONED)
        await self._store(conversation, new_resume_version(conversation, version=0))

        self.assertEqual(await self._export(), [])

    async def test_only_applied_keeps_the_conversations_that_ended_in_an_application(self) -> None:
        applied = new_conversation(status=ConversationStatus.APPLIED)
        exhausted = new_conversation(status=ConversationStatus.EXHAUSTED)
        await self._store(
            applied,
            new_resume_version(applied, version=1),
            exhausted,
            new_resume_version(exhausted, version=1),
        )

        rows = await self._export(only_applied=True)

        self.assertEqual([row["status"] for row in rows], [ConversationStatus.APPLIED])

    async def test_both_languages_travel_with_the_triple(self) -> None:
        conversation = new_conversation(status=ConversationStatus.APPLIED)
        await self._store(conversation, new_resume_version(conversation, version=1))

        row = (await self._export())[0]

        self.assertEqual(row["language"], "es")
        self.assertEqual(row["resume_language"], "en")


class CommandLineTest(unittest.TestCase):

    def setUp(self) -> None:
        self.script = load_script()

    def test_exporting_without_a_salt_is_refused(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            with patch("sys.argv", ["export_eval_corpus.py", "corpus.jsonl"]):
                with self.assertRaises(SystemExit):
                    self.script.main()

    def test_the_salt_comes_from_the_environment(self) -> None:
        environment = {self.script.SALT_ENV: "salt"}
        with patch.dict("os.environ", environment, clear=True):
            with patch("sys.argv", ["export_eval_corpus.py", "corpus.jsonl"]):
                with patch.object(self.script, "export", return_value=0) as export:
                    with patch.object(self.script.asyncio, "run", return_value=0):
                        with contextlib.redirect_stdout(io.StringIO()):
                            self.script.main()

        expected = os.path.join(os.path.realpath(os.getcwd()), "corpus.jsonl")
        export.assert_called_once_with(expected, "salt", False)

    def test_the_corpus_cannot_be_written_outside_the_working_directory(self) -> None:
        environment = {self.script.SALT_ENV: "salt"}
        with patch.dict("os.environ", environment, clear=True):
            with patch("sys.argv", ["export_eval_corpus.py", "../../corpus.jsonl"]):
                with self.assertRaises(SystemExit):
                    with contextlib.redirect_stderr(io.StringIO()):
                        self.script.main()

    def test_the_corpus_is_written_where_the_command_line_says(self) -> None:
        destination = os.path.join(os.path.realpath(os.getcwd()), "corpus.jsonl")
        environment = {self.script.SALT_ENV: "salt"}
        with patch.dict("os.environ", environment, clear=True):
            with patch("sys.argv", ["export_eval_corpus.py", "corpus.jsonl"]):
                with patch.object(self.script.asyncio, "run", return_value=7) as run:
                    with contextlib.redirect_stdout(io.StringIO()) as printed:
                        self.script.main()

        run.assert_called_once()
        self.assertIn(destination, printed.getvalue())
        self.assertIn("7 triples", printed.getvalue())

    def test_a_relative_destination_resolves_inside_the_working_directory(self) -> None:
        resolved = self.script.safe_output_path("out/corpus.jsonl")

        self.assertEqual(
            resolved,
            os.path.join(os.path.realpath(os.getcwd()), "out", "corpus.jsonl"),
        )


if __name__ == "__main__":
    unittest.main()
