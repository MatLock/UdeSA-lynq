from __future__ import annotations

import logging
import unittest

from logging_context import NO_REQUEST_UUID, RequestUuidFilter, request_uuid_ctx


class RequestUuidFilterTests(unittest.TestCase):

    def _record(self) -> logging.LogRecord:
        return logging.LogRecord("test", logging.INFO, __file__, 1, "msg", None, None)

    def test_records_outside_a_request_carry_the_fallback(self) -> None:
        record = self._record()

        RequestUuidFilter().filter(record)

        self.assertEqual(record.lynq_request_uuid, NO_REQUEST_UUID)

    def test_records_inside_a_request_carry_the_request_uuid(self) -> None:
        token = request_uuid_ctx.set("req-1")
        record = self._record()
        try:
            RequestUuidFilter().filter(record)
        finally:
            request_uuid_ctx.reset(token)

        self.assertEqual(record.lynq_request_uuid, "req-1")


if __name__ == "__main__":
    unittest.main()
