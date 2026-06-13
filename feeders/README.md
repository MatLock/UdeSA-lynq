# feeders

Tooling to seed Lynq with realistic job-listing data **for development**.

Three feeders are available:

1. **`linkedin_jobs.py`** — pulls a static, public LinkedIn dataset from the
   Hugging Face Hub. No auth, no scraping. Best for bulk seed data.
2. **`computrabajo_jobs.py`** — a polite live scraper for Computrabajo Argentina. Best
   for fresh, local (Argentine) listings.
3. **`bumeran_jobs.py`** — a polite live scraper for Bumeran Argentina (via its public
   JSON API, behind Cloudflare). Fresh local listings with descriptions inline.

All three normalize to the same listing shape used by the rest of the platform.

## Setup

Dependencies are already installed in `.venv/` (`datasets`, `requests`,
`beautifulsoup4`, `lxml`). If you ever recreate it:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

---

# Computrabajo Argentina scraper (`computrabajo_jobs.py`)

Scrapes the public SEO listing pages (`/trabajo-de-<slug>`), which are **not**
disallowed by the site's [robots.txt](https://ar.computrabajo.com/robots.txt). It's
deliberately polite: realistic User-Agent rotation, randomized 2–4.5s delays
between pages, and exponential backoff on `403`/`429`. For academic/development use
— the site's ToS restricts commercial scraping.

It takes a **job type**, a **starting page**, and a **page limit**, and returns
results **sorted latest-first** (parsed from the "Hace X horas / Ayer / …" labels).

```bash
# latest 'desarrollador python' jobs from page 1
.venv/bin/python computrabajo_jobs.py "desarrollador python"

# fetch 3 pages starting at page 1
.venv/bin/python computrabajo_jobs.py programador --page 1 --page-limit 3

# also fetch each job's description/skills/experience (1 extra request per job)
.venv/bin/python computrabajo_jobs.py programador --page-limit 2 --details
```

Results are written to **`outputs/computrabajo_jobs_output.json`** by default; pass
`--out some/other/path.json` to override.

### Descriptions (`--details`)

The listing cards only carry title/company/location — the **description**, **skills**
and **experience level** live on each job's detail page. Pass `--details` to follow
`applyUrl` into each posting and fill those fields. This costs one extra request per
job (rate-limited and polite), so it's **off by default** for bulk scrapes.

`--details` combines with pagination, so to scrape several pages **with** full
descriptions (e.g. for offline DB population):

```bash
.venv/bin/python computrabajo_jobs.py "desarrollador python" --page-limit 10 --details
```

This grabs 10 pages (~200 listings), enriches each with its description, sorts
latest-first, and writes one JSON array (to `outputs/computrabajo_jobs_output.json`)
ready to ingest.

> **Timing:** with `--details`, every job is one extra request (randomized 1–2.5s
> delay), plus 2–4.5s between pages — so ~10 pages ≈ 6–9 minutes. This pacing is
> deliberate to avoid the `403`s the site returns on bursts. A detail page that
> fails degrades gracefully (that job keeps `description: null`) instead of aborting
> the run. The `id` field is stable and unique — use it as your upsert key.

### Print a single posting to the console

To fetch just the latest posting for a job type and print it (no file written):

```bash
.venv/bin/python computrabajo_jobs.py "desarrollador python" --one --details
```

`--one` **always** includes the full description (it's just one extra request). The
output matches the LinkedIn feeder's schema exactly, so both feeders are drop-in
interchangeable.

Each result:

```json
{
  "id": "2989097B8E055F1F61373E686DCF3405",
  "title": "Desarrollador Python Senior / Remoto",
  "company": "ADN - Recursos Humanos",
  "location": "Palermo, Capital Federal",
  "remote": true,
  "workType": "Remoto",
  "experienceLevel": "5 años de experiencia",
  "description": "Responsabilidades principales: -Diseñar, desarrollar...",
  "skills": "Python, Cloud, Arquitectura, Integración de apis, ...",
  "salary": { "amount": null, "min": null, "max": null, "normalizedAnnual": null, "period": null, "currency": null },
  "applyUrl": "https://ar.computrabajo.com/ofertas-de-trabajo/...",
  "views": null,
  "applies": null,
  "postedAt": 1781364820707,
  "source": "computrabajo"
}
```

> Notes:
> - `description`, `skills`, `experienceLevel` are only populated with `--details`
>   (or `--one`); without it they're `null`.
> - `salary` is best-effort — Computrabajo rarely shows pay, so it's usually `null`.
> - `views`/`applies` aren't exposed by Computrabajo, so they're always `null`
>   (kept for schema parity with the LinkedIn feeder).

---

# Bumeran Argentina scraper (`bumeran_jobs.py`)

Bumeran is a client-rendered React app, so there's no HTML to parse — its listings
load from a JSON API (`POST /api/avisos/searchNormalizado`) that sits behind
**Cloudflare** bot protection. This feeder warms a session against a public page to
get the Cloudflare cookie, calls the public search API, and **re-warms + backs off**
whenever Cloudflare challenges a request. The search endpoint is public and not
disallowed by [robots.txt](https://www.bumeran.com.ar/robots.txt) (which only blocks
the `*recientes=true` HTML URLs, not the API). For academic/development use — the
site's ToS restricts commercial scraping.

It takes a **job type**, a **starting page**, and a **page limit**, sorts
**latest-first** (`sort=RECIENTES`), and — unlike Computrabajo — returns the full
**description inline**, so no `--details` step is needed.

```bash
# latest 'desarrollador python' jobs from page 1
.venv/bin/python bumeran_jobs.py "desarrollador python"

# fetch 3 pages starting at page 1
.venv/bin/python bumeran_jobs.py programador --page 1 --page-limit 3

# print just the latest single posting (no file written)
.venv/bin/python bumeran_jobs.py qa --one
```

Results are written to **`outputs/bumeran_jobs_output.json`** by default; pass
`--out some/other/path.json` to override. Output matches the other feeders' schema
exactly.

> Notes:
> - `description` is always populated (it comes back inline with each listing).
> - `salary`, `skills`, `experienceLevel`, `views`, `applies` aren't exposed by this
>   API, so they're `null` (kept for schema parity).
> - Cloudflare may intermittently challenge; the feeder retries with backoff, but very
>   large runs can be slower or occasionally need a re-run.

---

# LinkedIn dataset feeder (`linkedin_jobs.py`)

## Fetch the data

```bash
.venv/bin/python linkedin_jobs.py            # 500 jobs
.venv/bin/python linkedin_jobs.py --limit 50 # smaller sample
.venv/bin/python linkedin_jobs.py --limit 0  # the whole dataset
```

The dataset streams from HF, so a small `--limit` only downloads what it needs.
Results are written to **`outputs/linked_jobs_output.json`** by default; pass
`--out some/other/path.json` to override.

### Print a single posting to the console

To fetch just one posting and print it (no file written):

```bash
.venv/bin/python linkedin_jobs.py --one
```

The HF rate-limit warning is harmless. To silence it, hide stderr:

```bash
.venv/bin/python linkedin_jobs.py --one 2>/dev/null
```

Output is an array of listings:

```json
{
  "id": "921716",
  "title": "Marketing Coordinator",
  "company": "Corcoran Sawyer Smith",
  "location": "Princeton, NJ",
  "remote": false,
  "workType": "Full-time",
  "experienceLevel": null,
  "description": "...",
  "skills": "...",
  "salary": { "amount": 20.0, "min": 17.0, "max": 20.0, "normalizedAnnual": 38480.0, "period": "HOURLY", "currency": "USD" },
  "applyUrl": "https://www.linkedin.com/jobs/view/921716/...",
  "views": 20,
  "applies": 2,
  "postedAt": 1713397508000,
  "source": "linkedin"
}
```

Dataset: https://huggingface.co/datasets/datastax/linkedin_job_listings
