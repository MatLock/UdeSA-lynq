from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from backend_client import BackendClient, BackendError
from backend_client.client import INTERNAL_TOKEN_HEADER, to_ingest_payload
from scraper.base import Listing

BASE_URL = "http://lynq-app-backend:8080/lynq-backend-app"
REQUEST_UUID = "11111111-2222-3333-4444-555555555555"
TOKEN = "not-a-real-token"


def _listing(**overrides) -> Listing:
    defaults = dict(
        external_id="ABC123",
        title="Backend Developer",
        source="computrabajo",
        rubro="TECNOLOGIA",
        company="Lectus",
        description="Python y FastAPI.",
        remote=True,
        salary_min=1500000.0,
        salary_max=2000000.0,
        apply_url="https://ar.computrabajo.com/oferta",
        posted_at=1789207200000,
        skills=["Python"],
        similarity_tags=["Backend Development"],
    )
    defaults.update(overrides)
    return Listing(**defaults)


def _client():
    return BackendClient(base_url=BASE_URL, internal_token=TOKEN, timeout=1.0)


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
    return patch("backend_client.client.httpx.AsyncClient", return_value=context), async_client


class ToIngestPayloadTest(unittest.TestCase):

    def test_maps_the_listing_onto_the_backend_contract(self):
        payload = to_ingest_payload(_listing())

        self.assertEqual(payload["externalId"], "ABC123")
        self.assertEqual(payload["title"], "Backend Developer")
        self.assertEqual(payload["jobPostSource"], "COMPUTRABAJO")
        self.assertEqual(payload["companyName"], "Lectus")
        self.assertEqual(payload["jobUrl"], "https://ar.computrabajo.com/oferta")
        self.assertEqual(payload["skills"], ["Python"])
        self.assertEqual(payload["similarityTags"], ["Backend Development"])

    def test_remote_listings_map_to_the_remote_work_type(self):
        self.assertEqual(to_ingest_payload(_listing(remote=True))["workType"], "REMOTE")

    def test_non_remote_listings_map_to_in_office(self):
        self.assertEqual(to_ingest_payload(_listing(remote=False))["workType"], "IN_OFFICE")

    def test_salary_is_truncated_to_integers(self):
        payload = to_ingest_payload(_listing(salary_min=1500000.75, salary_max=None))
        self.assertEqual(payload["salaryRangeDown"], 1500000)
        self.assertIsNone(payload["salaryRangeTop"])

    def test_bumeran_source_is_uppercased(self):
        self.assertEqual(to_ingest_payload(_listing(source="bumeran"))["jobPostSource"], "BUMERAN")


class IngestTest(unittest.IsolatedAsyncioTestCase):

    async def test_parses_the_stats_envelope(self):
        payload = {
            "success": True,
            "data": {"jobs": 3, "companies": 2, "skills": 9, "similarityTags": 5, "skipped": 1},
        }
        patcher, _ = _patched_post(_response(payload))
        with patcher:
            stats = await _client().ingest(REQUEST_UUID, [_listing()])

        self.assertEqual(stats.jobs, 3)
        self.assertEqual(stats.companies, 2)
        self.assertEqual(stats.similarity_tags, 5)
        self.assertEqual(stats.skipped, 1)

    async def test_sends_the_internal_token_and_request_uuid(self):
        patcher, async_client = _patched_post(_response({"data": {}}))
        with patcher:
            await _client().ingest(REQUEST_UUID, [_listing()])

        headers = async_client.post.call_args.kwargs["headers"]
        self.assertEqual(headers[INTERNAL_TOKEN_HEADER], TOKEN)
        self.assertEqual(headers["lynq-request-uuid"], REQUEST_UUID)
        self.assertTrue(async_client.post.call_args.args[0].endswith("/internal/job-posts/ingest"))

    async def test_sends_every_listing_in_one_batch(self):
        patcher, async_client = _patched_post(_response({"data": {}}))
        with patcher:
            await _client().ingest(REQUEST_UUID, [_listing(), _listing(external_id="XYZ")])

        body = async_client.post.call_args.kwargs["json"]
        self.assertEqual(len(body["jobPosts"]), 2)

    async def test_envelope_without_data_raises(self):
        patcher, _ = _patched_post(_response({"success": False, "reason": "nope"}))
        with patcher, self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])

    async def test_transport_error_raises_backend_error(self):
        patcher, _ = _patched_post(side_effect=httpx.ConnectError("refused"))
        with patcher, self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])

    async def test_rejected_token_raises_backend_error(self):
        response = _response({}, raise_for_status=httpx.HTTPStatusError(
            "401", request=MagicMock(), response=MagicMock()
        ))
        patcher, _ = _patched_post(response)
        with patcher, self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])


if __name__ == "__main__":
    unittest.main()
