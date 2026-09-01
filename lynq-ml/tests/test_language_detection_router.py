"""Tests for the ``POST /lynq-ml/dmz/detect-language`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi.testclient import TestClient

from llm_client import LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/dmz/detect-language"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
}

_BODY = {"text": "Ingeniero backend con 6 años de experiencia."}


def _fake_client(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


class DetectLanguageRouterTests(unittest.TestCase):
    """Covers the happy path plus the LLM/validation failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_detected_language_on_valid_output(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"language": "ES"}))

        with patch("router.language_detection.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["data"]["language"], "ES")
        fake.generate.assert_awaited_once()

    def test_prompt_includes_input_text(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"language": "ES"}))

        with patch("router.language_detection.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("Ingeniero backend con 6 años de experiencia.", prompt)

    def test_accepts_every_declared_language(self) -> None:
        for language in ("EN", "ES", "FR", "PR"):
            fake = _fake_client(generate_return=json.dumps({"language": language}))
            with patch(
                "router.language_detection.get_llm_client", return_value=fake
            ):
                response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)
            self.assertEqual(response.status_code, 200, msg=language)
            self.assertEqual(response.json()["data"]["language"], language)

    def test_returns_502_when_llm_request_fails(self) -> None:
        fake = _fake_client(
            generate_side_effect=httpx.ConnectError("connection refused")
        )

        with patch("router.language_detection.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("LLM request failed", response.json()["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        fake = _fake_client(generate_return="not json at all")

        with patch("router.language_detection.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_on_unsupported_language(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"language": "DE"}))

        with patch("router.language_detection.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_missing_required_headers_returns_400(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"language": "ES"}))

        with patch("router.language_detection.get_llm_client", return_value=fake):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")
        fake.generate.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
