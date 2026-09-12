from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse

from logging_context import request_uuid_ctx
from response import ErrorRestResponse

REQUEST_UUID_HEADER = "lynq-request-uuid"

EXEMPT_PATHS = frozenset({"/lynq-feeders/health"})


async def require_request_uuid(request: Request, call_next):
    if request.url.path in EXEMPT_PATHS:
        return await call_next(request)

    request_uuid = request.headers.get(REQUEST_UUID_HEADER)
    if not request_uuid:
        return JSONResponse(
            status_code=403,
            content=ErrorRestResponse(
                reason=f"Missing required header: {REQUEST_UUID_HEADER}"
            ).model_dump(),
        )

    token = request_uuid_ctx.set(request_uuid)
    try:
        return await call_next(request)
    finally:
        request_uuid_ctx.reset(token)
