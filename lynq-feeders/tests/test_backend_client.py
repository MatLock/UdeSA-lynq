from __future__ import annotations

import json
import unittest
from unittest.mock import patch

import httpx

from backend_client import BackendClient, BackendError, LivenessReport
from backend_client.client import INTERNAL_TOKEN_HEADER, to_ingest_payload
from scraper.base import Listing, LivenessOutcome

BASE_URL = "http://lynq-app-backend:8080/lynq-backend-app"
REQUEST_UUID = "11111111-2222-3333-4444-555555555555"
TOKEN = "not-a-real-token"


def _listing(**overrides) -> Listing:
    defaults = dict(
        external_id="ABC123",
        title="Backend Developer",
        source="computrabajo",
        category="TECNOLOGIA",
        company="Lectus",
        company_logo_url="https://ii.ct-stc.com/lectus.jpeg",
        description="Python y FastAPI.",
        remote=True,
        salary_min=1500000.0,
        salary_max=2000000.0,
        currency="ARS",
        apply_url="https://ar.computrabajo.com/oferta",
        posted_at=1789207200000,
        skills=["Python"],
        similarity_tags=["Backend Development"],
    )
    defaults.update(overrides)
    return Listing(**defaults)


def _client():
    return BackendClient(base_url=BASE_URL, internal_token=TOKEN, timeout=1.0)


_REAL_ASYNC_CLIENT = httpx.AsyncClient


class _Backend:

    def __init__(self, status_code=200, payload=None, error=None, text=None):
        self.status_code = status_code
        self.payload = payload
        self.error = error
        self.text = text
        self.requests: list[httpx.Request] = []

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if self.error is not None:
            raise self.error
        if self.text is not None:
            return httpx.Response(self.status_code, text=self.text)
        return httpx.Response(self.status_code, json=self.payload)

    def patched(self):
        def build(**kwargs):
            return _REAL_ASYNC_CLIENT(transport=httpx.MockTransport(self.handle), **kwargs)

        return patch("backend_client.client.httpx.AsyncClient", side_effect=build)

    @property
    def last(self) -> httpx.Request:
        return self.requests[-1]

    def last_json(self):
        return json.loads(self.last.content)


class ToIngestPayloadTest(unittest.TestCase):

    def test_maps_the_listing_onto_the_backend_contract(self):
        payload = to_ingest_payload(_listing())

        self.assertEqual(payload["externalId"], "ABC123")
        self.assertEqual(payload["title"], "Backend Developer")
        self.assertEqual(payload["jobPostSource"], "COMPUTRABAJO")
        self.assertEqual(payload["companyName"], "Lectus")
        self.assertEqual(payload["companyLogoUrl"], "https://ii.ct-stc.com/lectus.jpeg")
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

    def test_salary_currency_and_category_are_sent(self):
        payload = to_ingest_payload(_listing())
        self.assertEqual(payload["salaryCurrency"], "ARS")
        self.assertEqual(payload["category"], "TECNOLOGIA")

    def test_a_listing_without_salary_sends_no_currency(self):
        payload = to_ingest_payload(_listing(salary_min=None, salary_max=None, currency=None))
        self.assertIsNone(payload["salaryCurrency"])

    def test_bumeran_source_is_uppercased(self):
        self.assertEqual(to_ingest_payload(_listing(source="bumeran"))["jobPostSource"], "BUMERAN")


class IngestTest(unittest.IsolatedAsyncioTestCase):

    async def test_parses_the_stats_envelope(self):
        backend = _Backend(payload={
            "success": True,
            "data": {
                "jobs": 3, "companies": 2, "skills": 9, "similarityTags": 5, "skipped": 1,
                "reopened": 2,
            },
        })
        with backend.patched():
            stats = await _client().ingest(REQUEST_UUID, [_listing()])

        self.assertEqual(stats.jobs, 3)
        self.assertEqual(stats.companies, 2)
        self.assertEqual(stats.similarity_tags, 5)
        self.assertEqual(stats.skipped, 1)
        self.assertEqual(stats.reopened, 2)

    async def test_sends_the_internal_token_and_request_uuid(self):
        backend = _Backend(payload={"data": {}})
        with backend.patched():
            await _client().ingest(REQUEST_UUID, [_listing()])

        self.assertEqual(backend.last.method, "POST")
        self.assertEqual(backend.last.headers[INTERNAL_TOKEN_HEADER], TOKEN)
        self.assertEqual(backend.last.headers["lynq-request-uuid"], REQUEST_UUID)
        self.assertTrue(str(backend.last.url).endswith("/internal/job-posts/ingest"))

    async def test_sends_every_listing_in_one_batch(self):
        backend = _Backend(payload={"data": {}})
        with backend.patched():
            await _client().ingest(REQUEST_UUID, [_listing(), _listing(external_id="XYZ")])

        self.assertEqual(len(backend.last_json()["jobPosts"]), 2)

    async def test_envelope_without_data_raises(self):
        backend = _Backend(payload={"success": False, "reason": "nope"})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])

    async def test_transport_error_raises_backend_error(self):
        backend = _Backend(error=httpx.ConnectError("refused"))
        with backend.patched(), self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])

    async def test_rejected_token_raises_backend_error(self):
        backend = _Backend(status_code=401, payload={"success": False})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])

    async def test_a_non_json_body_raises_backend_error(self):
        backend = _Backend(text="<html>gateway</html>")
        with backend.patched(), self.assertRaises(BackendError):
            await _client().ingest(REQUEST_UUID, [_listing()])


class VerificationCandidatesTest(unittest.IsolatedAsyncioTestCase):

    async def test_reads_the_candidates_in_order(self):
        backend = _Backend(payload={"success": True, "data": {"candidates": [
            {"id": "a", "jobUrl": "https://ar.computrabajo.com/ofertas-de-trabajo/a",
             "source": "COMPUTRABAJO", "category": "TECNOLOGIA", "lastSeenOn": "2026-09-01",
             "lastCheckedOn": None},
            {"id": "b", "jobUrl": "https://www.bumeran.com.ar/empleos/b-1.html",
             "source": "BUMERAN", "category": None},
        ]}})
        with backend.patched():
            candidates = await _client().list_verification_candidates(REQUEST_UUID)

        self.assertEqual([candidate.id for candidate in candidates], ["a", "b"])
        self.assertEqual(candidates[0].job_url, "https://ar.computrabajo.com/ofertas-de-trabajo/a")
        self.assertEqual(candidates[0].source, "COMPUTRABAJO")
        self.assertIsNone(candidates[1].category)
        self.assertEqual(backend.last.method, "GET")
        self.assertTrue(
            str(backend.last.url).endswith("/internal/job-posts/verification-candidates")
        )
        self.assertEqual(backend.last.headers[INTERNAL_TOKEN_HEADER], TOKEN)
        self.assertEqual(backend.last.content, b"")

    async def test_data_without_a_candidate_list_raises(self):
        backend = _Backend(payload={"success": True, "data": {}})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().list_verification_candidates(REQUEST_UUID)

    async def test_a_server_error_raises(self):
        backend = _Backend(status_code=500, payload={"success": False})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().list_verification_candidates(REQUEST_UUID)


class ReportLivenessTest(unittest.IsolatedAsyncioTestCase):

    async def test_posts_every_report_and_reads_the_counts(self):
        backend = _Backend(payload={"success": True, "data": {
            "alive": 1, "closed": 1, "gone": 0, "unknown": 1, "skipped": 0,
        }})
        reports = [
            LivenessReport(id="a", outcome=LivenessOutcome.ALIVE),
            LivenessReport(id="b", outcome=LivenessOutcome.CLOSED),
            LivenessReport(id="c", outcome=LivenessOutcome.UNKNOWN),
        ]
        with backend.patched():
            stats = await _client().report_liveness(REQUEST_UUID, reports)

        self.assertEqual(stats.alive, 1)
        self.assertEqual(stats.closed, 1)
        self.assertEqual(stats.unknown, 1)
        self.assertEqual(backend.last.method, "POST")
        self.assertTrue(str(backend.last.url).endswith("/internal/job-posts/liveness"))
        self.assertEqual(backend.last_json(), {"reports": [
            {"id": "a", "outcome": "ALIVE"},
            {"id": "b", "outcome": "CLOSED"},
            {"id": "c", "outcome": "UNKNOWN"},
        ]})

    async def test_a_rejected_batch_raises(self):
        backend = _Backend(status_code=400, payload={"success": False})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().report_liveness(
                REQUEST_UUID, [LivenessReport(id="a", outcome=LivenessOutcome.ALIVE)]
            )


class ExpireTest(unittest.IsolatedAsyncioTestCase):

    async def test_posts_the_expiry_and_reads_how_many_closed(self):
        backend = _Backend(payload={"success": True, "data": {"expired": 4}})
        with backend.patched():
            expired = await _client().expire(REQUEST_UUID)

        self.assertEqual(expired, 4)
        self.assertEqual(backend.last.method, "POST")
        self.assertTrue(str(backend.last.url).endswith("/internal/job-posts/expire"))
        self.assertEqual(backend.last.headers["lynq-request-uuid"], REQUEST_UUID)

    async def test_data_without_a_count_raises(self):
        backend = _Backend(payload={"success": True, "data": {}})
        with backend.patched(), self.assertRaises(BackendError):
            await _client().expire(REQUEST_UUID)

    async def test_a_timeout_raises(self):
        backend = _Backend(error=httpx.ReadTimeout("slow"))
        with backend.patched(), self.assertRaises(BackendError):
            await _client().expire(REQUEST_UUID)


if __name__ == "__main__":
    unittest.main()
