"""Request-scoped logging context (the Python analogue of log4j2's MDC).

The lynq-request-uuid is stashed in a :class:`contextvars.ContextVar` per
request by the middleware, and :class:`RequestUuidFilter` copies it onto every
log record so the formatter can render it — mirroring ``[%X{requestId}]`` in the
Java services' log4j2 pattern.
"""

from __future__ import annotations

import contextvars
import logging

#: Fallback shown for logs emitted outside a request (startup, health probe).
NO_REQUEST_UUID = "-"

request_uuid_ctx: contextvars.ContextVar[str] = contextvars.ContextVar(
    "lynq_request_uuid", default=NO_REQUEST_UUID
)


class RequestUuidFilter(logging.Filter):
    """Attach the current request's UUID to each log record.

    Installed on the log handlers so it runs for every record (application and
    uvicorn alike); records outside a request carry :data:`NO_REQUEST_UUID`.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        record.lynq_request_uuid = request_uuid_ctx.get()
        return True
