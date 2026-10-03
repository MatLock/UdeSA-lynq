from __future__ import annotations

import asyncio
import logging
import random
from typing import Awaitable, Callable, Optional

from pydantic import BaseModel, Field

from backend_client import (
    BackendClient,
    LivenessReport,
    LivenessStats,
    VerificationCandidate,
)
from config import Settings
from model import VerifyOverrides, VerifyPlan
from scraper import LivenessCheck, LivenessChecker, LivenessOutcome, get_liveness_checkers
from scraper.base import unreachable

log = logging.getLogger(__name__)

PAUSE_MIN_SECONDS = 1.0
PAUSE_MAX_SECONDS = 2.5


async def polite_async_pause() -> None:
    await asyncio.sleep(random.uniform(PAUSE_MIN_SECONDS, PAUSE_MAX_SECONDS))  # NOSONAR


class SourceVerifyReport(BaseModel):
    source: str
    requested: int = 0
    alive: int = 0
    closed: int = 0
    gone: int = 0
    unknown: int = 0
    blocked_reason: Optional[str] = None


class VerifyReport(BaseModel):
    plan: Optional[VerifyPlan] = None
    candidates: int = 0
    checked: int = 0
    alive: int = 0
    closed: int = 0
    gone: int = 0
    unknown: int = 0
    expired: int = 0
    blocked_sources: list[str] = Field(default_factory=list)
    per_source: list[SourceVerifyReport] = Field(default_factory=list)
    applied: LivenessStats = Field(default_factory=LivenessStats)


def _increment(report: BaseModel, outcome: LivenessOutcome) -> None:
    field = outcome.value.lower()
    setattr(report, field, getattr(report, field) + 1)


class _SourceRun:

    def __init__(self, checker: LivenessChecker, max_consecutive_failures: int) -> None:
        self.checker = checker
        self.report = SourceVerifyReport(source=checker.source)
        self.max_consecutive_failures = max(1, max_consecutive_failures)
        self.consecutive_failures = 0

    @property
    def blocked(self) -> bool:
        return self.report.blocked_reason is not None

    def record(self, check: LivenessCheck) -> None:
        self.report.requested += 1
        if check.blocked:
            self.report.blocked_reason = check.reason or "blocked by the portal"
        elif check.failed:
            self.consecutive_failures += 1
            if self.consecutive_failures >= self.max_consecutive_failures:
                self.report.blocked_reason = (
                    f"{self.consecutive_failures} network failures in a row"
                )
        else:
            self.consecutive_failures = 0


class VerifyService:

    def __init__(
        self,
        settings: Settings,
        backend_client: BackendClient,
        checkers: Optional[dict[str, LivenessChecker]] = None,
        pause: Callable[[], Awaitable[None]] = polite_async_pause,
    ) -> None:
        self.settings = settings
        self.backend_client = backend_client
        self._checkers = checkers
        self._pause = pause

    def plan_for(self, overrides: Optional[VerifyOverrides]) -> VerifyPlan:
        overrides = overrides or VerifyOverrides()
        sources = overrides.sources or self.settings.sources
        return VerifyPlan(
            sources=[source.strip().lower() for source in sources],
            max_checks=max(0, self.settings.verify_max_checks),
        )

    def checkers_for(self, plan: VerifyPlan) -> dict[str, LivenessChecker]:
        if self._checkers is not None:
            return {
                source: checker
                for source, checker in self._checkers.items()
                if source in plan.sources
            }
        return get_liveness_checkers(plan.sources, self.settings.scrape_timeout)

    def validate(self, overrides: Optional[VerifyOverrides] = None) -> VerifyPlan:
        plan = self.plan_for(overrides)
        self.checkers_for(plan)
        return plan

    async def run(
        self, request_uuid: str, overrides: Optional[VerifyOverrides] = None
    ) -> VerifyReport:
        plan = self.plan_for(overrides)
        runs = {
            source: _SourceRun(checker, self.settings.verify_max_consecutive_failures)
            for source, checker in self.checkers_for(plan).items()
        }
        report = VerifyReport(plan=plan)
        log.info(
            "message= Started feeder verify run, sources=%s, max_checks=%s",
            plan.sources,
            plan.max_checks,
        )

        candidates = await self.backend_client.list_verification_candidates(request_uuid)
        report.candidates = len(candidates)
        selected = [
            candidate for candidate in candidates if candidate.source.lower() in runs
        ][: plan.max_checks]

        reports: list[LivenessReport] = []
        for candidate in selected:
            source_run = runs[candidate.source.lower()]
            requested_before = any(run.report.requested for run in runs.values())
            outcome = await self._verify(candidate, source_run, requested_before)
            reports.append(LivenessReport(id=candidate.id, outcome=outcome))
            _increment(report, outcome)
            _increment(source_run.report, outcome)
        report.checked = len(reports)

        if reports:
            report.applied = await self.backend_client.report_liveness(request_uuid, reports)
        report.expired = await self.backend_client.expire(request_uuid)
        report.blocked_sources = [source for source, run in runs.items() if run.blocked]
        report.per_source = [run.report for run in runs.values()]

        for source_report in report.per_source:
            log.info(
                "message= Verified source, source=%s, requested=%s, alive=%s, closed=%s, "
                "gone=%s, unknown=%s, blocked_reason=%s",
                source_report.source,
                source_report.requested,
                source_report.alive,
                source_report.closed,
                source_report.gone,
                source_report.unknown,
                source_report.blocked_reason,
            )
        log.info(
            "message= Finished feeder verify run, candidates=%s, checked=%s, alive=%s, "
            "closed=%s, gone=%s, unknown=%s, skipped_by_backend=%s, expired=%s, "
            "blocked_sources=%s",
            report.candidates,
            report.checked,
            report.alive,
            report.closed,
            report.gone,
            report.unknown,
            report.applied.skipped,
            report.expired,
            report.blocked_sources,
        )
        return report

    async def _verify(
        self, candidate: VerificationCandidate, source_run: _SourceRun, requested_before: bool
    ) -> LivenessOutcome:
        if source_run.blocked:
            return LivenessOutcome.UNKNOWN
        if requested_before:
            await self._pause()
        try:
            check = await asyncio.to_thread(source_run.checker.check, candidate.job_url)
        except Exception as exc:  # NOSONAR
            check = unreachable(exc)
        source_run.record(check)
        log.info(
            "message= Checked job post liveness, source=%s, job_post_id=%s, outcome=%s, "
            "reason=%s",
            source_run.report.source,
            candidate.id,
            check.outcome.value,
            check.reason,
        )
        if source_run.blocked:
            log.warning(
                "message= Cutting the source for the rest of the run, source=%s, reason=%s",
                source_run.report.source,
                source_run.report.blocked_reason,
            )
        return check.outcome
