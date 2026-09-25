from __future__ import annotations

import contextvars
import logging
import re

NO_REQUEST_UUID = "-"
LOG_VALUE_MAX_LENGTH = 80

_CONTROL_CHARACTERS = re.compile(r"[\x00-\x1f\x7f]")


def log_safe(value: object) -> str:
    return _CONTROL_CHARACTERS.sub("", str(value))[:LOG_VALUE_MAX_LENGTH]


request_uuid_ctx: contextvars.ContextVar[str] = contextvars.ContextVar(
    "lynq_request_uuid", default=NO_REQUEST_UUID
)


class RequestUuidFilter(logging.Filter):

    def filter(self, record: logging.LogRecord) -> bool:
        record.lynq_request_uuid = request_uuid_ctx.get()
        return True
