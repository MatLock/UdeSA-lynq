from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from backend_client import BackendError, IngestStats
from main import app
from model import RunPlan
from router.ingest import run_guard
from service import EnrichmentError, IngestReport, SourceReport

INGEST = "/lynq-feeders/ingest"
HEADERS = {"lynq-request-uuid": "11111111-2222-3333-4444-555555555555"}


def _report() -> IngestReport:
    return IngestReport(
        fetched=12,
        deduplicated=2,
        enriched=9,
        enrichment_failed=1,
        ingested=IngestStats(jobs=10, companies=4, skills=30, similarityTags=18, skipped=0),
        per_source=[SourceReport(source="bumeran", category="TECNOLOGIA", fetched=10)],
    )


def _plan() -> RunPlan:
    return RunPlan(sources=["bumeran"], categories=["TECNOLOGIA"], jobs_per_category=10)


def _service(run_return=None, run_error=None, validate_error=None):
    service = AsyncMock()
    service.validate = MagicMock(return_value=_plan(), side_effect=validate_error)
    service.run = AsyncMock(return_value=run_return, side_effect=run_error)
    return service


class IngestRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)
        self.addCleanup(run_guard.finish)

    def test_accepts_the_run_with_a_202_and_no_body(self):
        with patch("router.ingest.build_service", return_value=_service(_report())):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.content, b"")

    def test_the_run_happens_in_the_background(self):
        service = _service(_report())
        with patch("router.ingest.build_service", return_value=service):
            with patch("router.ingest.BackgroundTasks.add_task") as add_task:
                response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 202)
        service.run.assert_not_awaited()
        add_task.assert_called_once()

    def test_passes_the_request_uuid_down_to_the_service(self):
        service = _service(_report())
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers=HEADERS)

        service.run.assert_awaited_once_with(HEADERS["lynq-request-uuid"], None)

    def test_a_failing_ingest_does_not_change_the_accepted_response(self):
        with patch(
            "router.ingest.build_service", return_value=_service(run_error=BackendError("401"))
        ):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.content, b"")

    def test_a_failing_enrichment_does_not_change_the_accepted_response(self):
        error = EnrichmentError("skill extraction failed for 3 of 8 listings")
        with patch("router.ingest.build_service", return_value=_service(run_error=error)):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 202)

    def test_an_unexpected_failure_is_swallowed_by_the_background_task(self):
        error = RuntimeError("the scraper blew up")
        with patch("router.ingest.build_service", return_value=_service(run_error=error)):
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 202)

    def test_a_failed_run_releases_the_guard_for_the_next_one(self):
        with patch(
            "router.ingest.build_service", return_value=_service(run_error=BackendError("401"))
        ):
            self.client.post(INGEST, headers=HEADERS)
            second = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(second.status_code, 202)

    def test_a_second_run_is_rejected_while_one_is_in_progress(self):
        run_guard.start()
        with patch("router.ingest.build_service", return_value=_service(_report())) as build:
            response = self.client.post(INGEST, headers=HEADERS)

        self.assertEqual(response.status_code, 409)
        self.assertFalse(response.json()["success"])
        self.assertIn("already in progress", response.json()["reason"])
        build.return_value.run.assert_not_awaited()

    def test_missing_request_uuid_is_rejected(self):
        response = self.client.post(INGEST)

        self.assertEqual(response.status_code, 403)
        self.assertFalse(response.json()["success"])


if __name__ == "__main__":
    unittest.main()
