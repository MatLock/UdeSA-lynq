from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from backend_client import IngestStats
from logging_context import NO_REQUEST_UUID, request_uuid_ctx
from main import app
from middleware.request_uuid import EXEMPT_PATHS, REQUEST_UUID_HEADER
from service import IngestReport

INGEST = "/lynq-feeders/ingest"
REQUEST_UUID = "11111111-2222-3333-4444-555555555555"


class RequireRequestUuidTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_a_request_without_the_header_is_rejected_with_403(self):
        response = self.client.post(INGEST)

        self.assertEqual(response.status_code, 403)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn(REQUEST_UUID_HEADER, payload["reason"])

    def test_a_blank_header_is_rejected_too(self):
        response = self.client.post(INGEST, headers={REQUEST_UUID_HEADER: ""})
        self.assertEqual(response.status_code, 403)

    def test_the_health_probe_is_exempt(self):
        self.assertIn("/lynq-feeders/health", EXEMPT_PATHS)

        ml = patch("router.health.MlClient.is_reachable", AsyncMock(return_value=True))
        backend = patch("router.health.BackendClient.is_reachable", AsyncMock(return_value=True))
        with ml, backend:
            self.assertEqual(self.client.get("/lynq-feeders/health").status_code, 200)

    def test_the_header_reaches_the_logging_context(self):
        seen = {}

        async def capture(_request_uuid, _overrides=None):
            seen["uuid"] = request_uuid_ctx.get()
            return IngestReport(ingested=IngestStats())

        service = AsyncMock()
        service.run = capture
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers={REQUEST_UUID_HEADER: REQUEST_UUID})

        self.assertEqual(seen["uuid"], REQUEST_UUID)

    def test_the_context_is_reset_after_the_request(self):
        service = AsyncMock()
        service.run = AsyncMock(return_value=IngestReport(ingested=IngestStats()))
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers={REQUEST_UUID_HEADER: REQUEST_UUID})

        self.assertEqual(request_uuid_ctx.get(), NO_REQUEST_UUID)


if __name__ == "__main__":
    unittest.main()
