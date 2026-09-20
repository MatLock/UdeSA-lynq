from __future__ import annotations

import asyncio
import unittest
from unittest.mock import AsyncMock, patch

from tests.support import TemporaryDatabase  # noqa: F401

import main
from config import Settings, reset_settings


def settings_with(**overrides) -> Settings:
    reset_settings()
    settings = Settings()
    for name, value in overrides.items():
        setattr(settings, name, value)
    return settings


class LifespanTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        self.started = asyncio.Event()
        self.cancelled = asyncio.Event()

    async def _forever(self) -> None:
        self.started.set()
        try:
            await asyncio.sleep(3600)
        except asyncio.CancelledError:
            self.cancelled.set()
            raise

    def _boot(self, **overrides):
        self.migrate = AsyncMock()
        self.dispose = AsyncMock()
        return patch.multiple(
            main,
            get_settings=lambda: settings_with(**overrides),
            update_to_latest=self.migrate,
            dispose_engine=self.dispose,
            housekeeping_loop=self._forever,
        )

    async def test_the_schema_is_migrated_before_the_service_takes_traffic(self) -> None:
        patched = self._boot(db_migrate_on_startup=True, housekeeping_enabled=False)
        with patched:
            async with main.lifespan(main.app):
                pass

        self.migrate.assert_awaited_once()

    async def test_the_migration_can_be_switched_off(self) -> None:
        patched = self._boot(db_migrate_on_startup=False, housekeeping_enabled=False)
        with patched:
            async with main.lifespan(main.app):
                pass

        self.migrate.assert_not_awaited()

    async def test_housekeeping_runs_alongside_the_service_and_stops_with_it(self) -> None:
        patched = self._boot(db_migrate_on_startup=False, housekeeping_enabled=True)
        with patched:
            async with main.lifespan(main.app):
                await asyncio.wait_for(self.started.wait(), timeout=1)

        self.assertTrue(self.cancelled.is_set())

    async def test_housekeeping_can_be_switched_off(self) -> None:
        patched = self._boot(db_migrate_on_startup=False, housekeeping_enabled=False)
        with patched:
            async with main.lifespan(main.app):
                await asyncio.sleep(0)

        self.assertFalse(self.started.is_set())

    async def test_the_connection_pool_is_released_on_shutdown(self) -> None:
        patched = self._boot(db_migrate_on_startup=False, housekeeping_enabled=False)
        with patched:
            async with main.lifespan(main.app):
                pass

        self.dispose.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
