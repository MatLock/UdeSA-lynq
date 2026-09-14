from __future__ import annotations

import asyncio
import logging
import os

log = logging.getLogger(__name__)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_ALEMBIC_INI = os.path.join(_REPO_ROOT, "alembic.ini")


def _upgrade_blocking() -> None:
    from alembic import command
    from alembic.config import Config

    config = Config(_ALEMBIC_INI)
    config.set_main_option("script_location", os.path.join(_REPO_ROOT, "migrations"))
    command.upgrade(config, "head")


async def upgrade_to_head() -> None:
    try:
        await asyncio.to_thread(_upgrade_blocking)
        log.info("message= Database schema is up to date")
    except Exception as exc:
        log.error("message= Database migration failed", exc_info=exc)
        raise
