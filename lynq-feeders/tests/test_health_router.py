from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from main import app

HEALTH = "/lynq-feeders/health"


def _reachability(llm_up: bool, backend_up: bool):
    return (
        patch("router.health.LlmClient.is_reachable", AsyncMock(return_value=llm_up)),
        patch("router.health.BackendClient.is_reachable", AsyncMock(return_value=backend_up)),
    )


class HealthRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_reports_both_dependencies_up(self):
        llm, backend = _reachability(True, True)
        with llm, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["llm"]["status"], "UP")
        self.assertEqual(body["backend"]["status"], "UP")

    def test_a_down_dependency_is_surfaced_without_failing_the_probe(self):
        llm, backend = _reachability(False, True)
        with llm, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["llm"]["status"], "DOWN")

    def test_the_probe_does_not_require_the_request_uuid_header(self):
        llm, backend = _reachability(True, True)
        with llm, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)

    def test_the_probe_is_not_wrapped_in_the_rest_envelope(self):
        llm, backend = _reachability(True, True)
        with llm, backend:
            body = self.client.get(HEALTH).json()

        self.assertNotIn("success", body)
        self.assertNotIn("data", body)


if __name__ == "__main__":
    unittest.main()
