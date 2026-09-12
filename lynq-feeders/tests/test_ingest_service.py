from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock

from backend_client import BackendError, IngestStats
from config import Settings
from ml_client import MlError, SkillEnhanceResult
from scraper.base import Listing
from service import IngestService

REQUEST_UUID = "11111111-2222-3333-4444-555555555555"


def _settings(**overrides) -> Settings:
    settings = Settings()
    settings.rubros = overrides.get("rubros", ["TECNOLOGIA"])
    settings.sources = overrides.get("sources", ["bumeran"])
    settings.jobs_per_rubro = overrides.get("jobs_per_rubro", 10)
    settings.ml_concurrency = overrides.get("ml_concurrency", 2)
    return settings


def _listing(external_id="1", source="bumeran", description="Python y FastAPI.") -> Listing:
    return Listing(
        external_id=external_id,
        title="Backend Developer",
        source=source,
        rubro="TECNOLOGIA",
        description=description,
    )


def _scraper(source="bumeran", listings=None, error=None):
    scraper = MagicMock()
    scraper.source = source
    scraper.fetch = MagicMock(return_value=listings or [], side_effect=error)
    return scraper


def _ml(result=None, error=None):
    ml = MagicMock()
    ml.skill_enhance = AsyncMock(
        return_value=result or SkillEnhanceResult(["Python"], ["Backend"]), side_effect=error
    )
    return ml


def _backend(stats=None, error=None):
    backend = MagicMock()
    backend.ingest = AsyncMock(return_value=stats or IngestStats(jobs=1), side_effect=error)
    return backend


class RunTest(unittest.IsolatedAsyncioTestCase):

    async def test_scrapes_enriches_and_ingests(self):
        backend = _backend(IngestStats(jobs=1, companies=1, skills=1, similarityTags=1))
        service = IngestService(
            _settings(), _ml(), backend, scrapers=[_scraper(listings=[_listing()])]
        )

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.fetched, 1)
        self.assertEqual(report.enriched, 1)
        self.assertEqual(report.enrichment_failed, 0)
        self.assertEqual(report.ingested.jobs, 1)
        backend.ingest.assert_awaited_once()

    async def test_enrichment_writes_skills_onto_the_listing(self):
        listing = _listing()
        service = IngestService(
            _settings(),
            _ml(SkillEnhanceResult(["Python", "FastAPI"], ["Backend Development"])),
            _backend(),
            scrapers=[_scraper(listings=[listing])],
        )

        await service.run(REQUEST_UUID)

        self.assertEqual(listing.skills, ["Python", "FastAPI"])
        self.assertEqual(listing.similarity_tags, ["Backend Development"])

    async def test_scrapes_every_rubro_for_every_source(self):
        bumeran = _scraper("bumeran", [_listing("1")])
        computrabajo = _scraper("computrabajo", [_listing("2", "computrabajo")])
        settings = _settings(rubros=["TECNOLOGIA", "CONTABILIDAD"], sources=["bumeran"])
        service = IngestService(
            settings, _ml(), _backend(), scrapers=[bumeran, computrabajo]
        )

        report = await service.run(REQUEST_UUID)

        self.assertEqual(bumeran.fetch.call_count, 2)
        self.assertEqual(computrabajo.fetch.call_count, 2)
        self.assertEqual(len(report.per_source), 4)

    async def test_passes_the_per_rubro_limit_to_the_scraper(self):
        scraper = _scraper(listings=[_listing()])
        service = IngestService(
            _settings(jobs_per_rubro=3), _ml(), _backend(), scrapers=[scraper]
        )

        await service.run(REQUEST_UUID)

        self.assertEqual(scraper.fetch.call_args.args, ("TECNOLOGIA", 3))

    async def test_duplicates_across_rubros_are_collapsed(self):
        settings = _settings(rubros=["TECNOLOGIA", "CONTABILIDAD"])
        scraper = _scraper(listings=[_listing("same")])
        backend = _backend()
        service = IngestService(settings, _ml(), backend, scrapers=[scraper])

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.fetched, 2)
        self.assertEqual(report.deduplicated, 1)
        self.assertEqual(len(backend.ingest.call_args.args[1]), 1)

    async def test_same_id_from_different_sources_is_kept(self):
        scraper = _scraper(listings=[_listing("1", "bumeran"), _listing("1", "computrabajo")])
        backend = _backend()
        service = IngestService(_settings(), _ml(), backend, scrapers=[scraper])

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.deduplicated, 0)
        self.assertEqual(len(backend.ingest.call_args.args[1]), 2)

    async def test_a_failing_ml_call_still_ingests_the_listing(self):
        backend = _backend()
        service = IngestService(
            _settings(),
            _ml(error=MlError("ollama down")),
            backend,
            scrapers=[_scraper(listings=[_listing()])],
        )

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.enriched, 0)
        self.assertEqual(report.enrichment_failed, 1)
        ingested = backend.ingest.call_args.args[1]
        self.assertEqual(len(ingested), 1)
        self.assertEqual(ingested[0].skills, [])

    async def test_listings_without_a_description_skip_the_llm(self):
        ml = _ml()
        service = IngestService(
            _settings(), ml, _backend(), scrapers=[_scraper(listings=[_listing(description=None)])]
        )

        report = await service.run(REQUEST_UUID)

        ml.skill_enhance.assert_not_awaited()
        self.assertEqual(report.enrichment_failed, 1)

    async def test_a_failing_scraper_does_not_abort_the_other_rubros(self):
        failing = _scraper("bumeran", error=RuntimeError("cloudflare"))
        working = _scraper("computrabajo", [_listing("2", "computrabajo")])
        service = IngestService(_settings(), _ml(), _backend(), scrapers=[failing, working])

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.fetched, 1)
        errors = [entry for entry in report.per_source if entry.error]
        self.assertEqual(len(errors), 1)
        self.assertIn("cloudflare", errors[0].error)

    async def test_a_run_that_finds_nothing_does_not_call_the_backend(self):
        backend = _backend()
        service = IngestService(_settings(), _ml(), backend, scrapers=[_scraper(listings=[])])

        report = await service.run(REQUEST_UUID)

        self.assertEqual(report.fetched, 0)
        backend.ingest.assert_not_awaited()

    async def test_a_failing_ingest_propagates(self):
        service = IngestService(
            _settings(),
            _ml(),
            _backend(error=BackendError("401")),
            scrapers=[_scraper(listings=[_listing()])],
        )

        with self.assertRaises(BackendError):
            await service.run(REQUEST_UUID)


if __name__ == "__main__":
    unittest.main()
