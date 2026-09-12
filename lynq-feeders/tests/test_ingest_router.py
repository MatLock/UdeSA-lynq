from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from backend_client import BackendError, IngestStats
from main import app
from service import IngestReport, SourceReport

INGEST = "/lynq-feeders/ingest"
HEADERS = {"lynq-request-uuid": "11111111-2222-3333-4444-555555555555"}


def _report() -> IngestReport:
    return IngestReport(
        fetched=12,
        deduplicated=2,
        enriched=9,
        enrichment_failed=1,
        ingested=IngestStats(jobs=10, companies=4, skills=30, similarityTags=18, skipped=0),
        per_source=[SourceReport(source="bumeran", rubro="TECNOLOGIA", fetched=10)],
    )


def _service(run_return=None, run_error=None):
    service = AsyncMock()
    service.run = AsyncMock(return_value=run_return, side_effect=run_error)
    return service


class IngestRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_returns_the_report_inside_the_standard_envelope(self):
        with patch("router.ingest.build_service", return_value=_service(_report())):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["data"]["fetched"], 12)
        self.assertEqual(payload["data"]["ingested"]["jobs"], 10)
        self.assertEqual(payload["data"]["per_source"][0]["source"], "bumeran")

    def test_passes_the_request_uuid_down_to_the_service(self):
        service = _service(_report())
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers=HEADERS)

        service.run.assert_awaited_once_with(HEADERS["lynq-request-uuid"], None)

    def test_a_failing_ingest_becomes_a_502_in_the_error_envelope(self):
        with patch("router.ingest.build_service", return_value=_service(run_error=BackendError("401"))):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 502)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn("401", payload["reason"])

    def test_missing_request_uuid_is_rejected(self):
        response = self.client.post(INGEST)

        self.assertEqual(response.status_code, 403)
        self.assertFalse(response.json()["success"])


if __name__ == "__main__":
    unittest.main()
