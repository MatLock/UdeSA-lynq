"""Tests for the ``require_request_uuid`` middleware."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch


from llm_client import LLMProvider
from tests.support import authenticated_client, clear_overrides
from middleware.request_uuid import REQUEST_UUID_HEADER

_SKILL_ENHANCE = "/lynq-llm/dmz/skill-enhance"

_BODY = {
    "title": "Backend Developer",
    "description": "Python and FastAPI.",
    "work_type": "REMOTE",
}


class RequestUuidMiddlewareTests(unittest.TestCase):
    """Non-exempt paths require the ``lynq-request-uuid`` header."""

    def setUp(self) -> None:
        self.client = authenticated_client()

    def tearDown(self) -> None:
        clear_overrides()

    def test_missing_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(
            _SKILL_ENHANCE,
            json=_BODY,
            headers={"Authorization": "Bearer access-token", "company-id": "c1"},
        )

        self.assertEqual(response.status_code, 403)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn(REQUEST_UUID_HEADER, payload["reason"])

    def test_empty_uuid_header_is_rejected_with_403(self) -> None:
        response = self.client.post(
            _SKILL_ENHANCE,
            json=_BODY,
            headers={
                REQUEST_UUID_HEADER: "",
                "Authorization": "Bearer access-token",
                "company-id": "c1",
            },
        )

        self.assertEqual(response.status_code, 403)

    def test_present_uuid_header_passes_through(self) -> None:
        import json

        fake = MagicMock()
        fake.provider = LLMProvider.OLLAMA
        fake.generate = AsyncMock(return_value=json.dumps({"skills": ["Python"]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(
                _SKILL_ENHANCE,
                json=_BODY,
                headers={
                    REQUEST_UUID_HEADER: "req-1",
                    "Authorization": "Bearer access-token",
                    "company-id": "c1",
                },
            )

        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()