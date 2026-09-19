from __future__ import annotations

import unittest

from fastapi.testclient import TestClient

from main import app

_ENDPOINT = "/lynq-agent/health"


class HealthEndpointTests(unittest.TestCase):

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_200_and_up(self) -> None:
        response = self.client.get(_ENDPOINT)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "UP"})

    def test_health_is_exempt_from_request_uuid_header(self) -> None:
        response = self.client.get(_ENDPOINT, headers={})

        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()
