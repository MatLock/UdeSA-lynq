from __future__ import annotations

import asyncio
import logging
from typing import Optional

from pydantic import BaseModel, Field

from backend_client import BackendClient, BackendError, IngestStats
from config import Settings
from ml_client import MlClient, MlError
from model import IngestOverrides, RunPlan
from scraper import Listing, Scraper, get_scrapers

log = logging.getLogger(__name__)

REMOTE = "REMOTE"
IN_OFFICE = "IN_OFFICE"


class SourceReport(BaseModel):
    source: str
    rubro: str
    fetched: int = 0
    error: Optional[str] = None


class IngestReport(BaseModel):
    plan: Optional[RunPlan] = None
    fetched: int = 0
    deduplicated: int = 0
    enriched: int = 0
    enrichment_failed: int = 0
    ingested: IngestStats = Field(default_factory=IngestStats)
    per_source: list[SourceReport] = Field(default_factory=list)


def _dedupe(listings: list[Listing]) -> list[Listing]:
    seen: set[tuple[str, str]] = set()
    unique: list[Listing] = []
    for listing in listings:
        key = (listing.source, listing.external_id)
        if key in seen:
            continue
        seen.add(key)
        unique.append(listing)
    return unique


class IngestService:

    def __init__(
        self,
        settings: Settings,
        ml_client: MlClient,
        backend_client: BackendClient,
        scrapers: Optional[list[Scraper]] = None,
    ) -> None:
        self.settings = settings
        self.ml_client = ml_client
        self.backend_client = backend_client
        self._scrapers = scrapers

    def plan_for(self, overrides: Optional[IngestOverrides]) -> RunPlan:
        overrides = overrides or IngestOverrides()
        return RunPlan(
            sources=overrides.sources or self.settings.sources,
            rubros=overrides.rubros or self.settings.rubros,
            jobs_per_rubro=overrides.jobs_per_rubro or self.settings.jobs_per_rubro,
        )

    def scrapers_for(self, plan: RunPlan) -> list[Scraper]:
        if self._scrapers is not None:
            return self._scrapers
        return get_scrapers(plan.sources, self.settings.scrape_timeout)

    async def _scrape_all(self, plan: RunPlan, report: IngestReport) -> list[Listing]:
        collected: list[Listing] = []
        for scraper in self.scrapers_for(plan):
            for rubro in plan.rubros:
                entry = SourceReport(source=scraper.source, rubro=rubro)
                try:
                    found = await asyncio.to_thread(scraper.fetch, rubro, plan.jobs_per_rubro)
                    collected.extend(found)
                    entry.fetched = len(found)
                except Exception as exc:  # NOSONAR
                    entry.error = str(exc)
                    log.error(
                        "message= Scraper failed, continuing with the other rubros, "
                        "source=%s, rubro=%s",
                        scraper.source,
                        rubro,
                        exc_info=exc,
                    )
                report.per_source.append(entry)
        return collected

    async def _enrich(self, request_uuid: str, listing: Listing) -> bool:
        if not listing.description:
            return False
        work_type = REMOTE if listing.remote else IN_OFFICE
        try:
            result = await self.ml_client.skill_enhance(
                request_uuid, listing.title, listing.description, work_type
            )
        except MlError as exc:
            log.warning(
                "message= Skill extraction failed, ingesting the listing without tags, "
                "source=%s, external_id=%s, error=%s",
                listing.source,
                listing.external_id,
                exc,
            )
            return False

        listing.skills = result.skills
        listing.similarity_tags = result.similarity_tags
        return not result.is_empty

    async def _enrich_all(self, request_uuid: str, listings: list[Listing]) -> int:
        semaphore = asyncio.Semaphore(max(1, self.settings.ml_concurrency))

        async def guarded(listing: Listing) -> bool:
            async with semaphore:
                return await self._enrich(request_uuid, listing)

        outcomes = await asyncio.gather(*(guarded(listing) for listing in listings))
        return sum(1 for enriched in outcomes if enriched)

    async def run(
        self, request_uuid: str, overrides: Optional[IngestOverrides] = None
    ) -> IngestReport:
        plan = self.plan_for(overrides)
        report = IngestReport(plan=plan)

        log.info(
            "message= Started feeder run, sources=%s, rubros=%s, jobs_per_rubro=%s",
            plan.sources,
            plan.rubros,
            plan.jobs_per_rubro,
        )

        collected = await self._scrape_all(plan, report)
        report.fetched = len(collected)

        listings = _dedupe(collected)
        report.deduplicated = report.fetched - len(listings)

        if not listings:
            log.info("message= Nothing to ingest, the run found no listings")
            return report

        report.enriched = await self._enrich_all(request_uuid, listings)
        report.enrichment_failed = len(listings) - report.enriched

        try:
            report.ingested = await self.backend_client.ingest(request_uuid, listings)
        except BackendError as exc:
            log.error("message= Job post ingest failed", exc_info=exc)
            raise

        log.info(
            "message= Finished feeder run, fetched=%s, deduplicated=%s, enriched=%s, "
            "ingested_jobs=%s, ingested_skills=%s, ingested_similarity_tags=%s",
            report.fetched,
            report.deduplicated,
            report.enriched,
            report.ingested.jobs,
            report.ingested.skills,
            report.ingested.similarity_tags,
        )
        return report
