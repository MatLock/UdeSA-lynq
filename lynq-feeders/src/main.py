from fastapi import APIRouter, FastAPI

from exception_handlers import register_exception_handlers
from middleware.request_uuid import require_request_uuid
from router.health import router as health_router
from router.ingest import router as ingest_router

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

app = FastAPI(version=_read_version())

app.middleware("http")(require_request_uuid)
register_exception_handlers(app)

health = APIRouter(prefix="/lynq-feeders")
health.include_router(health_router)
app.include_router(health)

internal = APIRouter(prefix="/lynq-feeders")
internal.include_router(ingest_router)
app.include_router(internal)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8089")),
    log_config=LOGGING_CONFIG,
  )
