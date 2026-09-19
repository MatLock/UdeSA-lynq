from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.support import TemporaryDatabase  # noqa: F401

from config import Settings
from db import session as session_module

SQLITE_URL = "sqlite+aiosqlite:///:memory:"


def sqlite_settings() -> Settings:
    settings = Settings()
    settings.db_url = SQLITE_URL
    settings.db_echo = False
    return settings


class SessionTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        patched = patch.object(session_module, "get_settings", sqlite_settings)
        patched.start()
        self.addCleanup(patched.stop)

    async def asyncTearDown(self) -> None:
        await session_module.dispose_engine()

    async def test_the_engine_is_built_once_and_shared(self) -> None:
        self.assertIs(session_module.get_engine(), session_module.get_engine())

    async def test_the_engine_points_at_the_configured_database(self) -> None:
        self.assertEqual(str(session_module.get_engine().url), SQLITE_URL)

    async def test_the_session_factory_is_built_once_and_shared(self) -> None:
        self.assertIs(
            session_module.get_session_factory(), session_module.get_session_factory()
        )

    async def test_sessions_do_not_expire_their_objects_on_commit(self) -> None:
        self.assertFalse(session_module.get_session_factory().kw["expire_on_commit"])

    async def test_disposing_lets_the_next_call_build_a_fresh_engine(self) -> None:
        first = session_module.get_engine()
        await session_module.dispose_engine()

        self.assertIsNot(first, session_module.get_engine())

    async def test_disposing_an_engine_that_was_never_built_is_harmless(self) -> None:
        await session_module.dispose_engine()
        await session_module.dispose_engine()


if __name__ == "__main__":
    unittest.main()
