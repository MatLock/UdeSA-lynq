"""Tests for the ``GET /lynq-ml/health`` endpoint."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from llm_client import LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/health"


class HealthEndpointTests(unittest.TestCase):
    """The health probe reports service and LLM status."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_200_and_up_when_llm_reachable(self) -> None:
        fake = MagicMock()
        fake.provider = LLMProvider.OLLAMA
        fake.health_check = AsyncMock(return_value=True)

        with patch("main.get_llm_client", return_value=fake):
            response = self.client.get(_ENDPOINT)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["status"], "UP")
        self.assertEqual(body["llm"], {"provider": "ollama", "status": "UP"})

    def test_returns_503_and_down_when_llm_unreachable(self) -> None:
        fake = MagicMock()
        fake.provider = LLMProvider.OPENAI
        fake.health_check = AsyncMock(return_value=False)

        with patch("main.get_llm_client", return_value=fake):
            response = self.client.get(_ENDPOINT)

        self.assertEqual(response.status_code, 503)
        body = response.json()
        self.assertEqual(body["status"], "DOWN")
        self.assertEqual(body["llm"], {"provider": "openai", "status": "DOWN"})

    def test_returns_503_when_client_misconfigured(self) -> None:
        with patch("main.get_llm_client", side_effect=ValueError("bad config")):
            response = self.client.get(_ENDPOINT)

        self.assertEqual(response.status_code, 503)
        body = response.json()
        self.assertEqual(body["status"], "DOWN")
        self.assertEqual(body["llm"], {"provider": None, "status": "DOWN"})

    def test_health_is_exempt_from_request_uuid_header(self) -> None:
        fake = MagicMock()
        fake.provider = LLMProvider.OLLAMA
        fake.health_check = AsyncMock(return_value=True)

        # No lynq-request-uuid header supplied; middleware must let it through.
        with patch("main.get_llm_client", return_value=fake):
            response = self.client.get(_ENDPOINT)

        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()