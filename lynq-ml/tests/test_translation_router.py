"""Tests for the ``POST /lynq-ml/translate`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi.testclient import TestClient

from llm_client import LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/translate"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
}

_RESUME = {
    "personal_info": {
        "full_name": "John Doe",
        "headline": "Senior Backend Engineer",
    },
    "summary": "Backend engineer with 6 years of experience.",
    "skills": {"technical": ["Java", "Spring Boot"], "soft": ["Leadership"]},
}

_TRANSLATED = {
    "personal_info": {
        "full_name": "John Doe",
        "headline": "Ingeniero Backend Senior",
    },
    "summary": "Ingeniero backend con 6 años de experiencia.",
    "skills": {"technical": ["Java", "Spring Boot"], "soft": ["Liderazgo"]},
}

_BODY = {"resume": _RESUME, "language": "ES"}


def _fake_client(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


class TranslateRouterTests(unittest.TestCase):
    """Covers the happy path plus the LLM/validation/enum failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_translated_resume_on_valid_output(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_TRANSLATED))

        with patch("translation.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        data = payload["data"]
        self.assertEqual(data["personal_info"]["headline"], "Ingeniero Backend Senior")
        self.assertEqual(data["skills"]["soft"], ["Liderazgo"])
        fake.generate.assert_awaited_once()

    def test_prompt_includes_resume_and_target_language(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_TRANSLATED))

        with patch("translation.router.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("Senior Backend Engineer", prompt)  # resume value
        self.assertIn("Spanish", prompt)  # ES enum -> full language name

    def test_rejects_unsupported_language_with_400(self) -> None:
        body = {"resume": _RESUME, "language": "DE"}

        response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["reason"], "Invalid Fields Found")
        self.assertIn("language", payload["data"])

    def test_accepts_every_declared_language(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_TRANSLATED))

        for language in ("EN", "ES", "FR", "PR"):
            with patch("translation.router.get_llm_client", return_value=fake):
                response = self.client.post(
                    _ENDPOINT,
                    json={"resume": _RESUME, "language": language},
                    headers=_HEADERS,
                )
            self.assertEqual(response.status_code, 200, msg=language)

    def test_returns_502_when_llm_request_fails(self) -> None:
        fake = _fake_client(
            generate_side_effect=httpx.ConnectError("connection refused")
        )

        with patch("translation.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("LLM request failed", response.json()["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        fake = _fake_client(generate_return="not json at all")

        with patch("translation.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_on_schema_mismatch(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": "not-an-object"}))

        with patch("translation.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_missing_required_headers_returns_400(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_TRANSLATED))

        with patch("translation.router.get_llm_client", return_value=fake):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")
        fake.generate.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
