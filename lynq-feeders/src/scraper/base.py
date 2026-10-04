from __future__ import annotations

import random
import re
import time
import unicodedata
from enum import Enum
from typing import Optional, Protocol

import requests
from pydantic import BaseModel, Field

USER_AGENTS = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/121.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
    "(KHTML, like Gecko) Version/17.2 Safari/605.1.15",
)

_NON_SLUG_CHARS = re.compile(r"[^a-zA-Z0-9\s-]")
_SLUG_SEPARATORS = re.compile(r"[\s-]+")


class Listing(BaseModel):
    external_id: str
    title: str
    source: str
    category: str
    company: Optional[str] = None
    company_logo_url: Optional[str] = None
    location: Optional[str] = None
    remote: bool = False
    work_type: Optional[str] = None
    experience_level: Optional[str] = None
    description: Optional[str] = None
    skills: list[str] = Field(default_factory=list)
    similarity_tags: list[str] = Field(default_factory=list)
    salary_min: Optional[float] = None
    salary_max: Optional[float] = None
    currency: Optional[str] = None
    apply_url: Optional[str] = None
    posted_at: Optional[int] = None


class Scraper(Protocol):
    source: str

    def fetch(self, category: str, limit: int) -> list[Listing]:
        ...


class LivenessOutcome(str, Enum):
    ALIVE = "ALIVE"
    CLOSED = "CLOSED"
    GONE = "GONE"
    UNKNOWN = "UNKNOWN"


class LivenessCheck(BaseModel):
    outcome: LivenessOutcome
    blocked: bool = False
    failed: bool = False
    reason: Optional[str] = None


class LivenessChecker(Protocol):
    source: str

    def check(self, url: str) -> LivenessCheck:
        ...


BLOCKING_STATUSES = frozenset({403, 429})
GONE_STATUSES = frozenset({404, 410})
REDIRECT_STATUSES = frozenset({301, 302, 303, 307, 308})


def liveness_by_status(status_code: int) -> Optional[LivenessCheck]:
    if status_code in BLOCKING_STATUSES:
        return LivenessCheck(
            outcome=LivenessOutcome.UNKNOWN, blocked=True, reason=f"HTTP {status_code}"
        )
    if status_code in GONE_STATUSES:
        return LivenessCheck(outcome=LivenessOutcome.GONE, reason=f"HTTP {status_code}")
    if status_code >= 500:
        return LivenessCheck(
            outcome=LivenessOutcome.UNKNOWN, failed=True, reason=f"HTTP {status_code}"
        )
    return None


def unreachable(exc: Exception) -> LivenessCheck:
    return LivenessCheck(
        outcome=LivenessOutcome.UNKNOWN, failed=True, reason=f"{type(exc).__name__}: {exc}"
    )


def unrecognised(reason: str) -> LivenessCheck:
    return LivenessCheck(outcome=LivenessOutcome.UNKNOWN, reason=reason)


def pick_user_agent() -> str:
    return random.choice(USER_AGENTS)  # NOSONAR


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    text = _NON_SLUG_CHARS.sub("", text).strip().lower()
    return _SLUG_SEPARATORS.sub("-", text)


def strip_accents(text: str) -> str:
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()


def backoff_seconds(attempt: int) -> float:
    return (2**attempt) + random.uniform(0, 1.5)  # NOSONAR


def polite_pause() -> None:
    time.sleep(random.uniform(2.0, 4.5))  # NOSONAR


def new_session(extra_headers: dict[str, str]) -> requests.Session:
    session = requests.Session()
    session.headers.update(extra_headers)
    return session


def sort_latest_first(listings: list[Listing]) -> list[Listing]:
    return sorted(listings, key=lambda listing: listing.posted_at or 0, reverse=True)
