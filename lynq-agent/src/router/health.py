from __future__ import annotations

import logging

from fastapi import APIRouter
from sqlalchemy import text

from client import LynqMlClient
from config import get_settings
from db.session import get_session_factory

log = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health() -> dict:
    settings = get_settings()
    ml_client = LynqMlClient(
        base_url=settings.ml_url,
        system_user_id=settings.system_user_id,
        timeout=min(settings.ml_timeout, 5.0),
    )

    database_up = await _database_is_reachable()
    ml_up = await ml_client.is_reachable()

    return {
        "status": "UP",
        "database": {"status": "UP" if database_up else "DOWN"},
        "ml": {"status": "UP" if ml_up else "DOWN"},
    }


async def _database_is_reachable() -> bool:
    try:
        async with get_session_factory()() as session:
            await session.execute(text("SELECT 1"))
        return True
    except Exception as exc:
        log.warning("message= Database health probe failed", exc_info=exc)
        return False
