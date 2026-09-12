from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from backend_client import IngestStats
from config import Settings
from main import app
from ml_client import SkillEnhanceResult
from model import IngestOverrides
from scraper.base import Listing
from service import IngestService

INGEST = "/lynq-feeders/ingest"
HEADERS = {"lynq-request-uuid": "11111111-2222-3333-4444-555555555555"}
REQUEST_UUID = HEADERS["lynq-request-uuid"]


def _settings() -> Settings:
    settings = Settings()
    settings.sources = ["bumeran", "computrabajo"]
    settings.rubros = ["ADMINISTRACION", "TECNOLOGIA", "CONTABILIDAD", "RECURSOS_HUMANOS"]
    settings.jobs_per_rubro = 10
    return settings


def _listing(external_id="1", source="bumeran") -> Listing:
    return Listing(
        external_id=external_id,
        title="Backend Developer",
        source=source,
        rubro="TECNOLOGIA",
        description="Python y FastAPI.",
    )


def _scraper(source="bumeran", listings=None):
    scraper = MagicMock()
    scraper.source = source
    scraper.fetch = MagicMock(return_value=listings if listings is not None else [])
    return scraper


def _ml():
    ml = MagicMock()
    ml.skill_enhance = AsyncMock(return_value=SkillEnhanceResult(["Python"], ["Backend"]))
    return ml


def _backend():
    backend = MagicMock()
    backend.ingest = AsyncMock(return_value=IngestStats(jobs=1))
    return backend


class PlanTest(unittest.TestCase):

    def setUp(self):
        self.service = IngestService(_settings(), _ml(), _backend(), scrapers=[_scraper()])

    def test_no_overrides_uses_the_configured_defaults(self):
        plan = self.service.plan_for(None)

        self.assertEqual(plan.sources, ["bumeran", "computrabajo"])
        self.assertEqual(len(plan.rubros), 4)
        self.assertEqual(plan.jobs_per_rubro, 10)

    def test_an_empty_body_is_the_same_as_no_body(self):
        self.assertEqual(self.service.plan_for(IngestOverrides()), self.service.plan_for(None))

    def test_each_field_can_be_overridden_on_its_own(self):
        plan = self.service.plan_for(IngestOverrides(rubros=["TECNOLOGIA"]))

        self.assertEqual(plan.rubros, ["TECNOLOGIA"])
        self.assertEqual(plan.sources, ["bumeran", "computrabajo"])
        self.assertEqual(plan.jobs_per_rubro, 10)

    def test_a_fully_scoped_run(self):
        plan = self.service.plan_for(
            IngestOverrides(sources=["computrabajo"], rubros=["TECNOLOGIA"], jobs_per_rubro=2)
        )

        self.assertEqual(plan.sources, ["computrabajo"])
        self.assertEqual(plan.rubros, ["TECNOLOGIA"])
        self.assertEqual(plan.jobs_per_rubro, 2)


class ScopedRunTest(unittest.IsolatedAsyncioTestCase):

    async def test_the_override_reaches_the_scraper(self):
        scraper = _scraper(listings=[_listing()])
        service = IngestService(_settings(), _ml(), _backend(), scrapers=[scraper])

        await service.run(
            REQUEST_UUID, IngestOverrides(rubros=["TECNOLOGIA"], jobs_per_rubro=3)
        )

        self.assertEqual(scraper.fetch.call_count, 1)
        self.assertEqual(scraper.fetch.call_args.args, ("TECNOLOGIA", 3))

    async def test_the_report_states_what_actually_ran(self):
        service = IngestService(
            _settings(), _ml(), _backend(), scrapers=[_scraper(listings=[_listing()])]
        )

        report = await service.run(REQUEST_UUID, IngestOverrides(rubros=["CONTABILIDAD"]))

        self.assertEqual(report.plan.rubros, ["CONTABILIDAD"])
        self.assertEqual(report.plan.jobs_per_rubro, 10)

    async def test_the_default_run_still_covers_every_rubro(self):
        scraper = _scraper(listings=[_listing()])
        service = IngestService(_settings(), _ml(), _backend(), scrapers=[scraper])

        report = await service.run(REQUEST_UUID)

        self.assertEqual(scraper.fetch.call_count, 4)
        self.assertEqual(len(report.plan.rubros), 4)


class OnDemandEndpointTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def _service(self):
        service = AsyncMock()
        service.run = AsyncMock(return_value=MagicMock())
        return service

    def test_the_cron_calls_it_with_no_body_at_all(self):
        service = self._service()
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers=HEADERS)

        self.assertIsNone(service.run.await_args.args[1])

    def test_an_on_demand_call_can_scope_the_run(self):
        service = self._service()
        body = {"sources": ["computrabajo"], "rubros": ["TECNOLOGIA"], "jobs_per_rubro": 2}
        with patch("router.ingest.build_service", return_value=service):
            self.client.post(INGEST, headers=HEADERS, json=body)

        overrides = service.run.await_args.args[1]
        self.assertEqual(overrides.sources, ["computrabajo"])
        self.assertEqual(overrides.rubros, ["TECNOLOGIA"])
        self.assertEqual(overrides.jobs_per_rubro, 2)

    def test_an_unknown_source_is_a_400_not_a_500(self):
        service = AsyncMock()
        service.run = AsyncMock(side_effect=ValueError("Unsupported feeder source: 'linkedin'"))
        with patch("router.ingest.build_service", return_value=service):
            response = self.client.post(INGEST, headers=HEADERS, json={"sources": ["linkedin"]})

        self.assertEqual(response.status_code, 400)
        self.assertFalse(response.json()["success"])
        self.assertIn("linkedin", response.json()["reason"])

    def test_an_out_of_range_limit_is_rejected_before_anything_runs(self):
        service = self._service()
        with patch("router.ingest.build_service", return_value=service):
            response = self.client.post(INGEST, headers=HEADERS, json={"jobs_per_rubro": 5000})

        self.assertEqual(response.status_code, 400)
        service.run.assert_not_awaited()

    def test_on_demand_still_requires_the_request_uuid(self):
        response = self.client.post(INGEST, json={"rubros": ["TECNOLOGIA"]})

        self.assertEqual(response.status_code, 403)


if __name__ == "__main__":
    unittest.main()
