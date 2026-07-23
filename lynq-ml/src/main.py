from fastapi import APIRouter, FastAPI

from candidate_explanation.router import router as candidate_explanation_router
from exception_handlers import register_exception_handlers
from health.router import router as health_router
from middleware.request_uuid import require_request_uuid
from resume_extractor.router import router as resume_extractor_router
from resume_template.router import router as resume_template_router
from skill_enhance.router import router as skill_enhance_router
from translation.router import router as translation_router
from upskilling_suggestion.router import router as upskilling_suggestion_router

import logging.config
import os
import uvicorn
import json


# src/main.py -> repo root is one level up; service config lives in resources/.
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_LOG_CONFIG_PATH = os.path.join(_REPO_ROOT, "resources", "log_config.json")
_VERSION_PATH = os.path.join(_REPO_ROOT, "VERSION")


def _build_logging_config() -> dict:
  with open(_LOG_CONFIG_PATH, "r", encoding="utf-8") as f:
    return json.load(f)


def _read_version() -> str:
  # The release workflow writes the GitHub tag into VERSION; fall back to a
  # sentinel when the file is missing (e.g. local checkouts before a release).
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

router = APIRouter(prefix="/lynq-ml")
router.include_router(health_router)
router.include_router(skill_enhance_router)
router.include_router(upskilling_suggestion_router)
router.include_router(candidate_explanation_router)
router.include_router(resume_extractor_router)
router.include_router(translation_router)
router.include_router(resume_template_router)
app.include_router(router)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8084")),
    log_config=LOGGING_CONFIG,
  )
