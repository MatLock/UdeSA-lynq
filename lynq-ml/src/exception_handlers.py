"""FastAPI exception handlers that wrap errors in the standard envelope.

Every handler renders an :class:`~response.ErrorRestResponse`, matching the
``GlobalRestResponse`` error shape used across the lynq platform.
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from response import ErrorRestResponse


async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    """Wrap raised HTTPExceptions in the standard error envelope."""
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorRestResponse(reason=str(exc.detail)).model_dump(),
    )


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """Return per-field validation errors in the standard error envelope."""
    errors = {
        ".".join(str(p) for p in err["loc"] if p != "body"): err["msg"]
        for err in exc.errors()
    }
    return JSONResponse(
        status_code=400,
        content=ErrorRestResponse(data=errors, reason="Invalid Fields Found").model_dump(),
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Catch-all: return unexpected errors in the standard error envelope."""
    return JSONResponse(
        status_code=500,
        content=ErrorRestResponse(reason=str(exc)).model_dump(),
    )


def register_exception_handlers(app: FastAPI) -> None:
    """Register all standard error-envelope handlers on ``app``."""
    app.add_exception_handler(HTTPException, http_exception_handler)
    app.add_exception_handler(RequestValidationError, validation_exception_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)
