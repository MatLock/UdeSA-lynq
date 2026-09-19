from __future__ import annotations

import contextvars
import logging

NO_REQUEST_UUID = "-"

request_uuid_ctx: contextvars.ContextVar[str] = contextvars.ContextVar(
    "lynq_request_uuid", default=NO_REQUEST_UUID
)


class RequestUuidFilter(logging.Filter):

    def filter(self, record: logging.LogRecord) -> bool:
        record.lynq_request_uuid = request_uuid_ctx.get()
        return True
