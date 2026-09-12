from __future__ import annotations

import logging
import random
import re
import time
from datetime import datetime, timedelta, timezone
from typing import Optional

import requests
from bs4 import BeautifulSoup

from scraper.base import (
    Listing,
    backoff_seconds,
    new_session,
    pick_user_agent,
    sort_latest_first,
    strip_accents,
)
from scraper.rubros import computrabajo_rubro

log = logging.getLogger(__name__)

SOURCE = "computrabajo"
BASE = "https://ar.computrabajo.com"
MAX_RETRIES = 4
SKILLS_SECTION_MARKER = "Aptitudes asociadas"
DESCRIPTION_HEADING = "Descripción de la oferta"

_DIGIT_RE = re.compile(r"(\d+)")
_HOURS_RE = re.compile(r"(\d++)\s{0,3}hora")
_MINUTES_RE = re.compile(r"(\d++)\s{0,3}minuto")
_WHITESPACE_RE = re.compile(r"\s+")
_AMOUNT_RE = re.compile(r"\d[\d.,]*")
_EXPERIENCE_RE = re.compile(r"(\d++)\s{1,3}a[nñ]os?\s{1,3}de\s{1,3}experiencia", re.I)


def _text(node) -> Optional[str]:
    if node is None:
        return None
    collapsed = _WHITESPACE_RE.sub(" ", node.get_text(strip=True)).strip()
    return collapsed or None


def _first_int(text: str, default: int = 1) -> int:
    match = _DIGIT_RE.search(text)
    return int(match.group(1)) if match else default


def _recent_delta(text: str) -> timedelta:
    match = _HOURS_RE.search(text)
    if match:
        return timedelta(hours=int(match.group(1)))
    match = _MINUTES_RE.search(text)
    if match:
        return timedelta(minutes=int(match.group(1)))
    return timedelta(0)


def parse_relative_date(raw: Optional[str], now: datetime) -> Optional[int]:
    if not raw:
        return None
    text = strip_accents(raw)
    if "hoy" in text or "hora" in text or "minuto" in text or "segundo" in text:
        moment = now - _recent_delta(text)
    elif "ayer" in text:
        moment = now - timedelta(days=1)
    elif "dia" in text:
        moment = now - timedelta(days=_first_int(text))
    elif "semana" in text:
        moment = now - timedelta(weeks=_first_int(text))
    elif "mes" in text:
        moment = now - timedelta(days=30 * _first_int(text))
    elif "ano" in text:
        moment = now - timedelta(days=365 * _first_int(text))
    else:
        return None
    return int(moment.timestamp() * 1000)


def _to_number(raw: str) -> Optional[float]:
    try:
        return float(raw.replace(".", "").replace(",", "."))
    except ValueError:
        return None


def parse_salary(raw: Optional[str]) -> tuple[Optional[float], Optional[float], Optional[str]]:
    if not raw:
        return None, None, None
    values = [value for value in (_to_number(n) for n in _AMOUNT_RE.findall(raw)) if value]
    if not values:
        return None, None, None
    if len(values) >= 2:
        return values[0], values[1], "ARS"
    return values[0], None, "ARS"


def _card_location(card) -> Optional[str]:
    for paragraph in card.select("p.fs16"):
        if not paragraph.find("a"):
            return _text(paragraph.select_one("span") or paragraph)
    return None


def _card_salary_text(card) -> Optional[str]:
    for span in card.find_all("span"):
        text = span.get_text(" ", strip=True)
        if "$" in text and _DIGIT_RE.search(text):
            return _WHITESPACE_RE.sub(" ", text)
    return None


def parse_card(card, rubro: str, now: datetime) -> Optional[Listing]:
    title_link = card.select_one("h2 a.js-o-link")
    title = _text(title_link)
    external_id = card.get("data-id")
    if not title or not external_id:
        return None

    href = title_link.get("href", "").split("#")[0] if title_link else None
    apply_url = BASE + href if href and href.startswith("/") else href

    modality = _text(card.select_one("div.fs13 span"))
    salary_min, salary_max, currency = parse_salary(_card_salary_text(card))

    return Listing(
        external_id=external_id,
        title=title,
        source=SOURCE,
        rubro=rubro,
        company=_text(card.select_one("a.fc_base.t_ellipsis")),
        location=_card_location(card),
        remote=bool(modality and "remoto" in modality.lower()),
        work_type=modality,
        salary_min=salary_min,
        salary_max=salary_max,
        currency=currency,
        apply_url=apply_url,
        posted_at=parse_relative_date(_text(card.select_one("p.fs13.fc_aux")), now),
    )


class ComputrabajoScraper:
    source = SOURCE

    def __init__(self, timeout: float = 25.0) -> None:
        self.timeout = timeout
        self._session: Optional[requests.Session] = None

    def _ensure_session(self) -> requests.Session:
        if self._session is None:
            self._session = new_session(
                {
                    "Accept-Language": "es-AR,es;q=0.9",
                    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                }
            )
        return self._session

    def _get(self, url: str) -> str:
        session = self._ensure_session()
        response = None
        for attempt in range(MAX_RETRIES):
            response = session.get(
                url, headers={"User-Agent": pick_user_agent()}, timeout=self.timeout
            )
            if response.status_code == 200:
                return response.text
            if response.status_code in (403, 429):
                wait = backoff_seconds(attempt)
                log.warning(
                    "message= Computrabajo blocked the request, backing off, "
                    "status=%s, url=%s, wait_seconds=%.1f",
                    response.status_code,
                    url,
                    wait,
                )
                time.sleep(wait)
                continue
            response.raise_for_status()
        status = response.status_code if response is not None else "unknown"
        raise RuntimeError(f"Computrabajo request failed for {url} after {MAX_RETRIES} retries (last {status})")

    def _fetch_detail(self, listing: Listing) -> None:
        if not listing.apply_url:
            return
        try:
            soup = BeautifulSoup(self._get(listing.apply_url), "lxml")
        except (requests.RequestException, RuntimeError) as exc:
            log.warning(
                "message= Could not fetch Computrabajo detail page, keeping the card as is, "
                "url=%s, error=%s",
                listing.apply_url,
                exc,
            )
            return

        box = soup.find(attrs={"description-offer": True})
        block = box.select_one("div.mb40.pb40.bb1") if box else None
        if not block:
            return

        text = block.get_text("\n", strip=True)
        cutoff = text.find(SKILLS_SECTION_MARKER)
        if cutoff != -1:
            text = text[:cutoff]
        lines = [line for line in text.split("\n") if line != DESCRIPTION_HEADING]
        listing.description = "\n".join(lines).strip() or None

        match = _EXPERIENCE_RE.search(block.get_text(" ", strip=True))
        if match:
            listing.experience_level = f"{match.group(1)} años de experiencia"

    def fetch(self, rubro: str, limit: int) -> list[Listing]:
        config = computrabajo_rubro(rubro)
        now = datetime.now(timezone.utc)

        html = self._get(f"{BASE}/trabajo-de-{config.slug}")
        cards = BeautifulSoup(html, "lxml").select("article.box_offer")

        parsed = [parse_card(card, rubro, now) for card in cards]
        found = sort_latest_first([listing for listing in parsed if listing is not None])[:limit]

        for listing in found:
            self._fetch_detail(listing)
            time.sleep(random.uniform(1.0, 2.5))  # NOSONAR

        log.info(
            "message= Fetched Computrabajo listings, rubro=%s, received=%s, usable=%s",
            rubro,
            len(cards),
            len(found),
        )
        return found
