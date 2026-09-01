"""Tests for the ``POST /lynq-ml/dmz/resume/skill-extraction`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from llm_client import LLMError, LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/dmz/resume/skill-extraction"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
}

_BODY = {
    "personal_info": {"full_name": "Juan Pérez", "headline": "Senior Backend Engineer"},
    "summary": "Ingeniero backend con 6 años de experiencia.",
    "work_experience": [
        {
            "company": "Mendel",
            "position": "Senior Backend Engineer",
            "technologies": ["Java", "Spring Boot"],
        }
    ],
    "skills": {"technical": ["Java", "AWS"], "tools": ["Docker"], "soft": ["Liderazgo"]},
}


def _fake_client(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


class SkillExtractionRouterTests(unittest.TestCase):
    """Covers the happy path plus the LLM/validation failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_bucketed_skills_on_valid_llm_output(self) -> None:
        payload = {
            "skills": ["Java", "Spring Boot", "AWS"],
            "tools": ["Docker"],
            "soft": ["Liderazgo"],
        }
        fake = _fake_client(generate_return=json.dumps(payload))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertTrue(body["success"])
        self.assertEqual(body["data"], payload)
        fake.generate.assert_awaited_once()

    def test_defaults_missing_buckets_to_empty_lists(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": ["Java"]}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json()["data"],
            {"skills": ["Java"], "tools": [], "soft": []},
        )

    def test_prompt_is_built_from_resume_body(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("Juan Pérez", prompt)
        self.assertIn("Spring Boot", prompt)

    def test_language_param_drives_the_prompt_language(self) -> None:
        # A Spanish resume with the param set to English must ask for English
        # soft skills — the resume's own language must not win.
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            self.client.post(
                _ENDPOINT,
                json=_BODY,
                headers=_HEADERS,
                params={"language": "en"},
            )

        prompt = fake.generate.await_args.args[0]
        self.assertIn("English", prompt)
        self.assertNotIn("ONLY in Spanish", prompt)

    def test_language_param_is_resolved_to_a_language_name(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            self.client.post(
                _ENDPOINT,
                json=_BODY,
                headers=_HEADERS,
                params={"language": "es"},
            )

        prompt = fake.generate.await_args.args[0]
        self.assertIn("Spanish", prompt)

    def test_language_defaults_to_english_when_param_is_absent(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("English", prompt)

    def test_returns_502_when_llm_request_fails(self) -> None:
        fake = _fake_client(
            generate_side_effect=LLMError("connection refused")
        )

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        body = response.json()
        self.assertFalse(body["success"])
        self.assertIn("LLM request failed", body["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        fake = _fake_client(generate_return="not json at all")

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_bucket_is_not_list_of_strings(self) -> None:
        fake = _fake_client(
            generate_return=json.dumps({"skills": [1, 2, 3], "tools": [], "soft": []})
        )

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_missing_request_uuid_returns_403(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers={"user-id": "user-1"})

        self.assertEqual(response.status_code, 403)
        fake.generate.assert_not_awaited()

    def test_missing_user_id_header_returns_400(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [], "tools": [], "soft": []}))

        with patch("router.user_resume_skill_extraction.get_llm_client", return_value=fake):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        # Missing user-id header fails validation, which the app's
        # RequestValidationError handler maps to a 400 error envelope.
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")
        fake.generate.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
