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

MAX_REPORTED_FAILURES = 10


class EnrichmentError(RuntimeError):
    pass


class EnrichmentFailure(BaseModel):
    source: str
    external_id: str
    reason: str


class SourceReport(BaseModel):
    source: str
    category: str
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


def _describe(failures: list["EnrichmentFailure"], total: int) -> str:
    shown = failures[:MAX_REPORTED_FAILURES]
    detail = "; ".join(
        f"{failure.source}/{failure.external_id}: {failure.reason}" for failure in shown
    )
    if len(failures) > len(shown):
        detail += f"; and {len(failures) - len(shown)} more"
    return (
        f"skill extraction failed for {len(failures)} of {total} listings, so nothing was "
        f"ingested — a job post stored without skills or similarity tags scores 0 on the "
        f"LyNQ score for every candidate. {detail}"
    )


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
            categories=overrides.categories or self.settings.categories,
            jobs_per_category=overrides.jobs_per_category or self.settings.jobs_per_category,
        )

    def scrapers_for(self, plan: RunPlan) -> list[Scraper]:
        if self._scrapers is not None:
            return self._scrapers
        return get_scrapers(plan.sources, self.settings.scrape_timeout)

    async def run(
        self, request_uuid: str, overrides: Optional[IngestOverrides] = None
    ) -> IngestReport:
        plan = self.plan_for(overrides)
        report = IngestReport(plan=plan)

        log.info(
            "message= Started feeder run, sources=%s, categories=%s, jobs_per_category=%s",
            plan.sources,
            plan.categories,
            plan.jobs_per_category,
        )

        collected = await self._scrape_all(plan, report)
        report.fetched = len(collected)

        listings = _dedupe(collected)
        report.deduplicated = report.fetched - len(listings)

        if not listings:
            log.info("message= Nothing to ingest, the run found no listings")
            return report

        failures = await self._enrich_all(request_uuid, listings)
        report.enriched = len(listings) - len(failures)
        report.enrichment_failed = len(failures)

        if failures:
            raise EnrichmentError(_describe(failures, len(listings)))

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

    async def _scrape_all(self, plan: RunPlan, report: IngestReport) -> list[Listing]:
        collected: list[Listing] = []
        for scraper in self.scrapers_for(plan):
            for category in plan.categories:
                entry = SourceReport(source=scraper.source, category=category)
                try:
                    found = await asyncio.to_thread(scraper.fetch, category, plan.jobs_per_category)
                    collected.extend(found)
                    entry.fetched = len(found)
                except Exception as exc:  # NOSONAR
                    entry.error = str(exc)
                    log.error(
                        "message= Scraper failed, continuing with the other categories, "
                        "source=%s, category=%s",
                        scraper.source,
                        category,
                        exc_info=exc,
                    )
                report.per_source.append(entry)
        return collected

    async def _enrich(self, request_uuid: str, listing: Listing) -> Optional[str]:
        if not listing.description:
            return "the scraper brought back no description to extract skills from"

        work_type = REMOTE if listing.remote else IN_OFFICE
        try:
            result = await self.ml_client.skill_enhance(
                request_uuid, listing.title, listing.description, work_type
            )
        except MlError as exc:
            return f"skill-enhance failed: {exc}"

        listing.skills = result.skills
        listing.similarity_tags = result.similarity_tags

        if result.is_empty:
            return "skill-enhance returned no skills and no similarity tags"
        return None

    async def _enrich_all(
        self, request_uuid: str, listings: list[Listing]
    ) -> list[EnrichmentFailure]:
        semaphore = asyncio.Semaphore(max(1, self.settings.ml_concurrency))

        async def guarded(listing: Listing) -> Optional[str]:
            async with semaphore:
                return await self._enrich(request_uuid, listing)

        reasons = await asyncio.gather(*(guarded(listing) for listing in listings))

        failures = []
        for listing, reason in zip(listings, reasons):
            if reason is None:
                continue
            log.error(
                "message= Skill extraction failed, source=%s, external_id=%s, reason=%s",
                listing.source,
                listing.external_id,
                reason,
            )
            failures.append(
                EnrichmentFailure(
                    source=listing.source, external_id=listing.external_id, reason=reason
                )
            )
        return failures
