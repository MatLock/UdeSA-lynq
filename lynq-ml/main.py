from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from llm_client import LLMProvider, get_llm_client
from middleware.request_uuid import require_request_uuid
from response import ErrorRestResponse
from skill_enhance.router import router as skill_enhance_router

import logging
import logging.config
import os
import uvicorn


def _build_logging_config() -> dict:
  """Colour logs per level (INFO cyan, WARNING yellow, ERROR red).

  Reuses uvicorn's colourising formatters so the service's own loggers and
  uvicorn's match, and streams to stdout (stderr is coloured red wholesale by
  many consoles/IDEs). ``LOG_COLORS=false`` disables colour for file/CI output.
  """
  level = os.getenv("LOG_LEVEL", "INFO").upper()
  use_colors = os.getenv("LOG_COLORS", "true").lower() not in ("0", "false", "no")
  return {
    "version": 1,
    "disable_existing_loggers": False,
    "filters": {
      "request_uuid": {"()": "logging_context.RequestUuidFilter"},
    },
    "formatters": {
      "default": {
        "()": "uvicorn.logging.DefaultFormatter",
        "fmt": "%(asctime)s %(levelprefix)s [%(lynq_request_uuid)s] %(name)s - %(message)s",
        "use_colors": use_colors,
      },
      "access": {
        "()": "uvicorn.logging.AccessFormatter",
        "fmt": '%(asctime)s %(levelprefix)s [%(lynq_request_uuid)s] %(client_addr)s - "%(request_line)s" %(status_code)s',
        "use_colors": use_colors,
      },
    },
    "handlers": {
      "default": {
        "class": "logging.StreamHandler",
        "formatter": "default",
        "filters": ["request_uuid"],
        "stream": "ext://sys.stdout",
      },
      "access": {
        "class": "logging.StreamHandler",
        "formatter": "access",
        "filters": ["request_uuid"],
        "stream": "ext://sys.stdout",
      },
    },
    "loggers": {
      "uvicorn": {"handlers": ["default"], "level": level, "propagate": False},
      "uvicorn.error": {"level": level},
      "uvicorn.access": {"handlers": ["access"], "level": level, "propagate": False},
    },
    "root": {"handlers": ["default"], "level": level},
  }


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
app.include_router(router)


if __name__ == "__main__":
  uvicorn.run(
    app,
    host=os.getenv("HOST", "0.0.0.0"),
    port=int(os.getenv("PORT", "8084")),
    log_config=LOGGING_CONFIG,
  )