from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from main import app

HEALTH = "/lynq-feeders/health"


def _reachability(ml_up: bool, backend_up: bool):
    return (
        patch("router.health.MlClient.is_reachable", AsyncMock(return_value=ml_up)),
        patch("router.health.BackendClient.is_reachable", AsyncMock(return_value=backend_up)),
    )


class HealthRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_reports_both_dependencies_up(self):
        ml, backend = _reachability(True, True)
        with ml, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["ml"]["status"], "UP")
        self.assertEqual(body["backend"]["status"], "UP")

    def test_a_down_dependency_is_surfaced_without_failing_the_probe(self):
        ml, backend = _reachability(False, True)
        with ml, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["ml"]["status"], "DOWN")

    def test_the_probe_does_not_require_the_request_uuid_header(self):
        ml, backend = _reachability(True, True)
        with ml, backend:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)

    def test_the_probe_is_not_wrapped_in_the_rest_envelope(self):
        ml, backend = _reachability(True, True)
        with ml, backend:
            body = self.client.get(HEALTH).json()

        self.assertNotIn("success", body)
        self.assertNotIn("data", body)


if __name__ == "__main__":
    unittest.main()
