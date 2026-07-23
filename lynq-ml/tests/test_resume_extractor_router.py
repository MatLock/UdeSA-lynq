"""Tests for the ``POST /lynq-ml/parse-resume`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi.testclient import TestClient

from llm_client import LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/parse-resume"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
}

_BODY = {"preSignedUrl": "https://s3.example.com/resume.pdf?sig=abc"}

_RESUME_JSON = {
    "personal_info": {
        "full_name": "Juan Pérez",
        "email": "juan.perez@example.com",
        "links": {"github": "github.com/juanperez"},
    },
    "summary": "Backend engineer.",
    "work_experience": [
        {
            "company": "Mendel",
            "position": "Senior Backend Engineer",
            "start_date": "2021-03",
            "is_current": True,
            "achievements": ["Reduced latency 40%"],
            "technologies": ["Java", "Spring Boot"],
        }
    ],
    "skills": {"technical": ["Java", "AWS"], "soft": ["Leadership"]},
}


def _fake_client(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


class ParseResumeRouterTests(unittest.TestCase):
    """Covers the happy path plus the read/LLM/validation failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_structured_resume_on_valid_output(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_RESUME_JSON))

        with patch(
            "resume_extractor.router.read_resume", return_value="CV text"
        ), patch("resume_extractor.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        data = payload["data"]
        self.assertEqual(data["personal_info"]["full_name"], "Juan Pérez")
        self.assertEqual(data["work_experience"][0]["company"], "Mendel")
        self.assertEqual(data["skills"]["technical"], ["Java", "AWS"])
        # Defaulted fields are present even when the model omits them.
        self.assertEqual(data["education"], [])
        self.assertEqual(data["personal_info"]["links"]["linkedin"], None)
        fake.generate.assert_awaited_once()

    def test_prompt_is_built_from_resume_text(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_RESUME_JSON))

        with patch(
            "resume_extractor.router.read_resume", return_value="MY-CV-TEXT"
        ), patch("resume_extractor.router.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("MY-CV-TEXT", prompt)

    def test_read_resume_called_with_presigned_url(self) -> None:
        fake = _fake_client(generate_return=json.dumps(_RESUME_JSON))

        with patch(
            "resume_extractor.router.read_resume", return_value="CV text"
        ) as read_mock, patch(
            "resume_extractor.router.get_llm_client", return_value=fake
        ):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        read_mock.assert_called_once_with(_BODY["preSignedUrl"])

    def test_returns_400_on_unsupported_document(self) -> None:
        with patch(
            "resume_extractor.router.read_resume",
            side_effect=ValueError("Unsupported document format; expected PDF or DOCX."),
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        self.assertFalse(response.json()["success"])

    def test_returns_502_when_download_fails(self) -> None:
        with patch(
            "resume_extractor.router.read_resume",
            side_effect=OSError("connection refused"),
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("Could not read resume", response.json()["reason"])

    def test_returns_502_when_llm_request_fails(self) -> None:
        fake = _fake_client(
            generate_side_effect=httpx.ConnectError("connection refused")
        )

        with patch(
            "resume_extractor.router.read_resume", return_value="CV text"
        ), patch("resume_extractor.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("LLM request failed", response.json()["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        fake = _fake_client(generate_return="not json at all")

        with patch(
            "resume_extractor.router.read_resume", return_value="CV text"
        ), patch("resume_extractor.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_on_schema_mismatch(self) -> None:
        fake = _fake_client(
            generate_return=json.dumps({"work_experience": "not-a-list"})
        )

        with patch(
            "resume_extractor.router.read_resume", return_value="CV text"
        ), patch("resume_extractor.router.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_missing_required_headers_returns_400(self) -> None:
        with patch("resume_extractor.router.read_resume", return_value="CV text"):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")

    def test_missing_presigned_url_returns_400(self) -> None:
        response = self.client.post(_ENDPOINT, json={}, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["reason"], "Invalid Fields Found")


if __name__ == "__main__":
    unittest.main()
