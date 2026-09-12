from __future__ import annotations

import asyncio

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from backend_client import BackendClient
from config import get_settings
from ml_client import MlClient

router = APIRouter()

UP = "UP"
DOWN = "DOWN"


@router.get("/health")
async def health() -> JSONResponse:
    settings = get_settings()
    ml_client = MlClient(
        base_url=settings.ml_url,
        system_user_id=settings.system_user_id,
        timeout=settings.http_timeout,
    )
    backend_client = BackendClient(
        base_url=settings.backend_url,
        internal_token=settings.internal_token,
        timeout=settings.http_timeout,
    )

    ml_up, backend_up = await asyncio.gather(
        ml_client.is_reachable(), backend_client.is_reachable()
    )

    body = {
        "status": UP,
        "ml": {"status": UP if ml_up else DOWN},
        "backend": {"status": UP if backend_up else DOWN},
    }
    return JSONResponse(status_code=200, content=body)
