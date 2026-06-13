#!/usr/bin/env python3
"""Scrape job listings from Computrabajo Argentina (https://ar.computrabajo.com).

Takes a job type (keyword), a starting page and a page limit, fetches the
server-rendered listing pages, normalizes each card to Lynq's listing shape and
returns them sorted latest-first.

This scrapes only the public SEO listing pages (/trabajo-de-<slug>), which are
NOT disallowed by the site's robots.txt, and is deliberately polite: a realistic
User-Agent, randomized delays between requests, and backoff on 403/429. Use for
academic/development purposes; the site's ToS restricts commercial scraping.

Results are written to outputs/computrabajo_jobs_output.json by default.

Usage:
    .venv/bin/python computrabajo_jobs.py "desarrollador python"
    .venv/bin/python computrabajo_jobs.py programador --page 1 --page-limit 3 --details
    .venv/bin/python computrabajo_jobs.py qa --page-limit 2 --out some/other/path.json
"""
from __future__ import annotations

import argparse
import json
import random
import re
import time
import unicodedata
from datetime import datetime, timedelta, timezone
from pathlib import Path

import requests
from bs4 import BeautifulSoup

BASE = "https://ar.computrabajo.com"
OUTPUT = Path(__file__).parent / "outputs" / "computrabajo_jobs_output.json"

# A couple of realistic desktop UAs to rotate through.
USER_AGENTS = [
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]


def slugify(text: str) -> str:
    """'Desarrollador Python' -> 'desarrollador-python' (Computrabajo SEO slug)."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    text = re.sub(r"[^a-zA-Z0-9\s-]", "", text).strip().lower()
    return re.sub(r"[\s-]+", "-", text)


def _txt(node) -> str | None:
    if node is None:
        return None
    t = re.sub(r"\s+", " ", node.get_text(strip=True)).strip()
    return t or None


def parse_relative_date(raw: str | None, now: datetime):
    """Turn 'Hace 6 horas' / 'Ayer' / 'Hace 3 dias' into an epoch-ms timestamp."""
    if not raw:
        return None
    s = unicodedata.normalize("NFKD", raw).encode("ascii", "ignore").decode().lower()
    if "hoy" in s or "hora" in s or "minuto" in s or "segundo" in s:
        delta = timedelta(0)
        m = re.search(r"(\d+)\s*hora", s)
        if m:
            delta = timedelta(hours=int(m.group(1)))
        m = re.search(r"(\d+)\s*minuto", s)
        if m:
            delta = timedelta(minutes=int(m.group(1)))
        dt = now - delta
    elif "ayer" in s:
        dt = now - timedelta(days=1)
    elif "dia" in s:
        m = re.search(r"(\d+)", s)
        dt = now - timedelta(days=int(m.group(1)) if m else 1)
    elif "semana" in s:
        m = re.search(r"(\d+)", s)
        dt = now - timedelta(weeks=int(m.group(1)) if m else 1)
    elif "mes" in s:
        m = re.search(r"(\d+)", s)
        dt = now - timedelta(days=30 * (int(m.group(1)) if m else 1))
    elif "ano" in s:  # "año"
        m = re.search(r"(\d+)", s)
        dt = now - timedelta(days=365 * (int(m.group(1)) if m else 1))
    else:
        return None
    return int(dt.timestamp() * 1000)


def _empty_salary() -> dict:
    return {"amount": None, "min": None, "max": None, "normalizedAnnual": None, "period": None, "currency": None}


def _parse_salary(raw: str | None) -> dict:
    """Best-effort: turn a card salary string ('$ 1.500.000') into the salary object.

    Mirrors the shape emitted by the LinkedIn feeder. Computrabajo amounts use '.'
    as the thousands separator and ',' as the decimal separator (es-AR).
    """
    sal = _empty_salary()
    if not raw:
        return sal

    def to_num(s: str):
        s = s.replace(".", "").replace(",", ".")
        try:
            return float(s)
        except ValueError:
            return None

    vals = [v for v in (to_num(n) for n in re.findall(r"\d[\d.,]*", raw)) if v is not None]
    if not vals:
        return sal
    sal["currency"] = "ARS"
    if len(vals) >= 2:
        sal["min"], sal["max"] = vals[0], vals[1]
        sal["amount"] = vals[1]
    else:
        sal["amount"] = vals[0]
    return sal


def parse_card(card, now: datetime) -> dict:
    """Map one <article class="box_offer"> to Lynq's listing shape.

    Emits the SAME structure and field names as linkedin_jobs.py. Fields the
    listing card doesn't expose (experienceLevel, description, skills, views,
    applies) are set to None so both feeders are drop-in interchangeable.
    """
    title_a = card.select_one("h2 a.js-o-link")
    href = title_a.get("href", "").split("#")[0] if title_a else None
    apply_url = BASE + href if href and href.startswith("/") else href

    company_a = card.select_one("a.fc_base.t_ellipsis")

    # Location is the fs16 paragraph that has no anchor (the company one does).
    location = None
    for p in card.select("p.fs16"):
        if not p.find("a"):
            location = _txt(p.select_one("span") or p)
            break

    # Modality (Remoto / Presencial / Hibrido) lives in the fs13 block.
    modality = _txt(card.select_one("div.fs13 span"))
    remote = bool(modality and "remoto" in modality.lower())

    posted_raw = _txt(card.select_one("p.fs13.fc_aux"))

    # Best-effort salary: any span on the card mentioning a currency amount.
    salary_text = None
    for sp in card.find_all("span"):
        t = sp.get_text(" ", strip=True)
        if "$" in t and re.search(r"\d", t):
            salary_text = re.sub(r"\s+", " ", t)
            break

    return {
        "id": card.get("data-id"),
        "title": _txt(title_a),
        "company": _txt(company_a),
        "location": location,
        "remote": remote,
        "workType": modality,
        "experienceLevel": None,
        "description": None,
        "skills": None,
        "salary": _parse_salary(salary_text),
        "applyUrl": apply_url,
        "views": None,
        "applies": None,
        "postedAt": parse_relative_date(posted_raw, now),
        "source": "computrabajo",
    }


def _get(session: requests.Session, url: str, max_retries: int = 4) -> str:
    """GET a URL with retry/backoff on 403/429."""
    resp = None
    for attempt in range(max_retries):
        resp = session.get(url, headers={"User-Agent": random.choice(USER_AGENTS)}, timeout=25)
        if resp.status_code == 200:
            return resp.text
        if resp.status_code in (403, 429):
            wait = (2 ** attempt) + random.uniform(0, 1.5)
            print(f"  [{resp.status_code}] blocked, backing off {wait:.1f}s...")
            time.sleep(wait)
            continue
        resp.raise_for_status()
    raise RuntimeError(f"Giving up on {url} after {max_retries} retries (last {resp.status_code if resp else '?'})")


def fetch_page(session: requests.Session, slug: str, page: int) -> str:
    """GET one listing page."""
    url = f"{BASE}/trabajo-de-{slug}" + (f"?p={page}" if page > 1 else "")
    return _get(session, url)


def fetch_detail(session: requests.Session, url: str | None) -> dict:
    """Visit a job's detail page and pull description, skills and experience level.

    The listing cards don't carry these; they live on the offer page under
    <div description-offer> -> div.mb40.pb40.bb1. Returns a dict of the fields to
    merge into the job (keys match the LinkedIn schema).
    """
    out = {"description": None, "skills": None, "experienceLevel": None}
    if not url:
        return out
    try:
        soup = BeautifulSoup(_get(session, url), "lxml")
    except Exception as exc:  # don't let one bad detail page kill the whole run
        print(f"  could not fetch detail {url}: {exc}")
        return out

    box = soup.find(attrs={"description-offer": True})
    block = box.select_one("div.mb40.pb40.bb1") if box else None
    if not block:
        return out

    # Description: everything from the heading down to the skill-tag section.
    lines = []
    for line in block.get_text("\n", strip=True).split("\n"):
        if line.startswith("Aptitudes asociadas"):
            break
        if line == "Descripción de la oferta":
            continue
        lines.append(line)
    out["description"] = "\n".join(lines).strip() or None

    # Skills: the tag chips at the bottom of the offer.
    tags = [s.get_text(strip=True) for s in block.select("span.tag.bg_brand_light")]
    out["skills"] = ", ".join(dict.fromkeys(tags)) or None

    # Experience: "5 años de experiencia" in the requirements block, if present.
    m = re.search(r"(\d+)\s+a[nñ]os?\s+de\s+experiencia", block.get_text(" ", strip=True), re.I)
    if m:
        out["experienceLevel"] = f"{m.group(1)} años de experiencia"
    return out


def _new_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({
        "Accept-Language": "es-AR,es;q=0.9",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    })
    return session


def scrape(query: str, page: int = 1, page_limit: int = 1, details: bool = False) -> list[dict]:
    """Scrape `page_limit` pages starting at `page`, return jobs sorted latest-first.

    When `details` is True, each job's detail page is also fetched to fill in
    description/skills/experienceLevel (one extra request per job — slower).
    """
    slug = slugify(query)
    now = datetime.now(timezone.utc)
    session = _new_session()

    jobs: list[dict] = []
    for i in range(page_limit):
        p = page + i
        print(f"Fetching '{slug}' page {p}...")
        cards = BeautifulSoup(fetch_page(session, slug, p), "lxml").select("article.box_offer")
        if not cards:
            print(f"  no cards on page {p} — stopping.")
            break
        jobs.extend(parse_card(c, now) for c in cards)
        if i < page_limit - 1:
            time.sleep(random.uniform(2.0, 4.5))  # be polite between page requests

    if details:
        print(f"Fetching descriptions for {len(jobs)} jobs (1 request each)...")
        for j in jobs:
            j.update(fetch_detail(session, j["applyUrl"]))
            time.sleep(random.uniform(1.0, 2.5))

    # Latest first; cards with no parseable date sink to the bottom.
    jobs.sort(key=lambda j: j["postedAt"] or 0, reverse=True)
    return jobs


def fetch_one(query: str) -> dict | None:
    """Fetch the latest single posting for `query` (with description), print, return it."""
    slug = slugify(query)
    now = datetime.now(timezone.utc)
    session = _new_session()
    print(f"Fetching one '{slug}' posting...")
    cards = BeautifulSoup(fetch_page(session, slug, 1), "lxml").select("article.box_offer")
    if not cards:
        print("No postings found.")
        return None
    job = parse_card(cards[0], now)
    job.update(fetch_detail(session, job["applyUrl"]))  # always enrich the single result
    print(json.dumps(job, indent=2, ensure_ascii=False))
    return job


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("query", help="type of job, e.g. 'desarrollador python'")
    ap.add_argument("--one", action="store_true", help="fetch and print a single posting, then exit")
    ap.add_argument("--page", type=int, default=1, help="starting page number (default 1)")
    ap.add_argument("--page-limit", type=int, default=1, help="how many pages to fetch (default 1)")
    ap.add_argument("--details", action="store_true",
                    help="also visit each job's page for description/skills (1 extra request each)")
    ap.add_argument("--out", type=Path, default=OUTPUT,
                    help=f"write results to this JSON file (default: {OUTPUT.relative_to(Path(__file__).parent)})")
    args = ap.parse_args()

    if args.one:
        fetch_one(args.query)
        return

    jobs = scrape(args.query, page=args.page, page_limit=args.page_limit, details=args.details)
    print(f"\nGot {len(jobs)} jobs (latest first).")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(jobs, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote -> {args.out}")


if __name__ == "__main__":
    main()
