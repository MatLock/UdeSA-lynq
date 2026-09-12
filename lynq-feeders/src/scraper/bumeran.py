from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Optional

import requests

from scraper.base import (
    Listing,
    backoff_seconds,
    new_session,
    pick_user_agent,
    slugify,
    sort_latest_first,
)
from scraper.rubros import bumeran_rubro

log = logging.getLogger(__name__)

SOURCE = "bumeran"
BASE = "https://www.bumeran.com.ar"
SEARCH_URL = f"{BASE}/api/avisos/searchV2"
WARMUP_URL = f"{BASE}/empleos.html"
SITE_ID = "BMAR"
MAX_RETRIES = 5
PAGE_SIZE = 20

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


def _to_listing(aviso: dict, rubro: str) -> Optional[Listing]:
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
        rubro=rubro,
        company=aviso.get("empresa"),
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

    def _ensure_session(self) -> requests.Session:
        if self._session is None:
            self._session = new_session(
                {
                    "x-site-id": SITE_ID,
                    "Origin": BASE,
                    "Referer": WARMUP_URL,
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                    "Accept-Language": "es-AR,es;q=0.9",
                }
            )
            self._warmup(self._session)
        return self._session

    def _warmup(self, session: requests.Session) -> None:
        session.get(WARMUP_URL, headers={"User-Agent": pick_user_agent()}, timeout=self.timeout)

    def _search(self, rubro: str, page: int, page_size: int) -> dict:
        session = self._ensure_session()
        config = bumeran_rubro(rubro)

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
                "status=%s, rubro=%s, page=%s, wait_seconds=%.1f",
                response.status_code,
                rubro,
                page,
                wait,
            )
            time.sleep(wait)
            self._warmup(session)

        raise RuntimeError(
            f"Bumeran search failed for rubro={rubro} page={page} after {MAX_RETRIES} retries"
        )

    def fetch(self, rubro: str, limit: int) -> list[Listing]:
        page_size = max(limit, PAGE_SIZE)
        content = self._search(rubro, 0, page_size).get("content") or []

        listings = [_to_listing(aviso, rubro) for aviso in content]
        found = [listing for listing in listings if listing is not None]

        log.info(
            "message= Fetched Bumeran listings, rubro=%s, received=%s, usable=%s",
            rubro,
            len(content),
            len(found),
        )
        return sort_latest_first(found)[:limit]
