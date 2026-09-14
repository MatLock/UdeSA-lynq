from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

import httpx

from tests.support import base_resume  # noqa: F401

from client import LynqMlClient, MlError


def _response(payload, status_code=200):
    request = httpx.Request("POST", "http://ml/dmz/skill-enhance")
    return httpx.Response(status_code, json=payload, request=request)


class LynqMlClientTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.client = LynqMlClient("http://ml/lynq-ml/", "system-user", 5.0)

    def test_the_trailing_slash_of_the_base_url_is_dropped(self):
        self.assertEqual(self.client.base_url, "http://ml/lynq-ml")

    def test_the_system_user_travels_in_the_headers(self):
        headers = self.client._headers("uuid-1")

        self.assertEqual(headers["lynq-request-uuid"], "uuid-1")
        self.assertEqual(headers["user-id"], "system-user")

    async def test_skills_and_similarity_tags_come_back_merged(self):
        payload = {
            "success": True,
            "data": {"skills": ["Kubernetes"], "similarity_tags": ["Orchestration"]},
        }
        with patch.object(
            httpx.AsyncClient, "post", AsyncMock(return_value=_response(payload))
        ):
            skills = await self.client.job_skills("uuid-1", "t", "d", "REMOTE")

        self.assertEqual(skills, ["Kubernetes", "Orchestration"])

    async def test_a_malformed_list_is_ignored_instead_of_breaking_the_turn(self):
        payload = {"success": True, "data": {"skills": ["Java"], "similarity_tags": 7}}
        with patch.object(
            httpx.AsyncClient, "post", AsyncMock(return_value=_response(payload))
        ):
            skills = await self.client.job_skills("uuid-1", "t", "d", "REMOTE")

        self.assertEqual(skills, ["Java"])

    async def test_an_envelope_without_data_is_an_error(self):
        with patch.object(
            httpx.AsyncClient,
            "post",
            AsyncMock(return_value=_response({"success": True})),
        ):
            with self.assertRaises(MlError):
                await self.client.job_skills("uuid-1", "t", "d", "REMOTE")

    async def test_a_transport_failure_is_an_error(self):
        with patch.object(
            httpx.AsyncClient,
            "post",
            AsyncMock(side_effect=httpx.ConnectError("refused")),
        ):
            with self.assertRaises(MlError):
                await self.client.job_skills("uuid-1", "t", "d", "REMOTE")

    async def test_an_http_error_status_is_an_error(self):
        with patch.object(
            httpx.AsyncClient,
            "post",
            AsyncMock(return_value=_response({"reason": "boom"}, status_code=500)),
        ):
            with self.assertRaises(MlError):
                await self.client.job_skills("uuid-1", "t", "d", "REMOTE")

    async def test_reachability_is_a_boolean_never_an_exception(self):
        request = httpx.Request("GET", "http://ml/lynq-ml/health")
        with patch.object(
            httpx.AsyncClient,
            "get",
            AsyncMock(return_value=httpx.Response(200, request=request)),
        ):
            self.assertTrue(await self.client.is_reachable())

        with patch.object(
            httpx.AsyncClient, "get", AsyncMock(side_effect=httpx.ConnectError("no"))
        ):
            self.assertFalse(await self.client.is_reachable())


if __name__ == "__main__":
    unittest.main()
