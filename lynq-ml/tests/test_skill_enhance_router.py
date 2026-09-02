"""Tests for the ``POST /lynq-ml/dmz/skill-enhance`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from llm_client import LLMError, LLMProvider
from main import app

_ENDPOINT = "/lynq-ml/dmz/skill-enhance"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
    "company-id": "company-1",
}

_BODY = {
    "title": "Senior Backend Java Developer",
    "description": "Building scalable services with Java, Spring and AWS.",
    "work_type": "REMOTE",
}


def _fake_client(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


class SkillEnhanceRouterTests(unittest.TestCase):
    """Covers the happy path plus the LLM/validation failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_skills_on_valid_llm_output(self) -> None:
        skills = ["Java", "Spring", "AWS", "REST", "Docker"]
        fake = _fake_client(generate_return=json.dumps({"skills": skills}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["data"]["skills"], skills)
        fake.generate.assert_awaited_once()

    def test_returns_similarity_tags_alongside_skills(self) -> None:
        similarity_tags = ["Backend Development", "Asynchronous Messaging"]
        fake = _fake_client(
            generate_return=json.dumps({"skills": ["Kafka"], "similarity_tags": similarity_tags})
        )

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["data"]["similarity_tags"], similarity_tags)

    def test_defaults_similarity_tags_to_empty_list_when_the_model_omits_them(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": ["Java"]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["data"]["similarity_tags"], [])

    def test_malformed_similarity_tags_are_dropped_instead_of_failing_the_request(self) -> None:
        # Similarity tags only widen the match, so a bad shape must degrade the response
        # rather than turn a usable skill list into a 502.
        fake = _fake_client(
            generate_return=json.dumps({"skills": ["Java"], "similarity_tags": [1, 2]})
        )

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["data"]["skills"], ["Java"])
        self.assertEqual(payload["data"]["similarity_tags"], [])

    def test_prompt_asks_for_english_similarity_tags(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": ["Java"]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn("similarity_tags", prompt)
        self.assertIn("ALWAYS write similarity tags in English", prompt)
        self.assertIn("Asynchronous Messaging", prompt)

    def test_prompt_is_built_from_request_body(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": ["Java"]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = fake.generate.await_args.args[0]
        self.assertIn(_BODY["title"], prompt)
        self.assertIn(_BODY["description"], prompt)
        self.assertIn(_BODY["work_type"], prompt)

    def test_returns_502_when_llm_request_fails(self) -> None:
        fake = _fake_client(
            generate_side_effect=LLMError("connection refused")
        )

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertIn("LLM request failed", payload["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        fake = _fake_client(generate_return="not json at all")

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_skills_key_missing(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"other": []}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_skills_is_not_list_of_strings(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": [1, 2, 3]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_missing_required_headers_returns_400(self) -> None:
        fake = _fake_client(generate_return=json.dumps({"skills": ["Java"]}))

        with patch("router.skill_enhance.get_llm_client", return_value=fake):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        # Missing user-id / company-id headers fail validation, which the app's
        # RequestValidationError handler maps to a 400 error envelope.
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")
        fake.generate.assert_not_awaited()

    def test_invalid_work_type_returns_400(self) -> None:
        body = {**_BODY, "work_type": "HYBRID"}

        response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["reason"], "Invalid Fields Found")
        self.assertIn("work_type", payload["data"])


if __name__ == "__main__":
    unittest.main()