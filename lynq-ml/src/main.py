from fastapi import APIRouter, FastAPI

from candidate_explanation.router import router as candidate_explanation_router
from exception_handlers import register_exception_handlers
from health.router import router as health_router
from middleware.request_uuid import require_request_uuid
from skill_enhance.router import router as skill_enhance_router
from upskilling_suggestion.router import router as upskilling_suggestion_router

import logging.config
import os
import uvicorn
import json


# src/main.py -> repo root is one level up; service config lives in resources/.
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_LOG_CONFIG_PATH = os.path.join(_REPO_ROOT, "resources", "log_config.json")


def _build_logging_config() -> dict:
  with open(_LOG_CONFIG_PATH, "r", encoding="utf-8") as f:
    return json.load(f)


LOGGING_CONFIG = _build_logging_config()
logging.config.dictConfig(LOGGING_CONFIG)

app = FastAPI()

app.middleware("http")(require_request_uuid)
register_exception_handlers(app)

router = APIRouter(prefix="/lynq-ml")
router.include_router(health_router)
router.include_router(skill_enhance_router)
router.include_router(upskilling_suggestion_router)
router.include_router(candidate_explanation_router)
app.include_router(router)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8084")),
    log_config=LOGGING_CONFIG,
  )
