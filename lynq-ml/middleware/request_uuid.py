"""Middleware that enforces the presence of the lynq request UUID header."""

from __future__ import annotations

from fastapi import Request
from fastapi.responses import JSONResponse

from response import ErrorRestResponse

REQUEST_UUID_HEADER = "lynq-request-uuid"

#: Paths exempt from the header check (e.g. probes hit by infra, not clients).
EXEMPT_PATHS = frozenset({"/lynq-ml/health"})


async def require_request_uuid(request: Request, call_next):
    """Reject requests missing the ``lynq-request-uuid`` header with a 403.

    Requests to :data:`EXEMPT_PATHS` bypass the check.

    Args:
        request: The incoming HTTP request.
        call_next: The next handler in the middleware chain.

    Returns:
        A 403 ``JSONResponse`` when the header is absent, otherwise the
        downstream handler's response.
    """
    if request.url.path in EXEMPT_PATHS:
        return await call_next(request)

    if not request.headers.get(REQUEST_UUID_HEADER):
        return JSONResponse(
            status_code=403,
            content=ErrorRestResponse(
                reason=f"Missing required header: {REQUEST_UUID_HEADER}"
            ).model_dump(),
        )
    return await call_next(request)