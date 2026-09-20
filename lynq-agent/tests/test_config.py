from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.support import TemporaryDatabase  # noqa: F401

from config import DEFAULT_DB_URL, Settings, get_settings, reset_settings


class SettingsTest(unittest.TestCase):

    def setUp(self) -> None:
        reset_settings()
        self.addCleanup(reset_settings)

    def test_the_defaults_match_what_the_plan_promises(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            settings = Settings()

        self.assertEqual(settings.db_url, DEFAULT_DB_URL)
        self.assertTrue(settings.db_migrate_on_startup)
        self.assertTrue(settings.housekeeping_enabled)
        self.assertEqual(settings.housekeeping_interval_seconds, 3600)
        self.assertEqual(settings.abandon_after_days, 7)
        self.assertEqual(settings.trace_ttl_days, 30)
        self.assertEqual(settings.conversation_ttl_days, 180)

    def test_every_ttl_can_be_dropped_to_zero_from_the_environment(self) -> None:
        environment = {
            "AGENT_ABANDON_AFTER_DAYS": "0",
            "AGENT_TRACE_TTL_DAYS": "0",
            "AGENT_CONVERSATION_TTL_DAYS": "0",
        }
        with patch.dict("os.environ", environment, clear=True):
            settings = Settings()

        self.assertEqual(settings.abandon_after_days, 0)
        self.assertEqual(settings.trace_ttl_days, 0)
        self.assertEqual(settings.conversation_ttl_days, 0)

    def test_a_ttl_that_is_not_a_number_falls_back_to_the_default(self) -> None:
        with patch.dict("os.environ", {"AGENT_TRACE_TTL_DAYS": "never"}, clear=True):
            self.assertEqual(Settings().trace_ttl_days, 30)

    def test_the_migration_flag_is_read_as_a_flag(self) -> None:
        with patch.dict("os.environ", {"DB_MIGRATE_ON_STARTUP": "FALSE"}, clear=True):
            self.assertFalse(Settings().db_migrate_on_startup)

    def test_the_settings_are_read_once_and_reused(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            self.assertIs(get_settings(), get_settings())


if __name__ == "__main__":
    unittest.main()
