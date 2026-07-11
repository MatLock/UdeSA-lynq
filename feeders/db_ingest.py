#!/usr/bin/env python3
"""Ingest normalized feeder listings into the Lynq backend database.

The three feeders (`linkedin_jobs.py`, `computrabajo_jobs.py`, `bumeran_jobs.py`)
all emit the same normalized listing shape and write it to a JSON array under
`outputs/`. This module reads those arrays and inserts them into the
`lynq_backend_db` MySQL schema owned by `lynq-app-backend`, mapping each listing
onto the `companies`, `job_posts` and `job_post_skills` tables.

The schema mirrors `lynq-app-backend/src/main/resources/changelog/ddl`:
    companies       (id, name, about, size, profile_image_url, created_on, ...)
    job_posts       (id, title, description, work_type, salary_range_down,
                     salary_range_top, job_url, job_post_source,
                     created_by_user_id, company_id, created_on, job_status)
    job_post_skills (id, job_id, skill)

Ingestion is idempotent: every row's primary key is a deterministic UUIDv5
derived from stable inputs (source+listing-id for jobs, name for companies,
job+skill for skills), so re-running the same feed updates rows in place via
`INSERT ... ON DUPLICATE KEY UPDATE` instead of creating duplicates.

Usage:
    # ingest a single feeder's output
    .venv/bin/python db_ingest.py outputs/linked_jobs_output.json

    # ingest several files at once
    .venv/bin/python db_ingest.py outputs/*.json

    # override connection settings (defaults shown)
    .venv/bin/python db_ingest.py outputs/bumeran_jobs_output.json \
        --host 127.0.0.1 --port 3306 --user root --password federico \
        --database lynq_backend_db
"""
from __future__ import annotations

import argparse
import json
import uuid
from datetime import date, datetime, timezone
from pathlib import Path

import pymysql

# --- defaults (overridable via CLI / env) -----------------------------------
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 3306
DEFAULT_USER = "root"
DEFAULT_PASSWORD = "federico"
DEFAULT_DATABASE = "lynq_backend_db"

# Deterministic-UUID namespace so keys are stable across runs (idempotent upserts).
NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "lynq.feeders")

# Enum domains defined by the backend DDL.
_WORK_TYPES = {"REMOTE", "IN_OFFICE"}
_JOB_POST_SOURCES = {"LYNQ", "LINKEDIN", "COMPUTRABAJO", "BUMERAN"}


def _det_uuid(*parts) -> str:
    """A stable 36-char UUIDv5 from the given parts (used as a table PK)."""
    return str(uuid.uuid5(NAMESPACE, "|".join(str(p) for p in parts)))


def _truncate(value, length):
    """Trim a string to the column's max length (None-safe)."""
    if value is None:
        return None
    value = str(value)
    return value[:length] if len(value) > length else value


def _int(value):
    """Best-effort int; None for missing/garbage (salary columns are INT)."""
    if value is None:
        return None
    try:
        return int(float(value))
    except (ValueError, TypeError):
        return None


def _created_on(listing) -> date:
    """Derive the posting date from `postedAt` (epoch millis), else today."""
    posted_at = _int(listing.get("postedAt"))
    if posted_at:
        try:
            return datetime.fromtimestamp(posted_at / 1000, tz=timezone.utc).date()
        except (ValueError, OverflowError, OSError):
            pass
    return datetime.now(tz=timezone.utc).date()


def _work_type(listing) -> str:
    """Map the listing onto the backend's REMOTE / IN_OFFICE enum."""
    return "REMOTE" if listing.get("remote") else "IN_OFFICE"


def _job_post_source(listing) -> str:
    """Map the feeder's `source` onto the JobPostSource enum (LYNQ fallback)."""
    source = str(listing.get("source") or "").strip().upper()
    return source if source in _JOB_POST_SOURCES else "LYNQ"


def _skills(listing) -> list[str]:
    """Split the comma-separated `skills` string into unique, non-empty tokens."""
    raw = listing.get("skills")
    if not raw:
        return []
    seen, out = set(), []
    for token in str(raw).split(","):
        skill = _truncate(token.strip(), 255)
        if skill and skill.lower() not in seen:
            seen.add(skill.lower())
            out.append(skill)
    return out


def _upsert_company(cursor, name) -> str | None:
    """Insert (or no-op) a company by name.

    Returns a (company_id, inserted) tuple, where `inserted` is True only when a
    new row was created (so callers can count genuinely-new companies).
    """
    name = _truncate(name, 255)
    if not name:
        return None, False
    company_id = _det_uuid("company", name.lower())
    cursor.execute(
        """
        INSERT INTO companies (id, name, created_on)
        VALUES (%s, %s, %s)
        ON DUPLICATE KEY UPDATE id = id
        """,
        (company_id, name, datetime.now(tz=timezone.utc).date()),
    )
    # rowcount == 1 on a fresh insert, 0 when the company already existed.
    return company_id, cursor.rowcount == 1


def _upsert_job(cursor, listing, company_id) -> str | None:
    """Insert or update one job post; return its id (None if unusable)."""
    title = _truncate(listing.get("title"), 255)
    if not title:
        return None  # title is NOT NULL — skip listings without one

    job_id = _det_uuid(listing.get("source"), listing.get("id"))
    salary = listing.get("salary") or {}
    cursor.execute(
        """
        INSERT INTO job_posts
            (id, title, description, work_type, salary_range_down, salary_range_top,
             job_url, job_post_source, company_id, created_on, job_status)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'OPEN')
        ON DUPLICATE KEY UPDATE
            title             = VALUES(title),
            description       = VALUES(description),
            work_type         = VALUES(work_type),
            salary_range_down = VALUES(salary_range_down),
            salary_range_top  = VALUES(salary_range_top),
            job_url           = VALUES(job_url),
            job_post_source   = VALUES(job_post_source),
            company_id        = VALUES(company_id)
        """,
        (
            job_id,
            title,
            listing.get("description"),
            _work_type(listing),
            _int(salary.get("min")),
            _int(salary.get("max")),
            _truncate(listing.get("applyUrl"), 2048),
            _job_post_source(listing),
            company_id,
            _created_on(listing),
        ),
    )
    return job_id


def _upsert_skills(cursor, job_id, skills) -> int:
    """Insert a job's skills (unique per (job_id, skill)); return count inserted."""
    inserted = 0
    for skill in skills:
        cursor.execute(
            """
            INSERT INTO job_post_skills (id, job_id, skill)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE id = id
            """,
            (_det_uuid("skill", job_id, skill.lower()), job_id, skill),
        )
        inserted += cursor.rowcount and 1 or 0
    return inserted


def insert_jobs(
    listings,
    *,
    host=DEFAULT_HOST,
    port=DEFAULT_PORT,
    user=DEFAULT_USER,
    password=DEFAULT_PASSWORD,
    database=DEFAULT_DATABASE,
) -> dict:
    """Insert normalized feeder listings into the Lynq backend database.

    Each listing is mapped onto a company (upserted by name), a job post, and
    its skills. The whole batch runs in one transaction and is committed only if
    every listing succeeds; on error the transaction is rolled back.

    Args:
        listings: an iterable of normalized listing dicts (feeder output shape).
        host/port/user/password/database: MySQL connection settings.

    Returns:
        A dict with counts: {"jobs", "companies", "skills", "skipped"}.
    """
    connection = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset="utf8mb4",
        autocommit=False,
    )
    stats = {"jobs": 0, "companies": 0, "skills": 0, "skipped": 0}
    try:
        with connection.cursor() as cursor:
            for listing in listings:
                company_id, company_inserted = _upsert_company(cursor, listing.get("company"))
                if company_inserted:
                    stats["companies"] += 1

                job_id = _upsert_job(cursor, listing, company_id)
                if job_id is None:
                    stats["skipped"] += 1
                    continue
                stats["jobs"] += 1
                stats["skills"] += _upsert_skills(cursor, job_id, _skills(listing))
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()
    return stats


def _load_listings(paths) -> list:
    """Read and concatenate the listing arrays from one or more JSON files."""
    listings = []
    for path in paths:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        if isinstance(data, dict):
            data = [data]
        listings.extend(data)
    return listings


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+", type=Path, help="feeder output JSON file(s) to ingest")
    ap.add_argument("--host", default=DEFAULT_HOST)
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    ap.add_argument("--user", default=DEFAULT_USER)
    ap.add_argument("--password", default=DEFAULT_PASSWORD)
    ap.add_argument("--database", default=DEFAULT_DATABASE)
    args = ap.parse_args()

    listings = _load_listings(args.files)
    print(f"Loaded {len(listings)} listing(s) from {len(args.files)} file(s).")

    stats = insert_jobs(
        listings,
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
    )
    print(
        f"Ingested {stats['jobs']} job(s), {stats['companies']} new company(ies), "
        f"{stats['skills']} skill(s); skipped {stats['skipped']} (missing title)."
    )


if __name__ == "__main__":
    main()
