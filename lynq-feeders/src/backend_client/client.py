from __future__ import annotations

import logging
from typing import Any, Optional

import httpx
from pydantic import BaseModel, Field

from scraper.base import Listing, LivenessOutcome

log = logging.getLogger(__name__)

INGEST_PATH = "/internal/job-posts/ingest"
VERIFICATION_CANDIDATES_PATH = "/internal/job-posts/verification-candidates"
LIVENESS_PATH = "/internal/job-posts/liveness"
EXPIRE_PATH = "/internal/job-posts/expire"
INTERNAL_TOKEN_HEADER = "lynq-internal-token"

REMOTE = "REMOTE"
IN_OFFICE = "IN_OFFICE"


class BackendError(RuntimeError):
    pass


class IngestStats(BaseModel):
    jobs: int = 0
    companies: int = 0
    skills: int = 0
    similarity_tags: int = Field(default=0, alias="similarityTags")
    skipped: int = 0
    reopened: int = 0

    model_config = {"populate_by_name": True}


class VerificationCandidate(BaseModel):
    id: str
    job_url: str = Field(alias="jobUrl")
    source: str
    category: Optional[str] = None

    model_config = {"populate_by_name": True}


class LivenessReport(BaseModel):
    id: str
    outcome: LivenessOutcome


class LivenessStats(BaseModel):
    alive: int = 0
    closed: int = 0
    gone: int = 0
    unknown: int = 0
    skipped: int = 0


def _as_int(value: Optional[float]) -> Optional[int]:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError, OverflowError):
        return None


def to_ingest_payload(listing: Listing) -> dict:
    return {
        "externalId": listing.external_id,
        "title": listing.title,
        "description": listing.description,
        "workType": REMOTE if listing.remote else IN_OFFICE,
        "salaryRangeDown": _as_int(listing.salary_min),
        "salaryRangeTop": _as_int(listing.salary_max),
        "salaryCurrency": listing.currency,
        "category": listing.category,
        "jobUrl": listing.apply_url,
        "jobPostSource": listing.source.upper(),
        "companyName": listing.company,
        "companyLogoUrl": listing.company_logo_url,
        "postedAt": listing.posted_at,
        "skills": listing.skills,
        "similarityTags": listing.similarity_tags,
    }


class BackendClient:

    def __init__(self, base_url: str, internal_token: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token
        self.timeout = timeout

    async def ingest(self, request_uuid: str, listings: list[Listing]) -> IngestStats:
        body = {"jobPosts": [to_ingest_payload(listing) for listing in listings]}
        data = await self._send("POST", INGEST_PATH, request_uuid, "job-post ingest", body)
        return IngestStats.model_validate(data)

    async def list_verification_candidates(
        self, request_uuid: str
    ) -> list[VerificationCandidate]:
        data = await self._send(
            "GET", VERIFICATION_CANDIDATES_PATH, request_uuid, "verification candidates"
        )
        candidates = data.get("candidates")
        if not isinstance(candidates, list):
            raise BackendError("verification candidates came without a candidate list")
        return [VerificationCandidate.model_validate(candidate) for candidate in candidates]

    async def report_liveness(
        self, request_uuid: str, reports: list[LivenessReport]
    ) -> LivenessStats:
        body = {"reports": [report.model_dump(mode="json") for report in reports]}
        data = await self._send("POST", LIVENESS_PATH, request_uuid, "liveness report", body)
        return LivenessStats.model_validate(data)

    async def expire(self, request_uuid: str) -> int:
        data = await self._send("POST", EXPIRE_PATH, request_uuid, "job-post expiry")
        expired = data.get("expired")
        if not isinstance(expired, int):
            raise BackendError("job-post expiry came without a count")
        return expired

    async def _send(
        self,
        method: str,
        path: str,
        request_uuid: str,
        what: str,
        body: Optional[dict[str, Any]] = None,
    ) -> dict:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.request(
                    method, f"{self.base_url}{path}", json=body, headers=self._headers(request_uuid)
                )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise BackendError(f"{what} failed: {exc}") from exc
        except ValueError as exc:
            raise BackendError(f"{what} returned a non-JSON body: {exc}") from exc

        data = payload.get("data") if isinstance(payload, dict) else None
        if not isinstance(data, dict):
            raise BackendError(f"{what} returned an envelope without data")
        return data

    async def is_reachable(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                await client.get(self.base_url)
            return True
        except httpx.HTTPError:
            return False

    def _headers(self, request_uuid: str) -> dict[str, str]:
        return {
            "lynq-request-uuid": request_uuid,
            INTERNAL_TOKEN_HEADER: self.internal_token,
            "Content-Type": "application/json",
        }
