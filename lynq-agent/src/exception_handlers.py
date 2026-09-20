from __future__ import annotations

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from model.errors import ConversationError
from response import ErrorRestResponse


def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorRestResponse(reason=str(exc.detail)).model_dump(),
    )


def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    errors = {
        ".".join(str(p) for p in err["loc"] if p != "body"): err["msg"]
        for err in exc.errors()
    }
    return JSONResponse(
        status_code=400,
        content=ErrorRestResponse(data=errors, reason="Invalid Fields Found").model_dump(),
    )


def conversation_exception_handler(
    request: Request, exc: ConversationError
) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorRestResponse(reason=exc.reason, code=exc.code).model_dump(),
    )


def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content=ErrorRestResponse(reason=str(exc)).model_dump(),
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(ConversationError, conversation_exception_handler)
    app.add_exception_handler(HTTPException, http_exception_handler)
    app.add_exception_handler(RequestValidationError, validation_exception_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)
