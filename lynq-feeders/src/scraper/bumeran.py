from __future__ import annotations

import json
import logging
import re
import time
from datetime import datetime, timezone
from typing import Optional
from urllib.parse import urlparse

import requests

from scraper.base import (
    Listing,
    LivenessCheck,
    LivenessOutcome,
    backoff_seconds,
    liveness_by_status,
    new_session,
    pick_user_agent,
    slugify,
    sort_latest_first,
    unreachable,
    unrecognised,
)
from scraper.categories import bumeran_category

log = logging.getLogger(__name__)

SOURCE = "bumeran"
BASE = "https://www.bumeran.com.ar"
SEARCH_URL = f"{BASE}/api/avisos/searchV2"
WARMUP_URL = f"{BASE}/empleos.html"
SITE_ID = "BMAR"
MAX_RETRIES = 5
PAGE_SIZE = 20
FICHA_URL = f"{BASE}/api/candidates/fichaAvisoNormalizada"
ACTIVE_STATE = "activo"
FINISHED_STATES = frozenset({"vencido", "offline"})
NO_CACHE = "no-cache, no-store, must-revalidate"

SESSION_HEADERS = {
    "x-site-id": SITE_ID,
    "Origin": BASE,
    "Referer": WARMUP_URL,
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Accept-Language": "es-AR,es;q=0.9",
}

_AVISO_ID_RE = re.compile(r"-(\d+)\.html$")

_DATE_FORMATS = ("%d-%m-%Y %H:%M:%S", "%d-%m-%Y")


def _parse_published_at(raw: Optional[str]) -> Optional[int]:
    if not raw:
        return None
    for fmt in _DATE_FORMATS:
        try:
            parsed = datetime.strptime(raw, fmt).replace(tzinfo=timezone.utc)
            return int(parsed.timestamp() * 1000)
        except ValueError:
            continue
    return None


def _is_challenge(text: str) -> bool:
    head = text.lstrip()[:600]
    return head.startswith("<!DOCTYPE") or "Attention Required" in head or "cf-error" in head


def warm_up(session: requests.Session, timeout: float) -> None:
    session.get(WARMUP_URL, headers={"User-Agent": pick_user_agent()}, timeout=timeout)


def aviso_id_of(url: str) -> Optional[str]:
    match = _AVISO_ID_RE.search(urlparse(url).path)
    return match.group(1) if match else None


def classify_ficha(status_code: int, text: str) -> LivenessCheck:
    by_status = liveness_by_status(status_code)
    if by_status is not None:
        return by_status
    if status_code != 200:
        return unrecognised(f"unexpected HTTP {status_code}")
    if _is_challenge(text):
        return LivenessCheck(
            outcome=LivenessOutcome.UNKNOWN, blocked=True, reason="challenged by the portal"
        )
    try:
        payload = json.loads(text)
    except ValueError:
        return unrecognised("the posting answered with something other than JSON")
    aviso = payload.get("aviso") if isinstance(payload, dict) else None
    estado = aviso.get("estado") if isinstance(aviso, dict) else None
    if estado == ACTIVE_STATE:
        return LivenessCheck(outcome=LivenessOutcome.ALIVE)
    if estado in FINISHED_STATES:
        return LivenessCheck(outcome=LivenessOutcome.CLOSED, reason=f"estado {estado}")
    return unrecognised(f"unrecognised estado {estado!r}")


def _to_listing(aviso: dict, category: str) -> Optional[Listing]:
    aviso_id = aviso.get("id")
    title = aviso.get("titulo")
    if aviso_id is None or not title:
        return None

    modalidad = (aviso.get("modalidadTrabajo") or "").lower()
    published = aviso.get("fechaHoraPublicacion") or aviso.get("fechaPublicacion")

    return Listing(
        external_id=str(aviso_id),
        title=title,
        source=SOURCE,
        category=category,
        company=aviso.get("empresa"),
        company_logo_url=aviso.get("logoURL"),
        location=aviso.get("localizacion"),
        remote="remoto" in modalidad,
        work_type=aviso.get("tipoTrabajo"),
        description=aviso.get("detalle"),
        apply_url=f"{BASE}/empleos/{slugify(title)}-{aviso_id}.html",
        posted_at=_parse_published_at(published),
    )


class BumeranScraper:
    source = SOURCE

    def __init__(self, timeout: float = 25.0) -> None:
        self.timeout = timeout
        self._session: Optional[requests.Session] = None

    def fetch(self, category: str, limit: int) -> list[Listing]:
        page_size = max(limit, PAGE_SIZE)
        content = self._search(category, 0, page_size).get("content") or []

        listings = [_to_listing(aviso, category) for aviso in content]
        found = [listing for listing in listings if listing is not None]

        log.info(
            "message= Fetched Bumeran listings, category=%s, received=%s, usable=%s",
            category,
            len(content),
            len(found),
        )
        return sort_latest_first(found)[:limit]

    def _ensure_session(self) -> requests.Session:
        if self._session is None:
            self._session = new_session(SESSION_HEADERS)
            self._warmup(self._session)
        return self._session

    def _warmup(self, session: requests.Session) -> None:
        warm_up(session, self.timeout)

    def _search(self, category: str, page: int, page_size: int) -> dict:
        session = self._ensure_session()
        config = bumeran_category(category)

        body: dict = {"filtros": []}
        if config.area:
            body["filtros"].append({"id": "area", "value": config.area})
        if config.query:
            body["query"] = config.query

        url = f"{SEARCH_URL}?pageSize={page_size}&page={page}&sort=RECIENTES"
        for attempt in range(MAX_RETRIES):
            response = session.post(
                url, json=body, headers={"User-Agent": pick_user_agent()}, timeout=self.timeout
            )
            if response.status_code == 200 and not _is_challenge(response.text):
                return response.json()
            wait = backoff_seconds(attempt)
            log.warning(
                "message= Bumeran challenged the search, re-warming and backing off, "
                "status=%s, category=%s, page=%s, wait_seconds=%.1f",
                response.status_code,
                category,
                page,
                wait,
            )
            time.sleep(wait)
            self._warmup(session)

        raise RuntimeError(
            f"Bumeran search failed for category={category} page={page} after {MAX_RETRIES} retries"
        )


class BumeranLivenessChecker:
    source = SOURCE

    def __init__(self, timeout: float = 25.0) -> None:
        self.timeout = timeout
        self._session: Optional[requests.Session] = None

    def check(self, url: str) -> LivenessCheck:
        aviso_id = aviso_id_of(url)
        if aviso_id is None:
            return unrecognised("the job URL carries no Bumeran posting id")
        try:
            response = self._ensure_session().get(
                f"{FICHA_URL}/{aviso_id}",
                headers={"User-Agent": pick_user_agent(), "Cache-Control": NO_CACHE},
                timeout=self.timeout,
                allow_redirects=False,
            )
        except requests.RequestException as exc:
            return unreachable(exc)
        return classify_ficha(response.status_code, response.text)

    def _ensure_session(self) -> requests.Session:
        if self._session is None:
            session = new_session(SESSION_HEADERS)
            warm_up(session, self.timeout)
            self._session = session
        return self._session
