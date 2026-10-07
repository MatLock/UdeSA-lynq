from __future__ import annotations

import unittest

from fastapi.testclient import TestClient

from main import app
from middleware.request_uuid import REQUEST_UUID_HEADER
from security import Principal, require_principal

_DMZ_PATH = "/lynq-agent/dmz/conversation"
_BEARER = "Bearer access-token"


class RequestUuidMiddlewareTests(unittest.TestCase):

    def setUp(self) -> None:
        app.dependency_overrides[require_principal] = lambda: Principal(
            id="u1", roles=["R_CANDIDATE"]
        )
        self.client = TestClient(app)

    def tearDown(self) -> None:
        app.dependency_overrides.clear()

    def test_missing_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(_DMZ_PATH, json={}, headers={"Authorization": _BEARER})

        self.assertEqual(response.status_code, 403)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn(REQUEST_UUID_HEADER, payload["reason"])

    def test_empty_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(
            _DMZ_PATH,
            json={},
            headers={REQUEST_UUID_HEADER: "", "Authorization": _BEARER},
        )

        self.assertEqual(response.status_code, 403)

    def test_present_uuid_header_passes_the_middleware(self) -> None:
        response = self.client.post(
            _DMZ_PATH,
            json={},
            headers={REQUEST_UUID_HEADER: "req-1", "Authorization": _BEARER},
        )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")

    def test_the_api_docs_are_served_without_the_header(self) -> None:
        for path in ("/openapi.json", "/docs", "/redoc"):
            with self.subTest(path=path):
                self.assertEqual(self.client.get(path).status_code, 200)


if __name__ == "__main__":
    unittest.main()
