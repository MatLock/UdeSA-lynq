from contextlib import asynccontextmanager, suppress

from fastapi import APIRouter, FastAPI

from config import get_settings
from db.housekeeping import housekeeping_loop
from db.migrations import update_to_latest
from db.session import dispose_engine
from exception_handlers import register_exception_handlers
from middleware.request_uuid import require_request_uuid
from router.conversation import router as conversation_router
from router.health import router as health_router

import asyncio
import logging.config
import os
import uvicorn
import json


_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_LOG_CONFIG_PATH = os.path.join(_REPO_ROOT, "resources", "log_config.json")
_VERSION_PATH = os.path.join(_REPO_ROOT, "VERSION")


def _build_logging_config() -> dict:
  with open(_LOG_CONFIG_PATH, "r", encoding="utf-8") as f:
    return json.load(f)


def _read_version() -> str:
  try:
    with open(_VERSION_PATH, "r", encoding="utf-8") as f:
      return f.read().strip() or "0.0.0"
  except FileNotFoundError:
    return "0.0.0"


LOGGING_CONFIG = _build_logging_config()
logging.config.dictConfig(LOGGING_CONFIG)


@asynccontextmanager
async def lifespan(_: FastAPI):
  settings = get_settings()

  if settings.db_migrate_on_startup:
    await update_to_latest()

  housekeeping = None
  if settings.housekeeping_enabled:
    housekeeping = asyncio.create_task(housekeeping_loop())

  yield

  if housekeeping is not None:
    housekeeping.cancel()
    with suppress(asyncio.CancelledError):
      await housekeeping

  await dispose_engine()


app = FastAPI(version=_read_version(), lifespan=lifespan)

app.middleware("http")(require_request_uuid)
register_exception_handlers(app)

health = APIRouter(prefix="/lynq-agent")
health.include_router(health_router)
app.include_router(health)

dmz = APIRouter(prefix="/lynq-agent/dmz")
dmz.include_router(conversation_router)
app.include_router(dmz)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8090")),
    log_config=LOGGING_CONFIG,
  )
