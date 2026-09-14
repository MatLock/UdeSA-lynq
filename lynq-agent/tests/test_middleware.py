from __future__ import annotations

import unittest

from fastapi.testclient import TestClient

from tests.support import base_resume  # noqa: F401

from logging_context import NO_REQUEST_UUID, RequestUuidFilter, request_uuid_ctx
from main import app
from middleware.request_uuid import EXEMPT_PATHS, REQUEST_UUID_HEADER


class RequestUuidMiddlewareTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_a_dmz_call_without_the_header_is_refused(self):
        response = self.client.get("/lynq-agent/dmz/conversation/whatever")

        self.assertEqual(response.status_code, 403)
        body = response.json()
        self.assertFalse(body["success"])
        self.assertIn(REQUEST_UUID_HEADER, body["reason"])

    def test_the_health_path_is_exempt(self):
        self.assertIn("/lynq-agent/health", EXEMPT_PATHS)


class LoggingContextTest(unittest.TestCase):

    def test_a_record_outside_a_request_carries_the_placeholder(self):
        import logging

        record = logging.LogRecord("n", logging.INFO, "p", 1, "m", None, None)
        RequestUuidFilter().filter(record)

        self.assertEqual(record.lynq_request_uuid, NO_REQUEST_UUID)

    def test_a_record_inside_a_request_carries_the_uuid(self):
        import logging

        token = request_uuid_ctx.set("uuid-42")
        try:
            record = logging.LogRecord("n", logging.INFO, "p", 1, "m", None, None)
            RequestUuidFilter().filter(record)
        finally:
            request_uuid_ctx.reset(token)

        self.assertEqual(record.lynq_request_uuid, "uuid-42")


if __name__ == "__main__":
    unittest.main()
