"""Tests for the ``POST /lynq-ml/upskilling_suggestion`` endpoint."""

from __future__ import annotations

import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from fastapi.testclient import TestClient

from llm_client import LLMProvider
from main import app
from udemy_client import Course

_ENDPOINT = "/lynq-ml/upskilling_suggestion"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
    "company-id": "company-1",
}

_BODY = {
    "job": {
        "description": "Backend role needing Kubernetes and GraphQL.",
        "skills": ["Java", "AWS", "Kubernetes", "GraphQL"],
    },
    "candidate": {
        "description": "Junior backend developer.",
        "skills": ["Java", "AWS"],
    },
}

_QUERIES = ["Kubernetes orchestration", "GraphQL APIs"]


def _fake_llm(*, generate_return=None, generate_side_effect=None):
    """Build a stand-in LLM client whose ``generate`` is an AsyncMock."""
    client = MagicMock()
    client.provider = LLMProvider.OLLAMA
    client.generate = AsyncMock(
        return_value=generate_return, side_effect=generate_side_effect
    )
    return client


def _fake_udemy(*, courses=None, search_side_effect=None):
    """Build a stand-in Udemy client whose ``search_courses`` is an AsyncMock."""
    udemy = MagicMock()
    udemy.search_courses = AsyncMock(
        return_value=courses if courses is not None else [],
        side_effect=search_side_effect,
    )
    return udemy


def _llm_output(outcome: str, queries: list[str]) -> str:
    return json.dumps({"outcome": outcome, "search_queries": queries})


class UpskillingRouterTests(unittest.TestCase):
    """Happy path, perfect-match short-circuit, and failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_outcome_and_courses_on_valid_output(self) -> None:
        llm = _fake_llm(generate_return=_llm_output("Close, but improve infra.", _QUERIES))
        udemy = _fake_udemy(
            courses=[Course(title="K8s course", url="https://www.udemy.com/course/k8s/")]
        )

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm), patch(
            "router.upskilling_suggestion.get_course_provider", return_value=udemy
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        data = payload["data"]
        self.assertEqual(data["outcome"], "Close, but improve infra.")
        self.assertEqual([s["query"] for s in data["suggestions"]], _QUERIES)
        self.assertEqual(
            data["suggestions"][0]["courses"][0]["url"],
            "https://www.udemy.com/course/k8s/",
        )
        # One Udemy search per query.
        self.assertEqual(udemy.search_courses.await_count, len(_QUERIES))

    def test_prompt_is_built_from_request_body(self) -> None:
        llm = _fake_llm(generate_return=_llm_output("You are perfect for this role.", []))

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm), patch(
            "router.upskilling_suggestion.get_course_provider"
        ):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        prompt = llm.generate.await_args.args[0]
        self.assertIn(_BODY["job"]["description"], prompt)
        self.assertIn(_BODY["candidate"]["description"], prompt)
        self.assertIn("Kubernetes", prompt)

    def test_perfect_match_returns_no_suggestions_and_skips_udemy(self) -> None:
        llm = _fake_llm(generate_return=_llm_output("You are perfect for this role.", []))

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm), patch(
            "router.upskilling_suggestion.get_course_provider"
        ) as get_udemy:
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        data = response.json()["data"]
        self.assertEqual(data["outcome"], "You are perfect for this role.")
        self.assertEqual(data["suggestions"], [])
        # No queries → Udemy client never even constructed.
        get_udemy.assert_not_called()

    def test_returns_502_when_llm_request_fails(self) -> None:
        llm = _fake_llm(generate_side_effect=httpx.ConnectError("connection refused"))

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("LLM request failed", response.json()["reason"])

    def test_returns_502_on_non_json_output(self) -> None:
        llm = _fake_llm(generate_return="not json at all")

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_keys_missing(self) -> None:
        llm = _fake_llm(generate_return=json.dumps({"outcome": "x"}))  # no search_queries

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_search_queries_not_list_of_strings(self) -> None:
        llm = _fake_llm(
            generate_return=json.dumps({"outcome": "x", "search_queries": [1, 2]})
        )

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["reason"], "LLM returned malformed output")

    def test_returns_502_when_course_search_fails(self) -> None:
        llm = _fake_llm(generate_return=_llm_output("Improve infra.", _QUERIES))
        udemy = _fake_udemy(search_side_effect=httpx.ConnectError("udemy down"))

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm), patch(
            "router.upskilling_suggestion.get_course_provider", return_value=udemy
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("Course search failed", response.json()["reason"])

    def test_missing_required_headers_returns_400(self) -> None:
        llm = _fake_llm(generate_return=_llm_output("You are perfect for this role.", []))

        with patch("router.upskilling_suggestion.get_llm_client", return_value=llm):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")
        llm.generate.assert_not_awaited()

    def test_invalid_body_returns_400(self) -> None:
        body = {"job": {"description": "only job, no candidate", "skills": []}}

        response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["reason"], "Invalid Fields Found")
        self.assertIn("candidate", payload["data"])


if __name__ == "__main__":
    unittest.main()
