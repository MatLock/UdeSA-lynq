from __future__ import annotations

import subprocess
import unittest
from unittest.mock import patch

from tests.support import base_resume  # noqa: F401

from db.migrations import (
    MigrationError,
    _update_blocking,
    jdbc_target,
    liquibase_executable,
    update_to_latest,
)

COMPOSE_URL = "mysql+aiomysql://root:testpassword123@mysql:3306/lynq_agent_db"


class JdbcTargetTest(unittest.TestCase):

    def test_the_sqlalchemy_url_becomes_a_jdbc_url_with_split_credentials(self):
        target = jdbc_target(COMPOSE_URL)

        self.assertEqual(target.url, "jdbc:mysql://mysql:3306/lynq_agent_db")
        self.assertEqual(target.username, "root")
        self.assertEqual(target.password, "testpassword123")

    def test_a_missing_port_falls_back_to_the_mysql_default(self):
        target = jdbc_target("mysql+aiomysql://root:secret@mysql/lynq_agent_db")

        self.assertEqual(target.url, "jdbc:mysql://mysql:3306/lynq_agent_db")

    def test_a_percent_encoded_password_is_decoded_for_the_driver(self):
        target = jdbc_target("mysql+aiomysql://root:p%40ss%3Aword@mysql:3306/db")

        self.assertEqual(target.password, "p@ss:word")

    def test_connection_options_survive_the_translation(self):
        target = jdbc_target(COMPOSE_URL + "?useSSL=false")

        self.assertEqual(
            target.url, "jdbc:mysql://mysql:3306/lynq_agent_db?useSSL=false"
        )

    def test_a_url_without_a_database_is_rejected(self):
        with self.assertRaises(MigrationError):
            jdbc_target("mysql+aiomysql://root:secret@mysql:3306/")

    def test_a_url_without_a_host_is_rejected(self):
        with self.assertRaises(MigrationError):
            jdbc_target("mysql+aiomysql:///lynq_agent_db")


class LiquibaseExecutableTest(unittest.TestCase):

    def test_the_image_installation_is_preferred(self):
        with patch.dict("os.environ", {"LIQUIBASE_HOME": "/opt/liquibase"}, clear=True):
            with patch("db.migrations.os.access", return_value=True):
                self.assertEqual(liquibase_executable(), "/opt/liquibase/liquibase")

    def test_a_developer_install_on_the_path_is_used_when_there_is_no_home(self):
        with patch.dict("os.environ", {}, clear=True):
            with patch("db.migrations.os.access", return_value=False):
                with patch(
                    "db.migrations.shutil.which", return_value="/usr/local/bin/liquibase"
                ):
                    self.assertEqual(liquibase_executable(), "/usr/local/bin/liquibase")

    def test_the_failure_names_what_the_developer_has_to_install(self):
        with patch.dict("os.environ", {}, clear=True):
            with patch("db.migrations.os.access", return_value=False):
                with patch("db.migrations.shutil.which", return_value=None):
                    with self.assertRaises(MigrationError) as raised:
                        liquibase_executable()

        self.assertIn("liquibase", str(raised.exception))


class UpdateTest(unittest.TestCase):

    def setUp(self):
        self.executable = patch(
            "db.migrations.liquibase_executable", return_value="/opt/liquibase/liquibase"
        )
        self.executable.start()
        self.addCleanup(self.executable.stop)

    def test_the_password_never_reaches_the_command_line(self):
        with patch("db.migrations.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, "", "")
            _update_blocking(COMPOSE_URL)

        command = run.call_args.args[0]
        self.assertEqual(command, ["/opt/liquibase/liquibase", "update"])
        self.assertNotIn("testpassword123", " ".join(command))
        self.assertEqual(
            run.call_args.kwargs["env"]["LIQUIBASE_COMMAND_PASSWORD"],
            "testpassword123",
        )

    def test_the_changelog_and_search_path_are_passed_to_liquibase(self):
        with patch("db.migrations.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, "", "")
            _update_blocking(COMPOSE_URL)

        environment = run.call_args.kwargs["env"]
        self.assertEqual(
            environment["LIQUIBASE_COMMAND_CHANGELOG_FILE"], "db.changelog-config.xml"
        )
        self.assertTrue(environment["LIQUIBASE_SEARCH_PATH"].endswith("changelog"))

    def test_usage_analytics_are_off_so_no_schema_detail_leaves_the_container(self):
        with patch("db.migrations.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, "", "")
            _update_blocking(COMPOSE_URL)

        self.assertEqual(
            run.call_args.kwargs["env"]["LIQUIBASE_ANALYTICS_ENABLED"], "false"
        )

    def test_a_failed_update_reports_what_liquibase_printed(self):
        with patch("db.migrations.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess(
                [], 1, "", "Connection refused"
            )
            with self.assertRaises(MigrationError) as raised:
                _update_blocking(COMPOSE_URL)

        self.assertIn("Connection refused", str(raised.exception))

    def test_a_failed_update_does_not_echo_the_password(self):
        with patch("db.migrations.subprocess.run") as run:
            run.return_value = subprocess.CompletedProcess([], 1, "", "boom")
            with self.assertRaises(MigrationError) as raised:
                _update_blocking(COMPOSE_URL)

        self.assertNotIn("testpassword123", str(raised.exception))


class UpdateToLatestTest(unittest.IsolatedAsyncioTestCase):

    async def test_a_broken_migration_stops_the_service_from_booting(self):
        with patch("db.migrations._update_blocking", side_effect=MigrationError("boom")):
            with self.assertRaises(MigrationError):
                await update_to_latest()

    async def test_a_healthy_migration_lets_the_service_boot(self):
        with patch("db.migrations._update_blocking") as blocking:
            await update_to_latest()

        blocking.assert_called_once()


if __name__ == "__main__":
    unittest.main()
