from __future__ import annotations

import unittest

from fastapi.testclient import TestClient

from main import app
from middleware.request_uuid import REQUEST_UUID_HEADER

_DMZ_PATH = "/lynq-agent/dmz/conversation"


class RequestUuidMiddlewareTests(unittest.TestCase):

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_missing_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(_DMZ_PATH, json={}, headers={"user-id": "u1"})

        self.assertEqual(response.status_code, 403)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn(REQUEST_UUID_HEADER, payload["reason"])

    def test_empty_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(
            _DMZ_PATH,
            json={},
            headers={REQUEST_UUID_HEADER: "", "user-id": "u1"},
        )

        self.assertEqual(response.status_code, 403)

    def test_present_uuid_header_passes_the_middleware(self) -> None:
        response = self.client.post(
            _DMZ_PATH,
            json={},
            headers={REQUEST_UUID_HEADER: "req-1", "user-id": "u1"},
        )

        self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()
