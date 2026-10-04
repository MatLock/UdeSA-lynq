from __future__ import annotations

import asyncio
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from backend_client import BackendError
from main import app
from model import VerifyPlan
from router.verify import build_verify_service, run_verify, verify_guard
from service import VerifyReport

VERIFY = "/lynq-feeders/verify"
HEADERS = {"lynq-request-uuid": "11111111-2222-3333-4444-555555555555"}


def _plan() -> VerifyPlan:
    return VerifyPlan(sources=["bumeran", "computrabajo"], max_checks=40)


def _service(run_return=None, run_error=None, validate_error=None):
    service = AsyncMock()
    service.validate = MagicMock(return_value=_plan(), side_effect=validate_error)
    service.run = AsyncMock(return_value=run_return or VerifyReport(), side_effect=run_error)
    return service


class VerifyRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)
        self.addCleanup(verify_guard.finish)

    def test_accepts_the_run_with_a_202_and_no_body(self):
        with patch("router.verify.build_verify_service", return_value=_service()):
            response = self.client.post(VERIFY, headers=HEADERS)

        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.content, b"")

    def test_the_run_happens_in_the_background(self):
        service = _service()
        with patch("router.verify.build_verify_service", return_value=service):
            with patch("router.verify.BackgroundTasks.add_task") as add_task:
                self.client.post(VERIFY, headers=HEADERS)

        service.run.assert_not_awaited()
        add_task.assert_called_once()

    def test_passes_the_request_uuid_and_the_sources_down_to_the_service(self):
        service = _service()
        with patch("router.verify.build_verify_service", return_value=service):
            self.client.post(VERIFY, headers=HEADERS, json={"sources": ["computrabajo"]})

        request_uuid, overrides = service.run.await_args.args
        self.assertEqual(request_uuid, HEADERS["lynq-request-uuid"])
        self.assertEqual(overrides.sources, ["computrabajo"])

    def test_an_unknown_source_is_a_400_and_nothing_starts(self):
        service = _service(validate_error=ValueError("Unsupported feeder source: 'linkedin'"))
        with patch("router.verify.build_verify_service", return_value=service):
            response = self.client.post(VERIFY, headers=HEADERS, json={"sources": ["linkedin"]})

        self.assertEqual(response.status_code, 400)
        self.assertIn("linkedin", response.json()["reason"])
        service.run.assert_not_awaited()

    def test_a_second_run_is_rejected_while_one_is_in_progress(self):
        verify_guard.start()
        with patch("router.verify.build_verify_service", return_value=_service()) as build:
            response = self.client.post(VERIFY, headers=HEADERS)

        self.assertEqual(response.status_code, 409)
        self.assertIn("already in progress", response.json()["reason"])
        build.return_value.run.assert_not_awaited()

    def test_a_failing_backend_does_not_change_the_accepted_response_and_frees_the_guard(self):
        with patch(
            "router.verify.build_verify_service",
            return_value=_service(run_error=BackendError("refused")),
        ):
            first = self.client.post(VERIFY, headers=HEADERS)
            second = self.client.post(VERIFY, headers=HEADERS)

        self.assertEqual(first.status_code, 202)
        self.assertEqual(second.status_code, 202)

    def test_an_unexpected_failure_is_swallowed_by_the_background_task(self):
        with patch(
            "router.verify.build_verify_service",
            return_value=_service(run_error=RuntimeError("boom")),
        ):
            response = self.client.post(VERIFY, headers=HEADERS)

        self.assertEqual(response.status_code, 202)

    def test_missing_request_uuid_is_rejected(self):
        self.assertEqual(self.client.post(VERIFY).status_code, 403)


class RunVerifyTest(unittest.IsolatedAsyncioTestCase):

    async def test_waits_for_the_feeder_run_in_progress(self):
        lock = asyncio.Lock()
        service = _service()
        verify_guard.start()
        with patch("router.run_control.feeder_runs", lock):
            await lock.acquire()
            task = asyncio.create_task(run_verify(service, HEADERS["lynq-request-uuid"], None))
            await asyncio.sleep(0)
            service.run.assert_not_awaited()

            lock.release()
            await task

        service.run.assert_awaited_once()
        self.assertTrue(verify_guard.start())
        verify_guard.finish()


class BuildVerifyServiceTest(unittest.TestCase):

    def test_wires_the_backend_client_and_the_limits_from_the_environment(self):
        env = {
            "LYNQ_BACKEND_URL": "http://backend:8080/lynq-backend-app",
            "LYNQ_INTERNAL_TOKEN": "configured",
            "FEEDER_SOURCES": "computrabajo",
            "VERIFY_MAX_CHECKS": "12",
            "VERIFY_MAX_CONSECUTIVE_FAILURES": "2",
        }
        with patch.dict("os.environ", env, clear=True):
            service = build_verify_service()

        self.assertEqual(service.backend_client.base_url, "http://backend:8080/lynq-backend-app")
        self.assertEqual(service.backend_client.internal_token, "configured")
        self.assertEqual(service.plan_for(None).max_checks, 12)
        self.assertEqual(service.settings.verify_max_consecutive_failures, 2)
        self.assertEqual(list(service.checkers_for(service.plan_for(None))), ["computrabajo"])


if __name__ == "__main__":
    unittest.main()
