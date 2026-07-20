from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from llm_client import LLMProvider, get_llm_client
from middleware.request_uuid import require_request_uuid
from response import ErrorRestResponse
from skill_enhance.router import router as skill_enhance_router
from upskilling_suggestion.router import router as upskilling_suggestion_router

import logging.config
import os
import uvicorn
import json


_LOG_CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "log_config.json")


def _build_logging_config() -> dict:
  with open(_LOG_CONFIG_PATH, "r", encoding="utf-8") as f:
    return json.load(f)


# Configure logging before the app starts so the service's own loggers (e.g.
# skill_enhance.router) actually emit. Without this the root logger defaults to
# WARNING with no handler and every log.info(...) is silently dropped. Applied
# at import so it also covers `uvicorn main:app`, not just `python main.py`.
LOGGING_CONFIG = _build_logging_config()
logging.config.dictConfig(LOGGING_CONFIG)

app = FastAPI()

app.middleware("http")(require_request_uuid)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
  """Wrap raised HTTPExceptions in the standard error envelope."""
  return JSONResponse(
    status_code=exc.status_code,
    content=ErrorRestResponse(reason=str(exc.detail)).model_dump(),
  )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
  """Return per-field validation errors in the standard error envelope."""
  errors = {
    ".".join(str(p) for p in err["loc"] if p != "body"): err["msg"]
    for err in exc.errors()
  }
  return JSONResponse(
    status_code=400,
    content=ErrorRestResponse(data=errors, reason="Invalid Fields Found").model_dump(),
  )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
  """Catch-all: return unexpected errors in the standard error envelope."""
  return JSONResponse(
    status_code=500,
    content=ErrorRestResponse(reason=str(exc)).model_dump(),
  )


router = APIRouter(prefix="/lynq-ml")


@router.get("/health")
async def health():
  try:
    client = get_llm_client()
    provider = client.provider
    llm_up = await client.health_check()
  except ValueError:
    provider = None
    llm_up = False

  llm = {
    "provider": provider.value if isinstance(provider, LLMProvider) else None,
    "status": "UP" if llm_up else "DOWN",
  }
  body = {"status": "UP" if llm_up else "DOWN", "llm": llm}
  return JSONResponse(status_code=200 if llm_up else 503, content=body)


router.include_router(skill_enhance_router)
router.include_router(upskilling_suggestion_router)
app.include_router(router)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8084")),
    log_config=LOGGING_CONFIG,
  )