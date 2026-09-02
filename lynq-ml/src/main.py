from contextlib import asynccontextmanager

from fastapi import APIRouter, FastAPI

from exception_handlers import register_exception_handlers
from renderer.resume_template import pdf_renderer_available
from middleware.request_uuid import require_request_uuid
from router.candidate_explanation import router as candidate_explanation_router
from router.health import router as health_router
from router.language_detection import router as language_detection_router
from router.resume_extractor import router as resume_extractor_router
from router.resume_template import router as resume_template_router
from router.skill_enhance import router as skill_enhance_router
from router.translation import router as translation_router
from router.upskilling_suggestion import router as upskilling_suggestion_router
from router.user_resume_skill_extraction import router as skill_extraction_router

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

@asynccontextmanager
async def lifespan(_: FastAPI):
  # WeasyPrint's native libraries are not Python packages, so pip cannot install
  # them and a machine missing them looks perfectly healthy until someone asks
  # for a resume PDF. Probe once at boot so the error is in the startup log
  # rather than in a 500 half an hour later. The probe itself logs the fix; the
  # service still starts, since only one of the eight endpoints needs it.
  pdf_renderer_available()
  yield


app = FastAPI(version=_read_version(), lifespan=lifespan)

app.middleware("http")(require_request_uuid)
register_exception_handlers(app)

health = APIRouter(prefix="/lynq-ml")
health.include_router(health_router)
app.include_router(health)

dmz = APIRouter(prefix="/lynq-ml/dmz")
dmz.include_router(skill_enhance_router)
dmz.include_router(skill_extraction_router)
dmz.include_router(upskilling_suggestion_router)
dmz.include_router(candidate_explanation_router)
dmz.include_router(resume_extractor_router)
dmz.include_router(translation_router)
dmz.include_router(resume_template_router)
dmz.include_router(language_detection_router)
app.include_router(dmz)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8084")),
    log_config=LOGGING_CONFIG,
  )
