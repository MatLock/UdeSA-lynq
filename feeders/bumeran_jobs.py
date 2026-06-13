#!/usr/bin/env python3
"""Scrape job listings from Bumeran Argentina (https://www.bumeran.com.ar).

Unlike Computrabajo, Bumeran is a client-rendered React app whose listings load
from a JSON API (POST /api/avisos/searchNormalizado) sitting behind Cloudflare bot
protection. So this feeder: (1) warms a session against a public page to obtain the
Cloudflare cookie, (2) calls the public search API with the site headers, and
(3) re-warms + backs off whenever Cloudflare challenges a request.

Same spirit as computrabajo_jobs.py: takes a job type, a starting page and a page
limit, sorts latest-first, normalizes to Lynq's listing shape, and is deliberately
polite (UA rotation, randomized delays, backoff). The search endpoint is public and
not disallowed by robots.txt (which only blocks the *recientes=true HTML URLs, not
the API). For academic/development use; the site's ToS restricts commercial scraping.

Results are written to outputs/bumeran_jobs_output.json by default.

Usage:
    .venv/bin/python bumeran_jobs.py "desarrollador python"
    .venv/bin/python bumeran_jobs.py programador --page 1 --page-limit 3
    .venv/bin/python bumeran_jobs.py qa --one
"""
from __future__ import annotations

import argparse
import json
import random
import re
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

import requests

BASE = "https://www.bumeran.com.ar"
API = f"{BASE}/api/avisos/searchNormalizado"
WARMUP_URL = f"{BASE}/empleos.html"
SITE_ID = "BMAR"
OUTPUT = Path(__file__).parent / "outputs" / "bumeran_jobs_output.json"

USER_AGENTS = [
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]


def slugify(text: str) -> str:
    """'Desarrollador Python' -> 'desarrollador-python'."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    text = re.sub(r"[^a-zA-Z0-9\s-]", "", text).strip().lower()
    return re.sub(r"[\s-]+", "-", text)


def _empty_salary() -> dict:
    return {"amount": None, "min": None, "max": None, "normalizedAnnual": None, "period": None, "currency": None}


def parse_pub_date(raw: str | None):
    """'12-05-2026 22:13:00' -> epoch-ms."""
    if not raw:
        return None
    for fmt in ("%d-%m-%Y %H:%M:%S", "%d-%m-%Y"):
        try:
            dt = datetime.strptime(raw, fmt).replace(tzinfo=timezone.utc)
            return int(dt.timestamp() * 1000)
        except ValueError:
            continue
    return None


def normalize(aviso: dict) -> dict:
    """Map one Bumeran aviso to Lynq's listing shape (same fields as the other feeders)."""
    job_id = aviso.get("id")
    title = aviso.get("titulo")
    slug = slugify(title or "")
    apply_url = f"{BASE}/empleos/{slug}-{job_id}.html" if job_id else None
    modalidad = (aviso.get("modalidadTrabajo") or "")
    return {
        "id": str(job_id) if job_id is not None else "",
        "title": title,
        "company": aviso.get("empresa"),
        "location": aviso.get("localizacion"),
        "remote": "remoto" in modalidad.lower(),
        "workType": aviso.get("tipoTrabajo"),
        "experienceLevel": None,
        "description": aviso.get("detalle"),
        "skills": None,
        "salary": _empty_salary(),
        "applyUrl": apply_url,
        "views": None,
        "applies": None,
        "postedAt": parse_pub_date(aviso.get("fechaHoraPublicacion") or aviso.get("fechaPublicacion")),
        "source": "bumeran",
    }


def _new_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({
        "x-site-id": SITE_ID,
        "Origin": BASE,
        "Referer": f"{BASE}/empleos.html",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "es-AR,es;q=0.9",
    })
    return session


def _warmup(session: requests.Session) -> None:
    """Visit a public page to (re)acquire the Cloudflare __cf_bm cookie."""
    session.get(WARMUP_URL, headers={"User-Agent": random.choice(USER_AGENTS)}, timeout=25)


def _is_challenge(text: str) -> bool:
    head = text.lstrip()[:600]
    return head.startswith("<!DOCTYPE") or "Attention Required" in head or "cf-error" in head


def fetch_search(session: requests.Session, query: str, api_page: int,
                 page_size: int = 20, max_retries: int = 5) -> dict:
    """POST the search API for one (0-indexed) page, re-warming on Cloudflare challenges."""
    body = {
        "filtros": [],
        "busquedaExtendida": False,
        "query": query,
        "tipoDetalle": "full",
        "withHome": False,
        "internacional": False,
    }
    url = f"{API}?pageSize={page_size}&page={api_page}&sort=RECIENTES"
    for attempt in range(max_retries):
        resp = session.post(url, json=body, headers={"User-Agent": random.choice(USER_AGENTS)}, timeout=25)
        if resp.status_code == 200 and not _is_challenge(resp.text):
            return resp.json()
        wait = (2 ** attempt) + random.uniform(0, 1.5)
        print(f"  [{resp.status_code}] Cloudflare challenge on page {api_page}, "
              f"re-warming + backing off {wait:.1f}s...")
        time.sleep(wait)
        _warmup(session)
    raise RuntimeError(f"Giving up on page {api_page} after {max_retries} retries (Cloudflare)")


def scrape(query: str, page: int = 1, page_limit: int = 1, page_size: int = 20) -> list[dict]:
    """Scrape `page_limit` pages starting at `page` (1-indexed), return jobs latest-first."""
    session = _new_session()
    _warmup(session)

    jobs: list[dict] = []
    for i in range(page_limit):
        api_page = (page - 1) + i  # API pages are 0-indexed
        print(f"Fetching '{query}' page {page + i}...")
        content = fetch_search(session, query, api_page, page_size).get("content") or []
        if not content:
            print(f"  no results on page {page + i} — stopping.")
            break
        jobs.extend(normalize(a) for a in content)
        if i < page_limit - 1:
            time.sleep(random.uniform(2.0, 4.5))  # be polite between page requests

    jobs.sort(key=lambda j: j["postedAt"] or 0, reverse=True)
    return jobs


def fetch_one(query: str) -> dict | None:
    """Fetch the latest single posting for `query`, print it, and return it."""
    session = _new_session()
    _warmup(session)
    print(f"Fetching one '{query}' posting...")
    content = fetch_search(session, query, 0).get("content") or []
    if not content:
        print("No postings found.")
        return None
    job = normalize(content[0])
    print(json.dumps(job, indent=2, ensure_ascii=False))
    return job


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("query", help="type of job, e.g. 'desarrollador python'")
    ap.add_argument("--one", action="store_true", help="fetch and print a single posting, then exit")
    ap.add_argument("--page", type=int, default=1, help="starting page number (default 1)")
    ap.add_argument("--page-limit", type=int, default=1, help="how many pages to fetch (default 1)")
    ap.add_argument("--out", type=Path, default=OUTPUT,
                    help=f"write results to this JSON file (default: {OUTPUT.relative_to(Path(__file__).parent)})")
    args = ap.parse_args()

    if args.one:
        fetch_one(args.query)
        return

    jobs = scrape(args.query, page=args.page, page_limit=args.page_limit)
    print(f"\nGot {len(jobs)} jobs (latest first).")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(jobs, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote -> {args.out}")


if __name__ == "__main__":
    main()
