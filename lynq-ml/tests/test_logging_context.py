"""Tests for the request-scoped logging context (MDC-style request UUID)."""

from __future__ import annotations

import json
import logging
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from llm_client import LLMProvider
from logging_context import NO_REQUEST_UUID, RequestUuidFilter, request_uuid_ctx
from main import app


def _make_record() -> logging.LogRecord:
    return logging.LogRecord(
        name="test", level=logging.INFO, pathname=__file__, lineno=1,
        msg="hi", args=(), exc_info=None,
    )


class RequestUuidFilterTests(unittest.TestCase):
    """The filter copies the context UUID onto each record."""

    def test_uses_placeholder_outside_a_request(self) -> None:
        record = _make_record()

        self.assertTrue(RequestUuidFilter().filter(record))
        self.assertEqual(record.lynq_request_uuid, NO_REQUEST_UUID)

    def test_copies_current_context_value(self) -> None:
        token = request_uuid_ctx.set("uuid-123")
        try:
            record = _make_record()
            RequestUuidFilter().filter(record)
        finally:
            request_uuid_ctx.reset(token)

        self.assertEqual(record.lynq_request_uuid, "uuid-123")


class RequestUuidContextIntegrationTests(unittest.TestCase):
    """The middleware exposes the header value to logging during a request."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_request_uuid_is_visible_to_handler_logs_and_reset_after(self) -> None:
        captured: list[str] = []

        class _Capture(logging.Handler):
            def emit(self, record: logging.LogRecord) -> None:
                captured.append(record.lynq_request_uuid)

        handler = _Capture()
        handler.addFilter(RequestUuidFilter())
        router_log = logging.getLogger("skill_enhance.router")
        router_log.addHandler(handler)
        previous_disable = logging.root.manager.disable
        logging.disable(logging.NOTSET)  # tests/__init__ disables logging globally

        fake = MagicMock()
        fake.provider = LLMProvider.OLLAMA
        fake.generate = AsyncMock(return_value=json.dumps({"skills": ["Java"]}))

        try:
            with patch("skill_enhance.router.get_llm_client", return_value=fake):
                response = self.client.post(
                    "/lynq-ml/skill-enhance",
                    json={"title": "t", "description": "d", "work_type": "REMOTE"},
                    headers={
                        "lynq-request-uuid": "req-abc",
                        "user-id": "u1",
                        "company-id": "c1",
                    },
                )
        finally:
            router_log.removeHandler(handler)
            logging.disable(previous_disable)

        self.assertEqual(response.status_code, 200)
        # Every router log emitted during the request carried the header UUID...
        self.assertTrue(captured)
        self.assertTrue(all(uuid == "req-abc" for uuid in captured))
        # ...and the context is restored once the request completes.
        self.assertEqual(request_uuid_ctx.get(), NO_REQUEST_UUID)


if __name__ == "__main__":
    unittest.main()
