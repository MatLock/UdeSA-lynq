from __future__ import annotations

import logging
from typing import Optional

import httpx
from pydantic import BaseModel, Field

from scraper.base import Listing

log = logging.getLogger(__name__)

INGEST_PATH = "/internal/job-posts/ingest"
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

    model_config = {"populate_by_name": True}


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
        "jobUrl": listing.apply_url,
        "jobPostSource": listing.source.upper(),
        "companyName": listing.company,
        "postedAt": listing.posted_at,
        "skills": listing.skills,
        "similarityTags": listing.similarity_tags,
    }


class BackendClient:

    def __init__(self, base_url: str, internal_token: str, timeout: float) -> None:
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token
        self.timeout = timeout

    def _headers(self, request_uuid: str) -> dict[str, str]:
        return {
            "lynq-request-uuid": request_uuid,
            INTERNAL_TOKEN_HEADER: self.internal_token,
            "Content-Type": "application/json",
        }

    async def ingest(self, request_uuid: str, listings: list[Listing]) -> IngestStats:
        body = {"jobPosts": [to_ingest_payload(listing) for listing in listings]}
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    f"{self.base_url}{INGEST_PATH}", json=body, headers=self._headers(request_uuid)
                )
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPError as exc:
            raise BackendError(f"job-post ingest failed: {exc}") from exc
        except ValueError as exc:
            raise BackendError(f"job-post ingest returned a non-JSON body: {exc}") from exc

        data = payload.get("data")
        if not isinstance(data, dict):
            raise BackendError("job-post ingest returned an envelope without data")
        return IngestStats.model_validate(data)

    async def is_reachable(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                await client.get(self.base_url)
            return True
        except httpx.HTTPError:
            return False
