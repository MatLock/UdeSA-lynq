#!/usr/bin/env python3
"""Fetch a public LinkedIn job-postings dataset from Hugging Face and normalize it.

This is NOT a live scraper. It pulls a static, real-world dataset of LinkedIn
postings so the rest of Lynq can be developed against realistic listing data.
No auth token is required — the dataset is public on the HF Hub.

Dataset: https://huggingface.co/datasets/datastax/linkedin_job_listings

Usage:
    .venv/bin/python linkedin_jobs.py            # 500 rows -> outputs/linked_jobs_output.json
    .venv/bin/python linkedin_jobs.py --limit 50
    .venv/bin/python linkedin_jobs.py --out some/other/path.json
"""
from __future__ import annotations

import argparse
import itertools
import json
from pathlib import Path

from datasets import load_dataset

DATASET = "datastax/linkedin_job_listings"
OUTPUT = Path(__file__).parent / "outputs" / "linked_jobs_output.json"


def _clean(value):
    """Normalize the dataset's stringly-typed nulls ('None', '', 'nan') to None."""
    if value is None:
        return None
    if isinstance(value, str):
        value = value.strip()
        if value == "" or value.lower() in ("none", "nan", "null"):
            return None
    return value


def _num(value):
    """Best-effort float; returns None for missing/garbage values."""
    value = _clean(value)
    if value is None:
        return None
    try:
        return float(value)
    except (ValueError, TypeError):
        return None


def _int(value):
    n = _num(value)
    return int(n) if n is not None else None


def _salary(row) -> dict:
    """Collapse the dataset's min/med/max columns into one best-effort figure."""
    amount = _num(row.get("med_salary")) or _num(row.get("max_salary")) or _num(row.get("min_salary"))
    return {
        "amount": amount,
        "min": _num(row.get("min_salary")),
        "max": _num(row.get("max_salary")),
        "normalizedAnnual": _num(row.get("normalized_salary")),
        "period": _clean(row.get("pay_period")),
        "currency": _clean(row.get("currency")) or "USD",
    }


def normalize(row) -> dict:
    """Map a raw dataset row to Lynq's listing shape."""
    remote = _clean(row.get("remote_allowed"))
    return {
        "id": str(_clean(row.get("job_id")) or ""),
        "title": _clean(row.get("title")),
        "company": _clean(row.get("company_name")),
        "location": _clean(row.get("location")),
        "remote": str(remote).lower() in ("true", "1", "1.0") if remote is not None else False,
        "workType": _clean(row.get("formatted_work_type")),
        "experienceLevel": _clean(row.get("formatted_experience_level")),
        "description": _clean(row.get("description")),
        "skills": _clean(row.get("skills_desc")),
        "salary": _salary(row),
        "applyUrl": _clean(row.get("application_url")) or _clean(row.get("job_posting_url")),
        "views": _int(row.get("views")),
        "applies": _int(row.get("applies")),
        "postedAt": _int(row.get("original_listed_time")) or _int(row.get("listed_time")),
        "source": "linkedin",
    }


def fetch_one() -> dict:
    """Stream a single posting from the dataset, print it, and return it."""
    print(f"Streaming one posting from '{DATASET}'...")
    ds = load_dataset(DATASET, split="train", streaming=True)
    job = normalize(next(iter(ds)))
    print(json.dumps(job, indent=2, ensure_ascii=False))
    return job


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--one", action="store_true", help="fetch and print a single posting, then exit")
    ap.add_argument("--limit", type=int, default=500, help="max rows to keep (default 500, 0 = all)")
    ap.add_argument("--out", type=Path, default=OUTPUT)
    args = ap.parse_args()

    if args.one:
        fetch_one()
        return

    print(f"Streaming dataset '{DATASET}' from Hugging Face (no auth needed)...")
    ds = load_dataset(DATASET, split="train", streaming=True)
    rows = ds if not args.limit or args.limit <= 0 else itertools.islice(ds, args.limit)

    jobs = [normalize(row) for row in rows]

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(jobs, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote {len(jobs)} jobs -> {args.out}")


if __name__ == "__main__":
    main()
