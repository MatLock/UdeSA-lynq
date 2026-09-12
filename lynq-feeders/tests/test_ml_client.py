from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from ml_client import MlClient, MlError

BASE_URL = "http://lynq-ml:8084/lynq-ml"
REQUEST_UUID = "11111111-2222-3333-4444-555555555555"
SYSTEM_USER_ID = "00000000-0000-0000-0000-00000000feed"


def _client():
    return MlClient(base_url=BASE_URL, system_user_id=SYSTEM_USER_ID, timeout=1.0)


def _response(payload, raise_for_status=None):
    response = MagicMock()
    response.json.return_value = payload
    response.raise_for_status = MagicMock(side_effect=raise_for_status)
    return response


def _patched_post(response=None, side_effect=None):
    async_client = MagicMock()
    async_client.post = AsyncMock(return_value=response, side_effect=side_effect)
    context = MagicMock()
    context.__aenter__ = AsyncMock(return_value=async_client)
    context.__aexit__ = AsyncMock(return_value=False)
    return patch("ml_client.client.httpx.AsyncClient", return_value=context), async_client


class SkillEnhanceTest(unittest.IsolatedAsyncioTestCase):

    async def test_returns_skills_and_similarity_tags(self):
        payload = {"success": True, "data": {"skills": ["Python"], "similarity_tags": ["Backend"]}}
        patcher, async_client = _patched_post(_response(payload))
        with patcher:
            result = await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")

        self.assertEqual(result.skills, ["Python"])
        self.assertEqual(result.similarity_tags, ["Backend"])
        self.assertFalse(result.is_empty)

    async def test_sends_the_request_uuid_and_system_user_id_headers(self):
        payload = {"data": {"skills": [], "similarity_tags": []}}
        patcher, async_client = _patched_post(_response(payload))
        with patcher:
            await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "IN_OFFICE")

        headers = async_client.post.call_args.kwargs["headers"]
        self.assertEqual(headers["lynq-request-uuid"], REQUEST_UUID)
        self.assertEqual(headers["user-id"], SYSTEM_USER_ID)
        body = async_client.post.call_args.kwargs["json"]
        self.assertEqual(body, {"title": "Dev", "description": "Desc", "work_type": "IN_OFFICE"})
        self.assertTrue(async_client.post.call_args.args[0].endswith("/dmz/skill-enhance"))

    async def test_malformed_lists_are_ignored_rather_than_fatal(self):
        payload = {"data": {"skills": "Python", "similarity_tags": [1, 2]}}
        patcher, _ = _patched_post(_response(payload))
        with patcher:
            result = await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")

        self.assertEqual(result.skills, [])
        self.assertEqual(result.similarity_tags, [])
        self.assertTrue(result.is_empty)

    async def test_envelope_without_data_raises(self):
        patcher, _ = _patched_post(_response({"success": False, "reason": "boom"}))
        with patcher, self.assertRaises(MlError):
            await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")

    async def test_transport_error_raises_ml_error(self):
        patcher, _ = _patched_post(side_effect=httpx.ConnectError("refused"))
        with patcher, self.assertRaises(MlError):
            await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")

    async def test_http_error_status_raises_ml_error(self):
        response = _response({}, raise_for_status=httpx.HTTPStatusError(
            "502", request=MagicMock(), response=MagicMock()
        ))
        patcher, _ = _patched_post(response)
        with patcher, self.assertRaises(MlError):
            await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")

    async def test_non_json_body_raises_ml_error(self):
        response = MagicMock()
        response.raise_for_status = MagicMock()
        response.json.side_effect = ValueError("not json")
        patcher, _ = _patched_post(response)
        with patcher, self.assertRaises(MlError):
            await _client().skill_enhance(REQUEST_UUID, "Dev", "Desc", "REMOTE")


if __name__ == "__main__":
    unittest.main()
