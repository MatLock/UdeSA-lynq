from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from config import Settings, reset_settings
from client.lynq_llm_client import (
    SKILL_ENHANCE_PATH,
    LynqLlmClient,
    SkillExtractionFailed,
    get_lynq_llm_client,
    reset_lynq_llm_client,
)

_BEARER = "Bearer access-token"

_JOB = {
    "id": "job-1",
    "title": "Senior Backend Engineer",
    "description": "Kubernetes and PostgreSQL",
    "workType": "REMOTE",
    "skills": ["Jenkins"],
}


def settings_with(**overrides) -> Settings:
    reset_settings()
    settings = Settings()
    settings.lynq_llm_url = "http://lynq-llm:8084/lynq-llm"
    for name, value in overrides.items():
        setattr(settings, name, value)
    return settings


class LynqLlmClientTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        reset_lynq_llm_client()
        self.post = AsyncMock()
        self.session = MagicMock()
        self.session.post = self.post
        self.session.__aenter__ = AsyncMock(return_value=self.session)
        self.session.__aexit__ = AsyncMock(return_value=False)

    def _respond(self, payload: dict, status_code: int = 200) -> None:
        response = MagicMock()
        response.json.return_value = payload
        response.raise_for_status = MagicMock()
        if status_code >= 400:
            response.raise_for_status.side_effect = httpx.HTTPStatusError(
                "boom", request=MagicMock(), response=MagicMock()
            )
        self.post.return_value = response

    async def _extract(self, **overrides) -> list[str]:
        client = LynqLlmClient(settings_with(**overrides))
        with patch("httpx.AsyncClient", return_value=self.session):
            return await client.extract_skills(_JOB, "req-1", _BEARER)

    async def test_it_posts_the_posting_to_skill_enhance(self) -> None:
        self._respond({"success": True, "data": {"skills": ["Kubernetes"]}})

        skills = await self._extract()

        self.assertEqual(skills, ["Kubernetes"])
        url = self.post.await_args.args[0]
        self.assertEqual(url, "http://lynq-llm:8084/lynq-llm" + SKILL_ENHANCE_PATH)
        body = self.post.await_args.kwargs["json"]
        self.assertEqual(body["title"], _JOB["title"])
        self.assertEqual(body["work_type"], "REMOTE")

    async def test_it_relays_the_callers_credential_rather_than_their_id(self) -> None:
        self._respond({"success": True, "data": {"skills": []}})

        await self._extract()

        headers = self.post.await_args.kwargs["headers"]
        self.assertEqual(headers["lynq-request-uuid"], "req-1")
        self.assertEqual(headers["Authorization"], _BEARER)
        self.assertNotIn("user-id", headers)

    async def test_a_job_without_work_type_defaults_to_remote(self) -> None:
        self._respond({"success": True, "data": {"skills": []}})
        client = LynqLlmClient(settings_with())

        with patch("httpx.AsyncClient", return_value=self.session):
            await client.extract_skills({"title": "T", "description": "D"}, "r", _BEARER)

        self.assertEqual(self.post.await_args.kwargs["json"]["work_type"], "REMOTE")

    async def test_an_empty_payload_is_an_empty_list(self) -> None:
        self._respond({"success": True, "data": None})

        self.assertEqual(await self._extract(), [])

    async def test_an_http_error_is_reported_as_a_failure(self) -> None:
        self._respond({}, status_code=502)

        with self.assertRaises(SkillExtractionFailed):
            await self._extract()

    async def test_a_transport_error_is_reported_as_a_failure(self) -> None:
        self.post.side_effect = httpx.ConnectError("lynq-llm is down")

        with self.assertRaises(SkillExtractionFailed):
            await self._extract()

    def test_the_client_is_a_singleton(self) -> None:
        reset_settings()
        first = get_lynq_llm_client()
        second = get_lynq_llm_client()

        self.assertIs(first, second)
        reset_lynq_llm_client()


if __name__ == "__main__":
    unittest.main()
