from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from backend_client import BackendError, LivenessStats, VerificationCandidate
from config import Settings
from model import VerifyOverrides
from scraper import LivenessCheck, LivenessOutcome
from service import VerifyService
from service.verify_service import PAUSE_MAX_SECONDS, PAUSE_MIN_SECONDS, polite_async_pause

REQUEST_UUID = "11111111-2222-3333-4444-555555555555"

ALIVE = LivenessCheck(outcome=LivenessOutcome.ALIVE)
CLOSED = LivenessCheck(outcome=LivenessOutcome.CLOSED, reason="estado offline")
GONE = LivenessCheck(outcome=LivenessOutcome.GONE, reason="HTTP 404")
BLOCKED = LivenessCheck(outcome=LivenessOutcome.UNKNOWN, blocked=True, reason="HTTP 429")
TIMEOUT = LivenessCheck(outcome=LivenessOutcome.UNKNOWN, failed=True, reason="Timeout")


def _settings(**overrides) -> Settings:
    settings = Settings()
    settings.sources = overrides.get("sources", ["bumeran", "computrabajo"])
    settings.verify_max_checks = overrides.get("max_checks", 40)
    settings.verify_max_consecutive_failures = overrides.get("max_failures", 3)
    return settings


def _candidate(job_id: str, source: str = "COMPUTRABAJO") -> VerificationCandidate:
    return VerificationCandidate(
        id=job_id,
        jobUrl=f"https://example.test/{source.lower()}/{job_id}",
        source=source,
        category="TECNOLOGIA",
    )


def _checker(source: str, *checks: LivenessCheck, error: Exception | None = None):
    checker = MagicMock()
    checker.source = source
    if error is not None:
        checker.check = MagicMock(side_effect=error)
    else:
        checker.check = MagicMock(side_effect=list(checks))
    return checker


def _backend(candidates, liveness=None, expired=0, report_error=None):
    backend = MagicMock()
    backend.list_verification_candidates = AsyncMock(return_value=candidates)
    backend.report_liveness = AsyncMock(
        return_value=liveness or LivenessStats(), side_effect=report_error
    )
    backend.expire = AsyncMock(return_value=expired)
    return backend


def _reported(backend) -> list[tuple[str, str]]:
    reports = backend.report_liveness.await_args.args[1]
    return [(report.id, report.outcome.value) for report in reports]


class VerifyRunTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.pause = AsyncMock()

    def _service(self, backend, checkers, **settings):
        return VerifyService(_settings(**settings), backend, checkers=checkers, pause=self.pause)

    async def test_checks_every_candidate_reports_the_outcomes_and_then_expires(self):
        backend = _backend(
            [_candidate("a"), _candidate("b", "BUMERAN"), _candidate("c")],
            liveness=LivenessStats(alive=1, closed=1, gone=1),
            expired=5,
        )
        checkers = {
            "computrabajo": _checker("computrabajo", ALIVE, GONE),
            "bumeran": _checker("bumeran", CLOSED),
        }

        report = await self._service(backend, checkers).run(REQUEST_UUID)

        self.assertEqual(_reported(backend), [("a", "ALIVE"), ("b", "CLOSED"), ("c", "GONE")])
        backend.expire.assert_awaited_once_with(REQUEST_UUID)
        self.assertEqual(report.candidates, 3)
        self.assertEqual(report.checked, 3)
        self.assertEqual((report.alive, report.closed, report.gone, report.unknown), (1, 1, 1, 0))
        self.assertEqual(report.expired, 5)
        self.assertEqual(report.applied.alive, 1)
        self.assertEqual(report.blocked_sources, [])
        computrabajo = next(item for item in report.per_source if item.source == "computrabajo")
        self.assertEqual((computrabajo.requested, computrabajo.alive, computrabajo.gone), (2, 1, 1))

    async def test_checks_with_the_url_the_backend_gave(self):
        backend = _backend([_candidate("a")])
        checker = _checker("computrabajo", ALIVE)

        await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        checker.check.assert_called_once_with("https://example.test/computrabajo/a")

    async def test_checks_at_most_the_configured_number_per_run(self):
        backend = _backend([_candidate(str(index)) for index in range(45)])
        checker = _checker("computrabajo", *([ALIVE] * 40))

        report = await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        self.assertEqual(checker.check.call_count, 40)
        self.assertEqual(report.checked, 40)
        self.assertEqual(len(_reported(backend)), 40)
        self.assertEqual(report.candidates, 45)

    async def test_pauses_between_requests_but_not_before_the_first(self):
        backend = _backend([_candidate("a"), _candidate("b"), _candidate("c")])
        checker = _checker("computrabajo", ALIVE, ALIVE, ALIVE)

        await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        self.assertEqual(self.pause.await_count, 2)

    async def test_a_blocked_source_is_cut_and_its_remaining_candidates_reported_unknown(self):
        backend = _backend([
            _candidate("a", "BUMERAN"),
            _candidate("b", "BUMERAN"),
            _candidate("c"),
            _candidate("d", "BUMERAN"),
        ])
        bumeran = _checker("bumeran", BLOCKED)
        checkers = {"bumeran": bumeran, "computrabajo": _checker("computrabajo", ALIVE)}

        report = await self._service(backend, checkers).run(REQUEST_UUID)

        self.assertEqual(bumeran.check.call_count, 1)
        self.assertEqual(
            _reported(backend),
            [("a", "UNKNOWN"), ("b", "UNKNOWN"), ("c", "ALIVE"), ("d", "UNKNOWN")],
        )
        self.assertEqual(report.blocked_sources, ["bumeran"])
        bumeran_report = next(item for item in report.per_source if item.source == "bumeran")
        self.assertEqual(bumeran_report.blocked_reason, "HTTP 429")
        self.assertEqual(bumeran_report.requested, 1)
        self.assertEqual(bumeran_report.unknown, 3)

    async def test_three_network_failures_in_a_row_cut_the_source(self):
        backend = _backend([_candidate(str(index)) for index in range(5)])
        checker = _checker("computrabajo", TIMEOUT, TIMEOUT, TIMEOUT)

        report = await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        self.assertEqual(checker.check.call_count, 3)
        self.assertEqual(report.unknown, 5)
        self.assertEqual(report.blocked_sources, ["computrabajo"])
        self.assertEqual(
            report.per_source[0].blocked_reason, "3 network failures in a row"
        )

    async def test_a_definite_answer_resets_the_failure_streak(self):
        backend = _backend([_candidate(str(index)) for index in range(5)])
        checker = _checker("computrabajo", TIMEOUT, TIMEOUT, ALIVE, TIMEOUT, TIMEOUT)

        report = await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        self.assertEqual(checker.check.call_count, 5)
        self.assertEqual(report.blocked_sources, [])

    async def test_the_failure_threshold_is_configurable(self):
        backend = _backend([_candidate(str(index)) for index in range(3)])
        checker = _checker("computrabajo", TIMEOUT)

        report = await self._service(
            backend, {"computrabajo": checker}, max_failures=1
        ).run(REQUEST_UUID)

        self.assertEqual(checker.check.call_count, 1)
        self.assertEqual(report.blocked_sources, ["computrabajo"])

    async def test_an_unexpected_checker_error_is_an_unknown_failure(self):
        backend = _backend([_candidate("a")])
        checker = _checker("computrabajo", error=RuntimeError("parser blew up"))

        report = await self._service(backend, {"computrabajo": checker}).run(REQUEST_UUID)

        self.assertEqual(_reported(backend), [("a", "UNKNOWN")])
        self.assertEqual(report.unknown, 1)

    async def test_candidates_of_sources_outside_the_run_are_left_alone(self):
        backend = _backend([_candidate("a", "LINKEDIN"), _candidate("b", "BUMERAN"), _candidate("c")])
        checkers = {
            "bumeran": _checker("bumeran", ALIVE),
            "computrabajo": _checker("computrabajo", ALIVE),
        }

        await self._service(backend, checkers).run(
            REQUEST_UUID, VerifyOverrides(sources=["computrabajo"])
        )

        self.assertEqual(_reported(backend), [("c", "ALIVE")])

    async def test_with_no_candidates_nothing_is_reported_but_the_expiry_still_runs(self):
        backend = _backend([], expired=2)

        report = await self._service(backend, {"computrabajo": _checker("computrabajo")}).run(
            REQUEST_UUID
        )

        backend.report_liveness.assert_not_awaited()
        backend.expire.assert_awaited_once()
        self.assertEqual(report.expired, 2)

    async def test_a_rejected_report_aborts_before_expiring(self):
        backend = _backend([_candidate("a")], report_error=BackendError("400"))

        with self.assertRaises(BackendError):
            await self._service(backend, {"computrabajo": _checker("computrabajo", ALIVE)}).run(
                REQUEST_UUID
            )

        backend.expire.assert_not_awaited()


class VerifyPlanTest(unittest.TestCase):

    def test_defaults_to_the_configured_sources_and_cap(self):
        service = VerifyService(_settings(sources=["Bumeran"], max_checks=12), MagicMock())

        plan = service.plan_for(None)

        self.assertEqual(plan.sources, ["bumeran"])
        self.assertEqual(plan.max_checks, 12)

    def test_the_sources_can_be_overridden(self):
        service = VerifyService(_settings(), MagicMock())

        plan = service.plan_for(VerifyOverrides(sources=["computrabajo"]))

        self.assertEqual(plan.sources, ["computrabajo"])

    def test_an_unknown_source_fails_validation(self):
        service = VerifyService(_settings(), MagicMock())

        with self.assertRaises(ValueError):
            service.validate(VerifyOverrides(sources=["linkedin"]))

    def test_validation_builds_the_real_checkers(self):
        service = VerifyService(_settings(), MagicMock())

        self.assertEqual(sorted(service.checkers_for(service.validate())), ["bumeran", "computrabajo"])


class PoliteAsyncPauseTest(unittest.IsolatedAsyncioTestCase):

    async def test_sleeps_a_short_random_while(self):
        with patch("service.verify_service.asyncio.sleep", new=AsyncMock()) as sleep:
            await polite_async_pause()

        delay = sleep.await_args.args[0]
        self.assertTrue(PAUSE_MIN_SECONDS <= delay <= PAUSE_MAX_SECONDS)


if __name__ == "__main__":
    unittest.main()
