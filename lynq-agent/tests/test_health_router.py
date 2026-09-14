from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from tests.support import base_resume  # noqa: F401

from main import app

HEALTH = "/lynq-agent/health"


def _probes(database_up: bool, ml_up: bool):
    return (
        patch(
            "router.health._database_is_reachable",
            AsyncMock(return_value=database_up),
        ),
        patch(
            "router.health.LynqMlClient.is_reachable", AsyncMock(return_value=ml_up)
        ),
    )


class HealthRouterTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_reports_both_dependencies_up(self):
        database, ml = _probes(True, True)
        with database, ml:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["database"]["status"], "UP")
        self.assertEqual(body["ml"]["status"], "UP")

    def test_a_down_dependency_is_surfaced_without_failing_the_probe(self):
        database, ml = _probes(False, True)
        with database, ml:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "UP")
        self.assertEqual(response.json()["database"]["status"], "DOWN")

    def test_the_probe_does_not_require_the_request_uuid_header(self):
        database, ml = _probes(True, True)
        with database, ml:
            response = self.client.get(HEALTH)

        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()
