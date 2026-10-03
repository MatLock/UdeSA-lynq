# lynq-feeders

Job-listing feeder service for the Lynq platform. A FastAPI app that scrapes the Argentine job portals, asks `lynq-llm` to extract skills and similarity tags from each posting, and hands the batch to `lynq-app-backend` for persistence. It also goes back to the postings it ingested weeks ago and checks whether they are still up, so that a posting that left its portal gets closed in Lynq. A daily Kubernetes CronJob calls both; the same endpoints can be invoked on demand from inside the cluster, optionally scoped to a subset of portals and categories.

It replaces a set of standalone scripts that wrote scraped JSON to disk and then inserted it straight into MySQL. Those are gone: this service is the only thing that feeds external listings into the platform, and it does so through `lynq-app-backend` rather than by touching the database.

---

## Table of contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Categories](#categories)
- [Liveness verification](#liveness-verification)
- [API reference](#api-reference)
- [Running locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Configuration](#configuration)
- [Scheduling and on-demand runs](#scheduling-and-on-demand-runs)
- [Testing](#testing)
- [Project layout](#project-layout)

---

## Technologies

| Area             | Stack                                                     |
| ---------------- | --------------------------------------------------------- |
| Language         | Python 3.12                                               |
| Framework        | FastAPI 0.139 (Starlette), served by Uvicorn 0.50         |
| Validation       | Pydantic 2                                                |
| HTTP clients     | httpx (async) downstream, requests (sync) for scraping    |
| HTML parsing     | BeautifulSoup 4 + lxml                                    |
| Logging          | stdlib `logging` + `contextvars` MDC for correlation IDs  |
| Build            | Dockerfile on `python:3.12-slim`                          |
| Tests            | `unittest` (stdlib), FastAPI `TestClient`                 |

---

## Architecture

```
              ┌──────────────────────────┐
              │  Kubernetes CronJob      │  daily, 06:00 UTC
              └────────────┬─────────────┘
                           │ POST /lynq-feeders/ingest
                           │ lynq-request-uuid
                           ▼
              ┌──────────────────────────┐
              │      lynq-feeders        │
              └────────────┬─────────────┘
                           │
          ┌────────────────┼─────────────────┐
          ▼                ▼                 ▼
   ┌────────────┐   ┌────────────┐   ┌────────────────┐
   │  Bumeran   │   │Computrabajo│   │    lynq-llm    │
   │  searchV2  │   │ SEO pages  │   │ /skill-enhance │
   └────────────┘   └────────────┘   └────────────────┘
          │                │                 │
          └────────────────┴─────────────────┘
                           │ normalized + enriched batch
                           ▼
              ┌────────────────────────────────┐
              │        lynq-app-backend        │
              │  POST /internal/job-posts/     │
              │            ingest              │
              └────────────────────────────────┘
```

The service never touches the database. `lynq-app-backend` owns `lynq_backend_db`, so persistence goes through its internal ingest endpoint, authenticated with a shared secret.

### Run sequence

1. For every configured source and category, scrape the latest `FEEDER_JOBS_PER_CATEGORY` postings.
2. Drop duplicates by `(source, external_id)` — Bumeran merges administración and contabilidad into one area, so the same posting can surface under two categories.
3. For every posting with a description, call `lynq-llm` `/internal/skill-enhance` to get `skills` and `similarity_tags`, presenting `LYNQ_INTERNAL_TOKEN` — a scheduled scrape has no user token, and lynq-llm's `/dmz` routes want one. Calls are bounded by `LYNQ_LLM_CONCURRENCY`.
4. Post the whole batch to `lynq-app-backend` — only if every posting came back enriched.

Steps 1-4 run in the background, after the caller has been answered: nothing downstream
of the `202` can be reported back to whoever asked for the run, so it is all logged.

Each posting also carries its company's logo URL, which both portals hand over at
no extra cost: Bumeran returns `logoURL` in the same `searchV2` payload, and
Computrabajo puts it on the detail page that step 1 already fetches. A confidential
Bumeran posting has no logo, and `lynq-app-backend` keeps whatever an earlier run
found rather than blanking it. Neither portal publishes a company description, so
`about` stays empty for scraped companies.

Each posting is also sent with its `category` — the feeder category it was scraped
under, e.g. `TECNOLOGIA` — and the `salaryCurrency` of its salary range. Only
Computrabajo contributes salaries: its listing cards show a `$` range, parsed as
`ARS`. Bumeran's `searchV2` listing carries no salary at all — the only related
field, `salarioObligatorio`, says whether applicants must state their expected
salary, not what the post pays — and the feeder does not open each posting's
detail page to look for one, since that multiplies requests and the risk of being
blocked. Bumeran postings are ingested without salary or currency.

Skill extraction is not best-effort: a posting stored with no skills and no
similarity tags scores 0 on the LyNQ score for every candidate, and because the
backend replaces a job post's skills on every ingest, a degraded run also wipes
what an earlier one had extracted. So if any posting fails to enrich — `lynq-llm`
unreachable, an empty completion, or a scraper that brought back no description —
the run aborts naming every offender in the logs and nothing is ingested. Rerun it
once `lynq-llm` is healthy.

A scraper that fails for one category does not abort the others — that failure is logged per source and the run carries on.

Every posting the backend receives is stamped `last_seen_on = today`, and a posting it
had closed is reopened when it shows up again.

---

## Categories

Four categories are scraped by default. The mappings were verified against both portals' live responses:

| Category               | Bumeran area (`idSemantico`)               | Bumeran query  | Computrabajo slug  |
| ------------------- | ------------------------------------------ | -------------- | ------------------ |
| `ADMINISTRACION`    | `administracion-contabilidad-y-finanzas`   | `administracion` | `administracion` |
| `TECNOLOGIA`        | `tecnologia-sistemas-y-telecomunicaciones` | —              | `sistemas`         |
| `CONTABILIDAD`      | `administracion-contabilidad-y-finanzas`   | `contabilidad` | `contabilidad`     |
| `RECURSOS_HUMANOS`  | `recursos-humanos-y-capacitacion`          | —              | `recursos-humanos` |

Bumeran has no separate accounting area, so `ADMINISTRACION` and `CONTABILIDAD` share area id 1 and are narrowed by a query. Computrabajo has no category API at all, so it is searched by keyword slug — `sistemas` rather than `tecnologia`, because the latter matches maintenance technicians.

A category that is not in the table falls back to a keyword derived from its name, so `FEEDER_CATEGORIES` can name one that was never mapped.

---

## Liveness verification

The portals never tell Lynq that a posting ended: the ingest only sees the newest
postings, so one that left its portal would stay open forever. The verify run is what
closes them, and it is where the time to fill of external postings comes from — a
posting's days on the market end on the day the check found it gone.

### Run sequence

1. Ask `lynq-app-backend` for the postings due a check: open, external, with a job URL,
   not seen for 20 days and not checked today, at most 10 per category, the never-checked
   first. The list interleaves the categories.
2. Check at most `VERIFY_MAX_CHECKS` (40) of them, one after the other with a 1–2.5 s pause,
   with the checker of their source.
3. Report every outcome in one batch to `POST /internal/job-posts/liveness`.
4. Call `POST /internal/job-posts/expire`, which closes as `EXPIRED_BY_POLICY` every external
   posting not seen for 25 days, checked or not. Analytics keeps those out of the median:
   the posting was still up when the policy closed it.

If the report in step 3 fails, step 4 does not run: the postings found alive today would
otherwise be expired.

### What each outcome means

| Outcome | When | Backend |
| --- | --- | --- |
| `ALIVE` | the posting answers and is still taking applications | renews `last_seen_on` and `last_checked_on` |
| `CLOSED` | the portal shows the posting as finished | closes it today as `VERIFIED_CLOSED` |
| `GONE` | `404`/`410`, or a redirect away from the posting to a listing or the home page | closes it today as `VERIFIED_GONE` |
| `UNKNOWN` | timeout, network error, `429`, `5xx`, or a page the checker does not recognise | stamps `last_checked_on` only |

`UNKNOWN` is deliberately where anything unrecognised lands: a wrong `ALIVE` would keep a
dead posting open forever, while an `UNKNOWN` lets the 25-day policy close it as censored.
So if a portal changes its markup, the symptom is the `unknown` count growing against
`checked` in the run's last log line — watch that ratio.

### The markers, per portal

They were found by checking the postings already stored, on 2026-10-03, and the captures
live in `tests/fixtures/liveness/`.

- **Computrabajo** takes a finished posting down instead of marking it: the posting URL
  answers `301` to the search listing (`/trabajo-de-…` or `/empleos-en-…`). Of 24 stored
  postings, 4 redirected and 20 still showed the "Postularme" button. No "finished" page
  was ever seen, so the checker has no `CLOSED`: a posting is `ALIVE` only when the page is
  a posting (the `description-offer` block) **and** has the apply button
  (`data-href-offer-apply`). A redirect to another posting is followed, at most twice.
- **Bumeran** serves the same single-page-app shell for every posting URL, live or not, so
  the HTML says nothing. The checker asks the portal's own posting API,
  `GET /api/candidates/fichaAvisoNormalizada/{id}` (the id is the number at the end of the
  posting URL), on the same warmed-up session as the scraper. `aviso.estado` is `activo`
  while it is live; `offline` and `vencido` are finished — those two are what Bumeran's own
  front end treats as a finished posting, and 5 of 18 stored postings were `offline`. An
  unknown id answers `404`. Any other `estado` is `UNKNOWN`.

### Blocked sources

A source that answers `403` or `429`, or Bumeran's Cloudflare challenge, is cut for the
rest of the run: its remaining postings are reported `UNKNOWN` without being requested.
So is a source that fails `VERIFY_MAX_CONSECUTIVE_FAILURES` (3) times in a row on the
network; a definite answer resets that count. Being cut costs nothing but a day: the
postings go to the back of the queue and come up again.

### One run at a time, after the ingest

A second verify call while one is running is refused with `409`. A verify run also waits
for an ingest run in progress before it starts — the CronJob fires both one after the
other, and the expiry must not close a posting the ingest is about to see again.

---

## API reference

| Method | Path                    | Purpose                                      |
| ------ | ----------------------- | -------------------------------------------- |
| `GET`  | `/lynq-feeders/health`  | Liveness/readiness probe.                    |
| `POST` | `/lynq-feeders/ingest`  | Accept one feed run and start it in the background. Scoped by an optional body. |
| `POST` | `/lynq-feeders/verify`  | Accept one verify run and start it in the background, after any ingest in progress. Scoped by an optional body. |

Every route except the probe requires the `lynq-request-uuid` header; a request without it is rejected with `403`.

### `POST /lynq-feeders/ingest`

Accepts one feed run and answers **`202` with an empty body**, then runs it in the
background. A run is ~80 postings, each one an LLM generation, so holding the caller's
connection open for the whole thing made the CronJob fail on the connection rather than
on the ingest. The outcome now lives in the pod's logs, under the caller's
`lynq-request-uuid`.

Called with no body it runs the configured defaults — this is what the CronJob does:

```bash
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)"
```

An on-demand call can scope the run with an optional body. Every field is optional and falls back to the configured default, so a manual trigger does not have to be the full ~80-posting run:

```bash
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sources": ["computrabajo"], "categories": ["TECNOLOGIA"], "jobs_per_category": 2}'
```

| Field | Default | Notes |
| ----- | ------- | ----- |
| `sources` | `FEEDER_SOURCES` | `bumeran` and/or `computrabajo`. An unknown source is a `400`. |
| `categories` | `FEEDER_CATEGORIES` | A category with no mapping falls back to a keyword search. |
| `jobs_per_category` | `FEEDER_JOBS_PER_CATEGORY` | Between 1 and 50. |

The plan is validated before the `202`: an unknown source is still a `400`, and so is a
`jobs_per_category` outside 1-50. What actually ran is logged the moment the run is
accepted.

Only one run at a time. A call that arrives while one is in flight is refused with `409`
and nothing is started — the CronJob's `concurrencyPolicy: Forbid` stopped protecting
against overlap the moment the trigger began returning immediately.

| Status | Meaning |
| ------ | ------- |
| `202` | Accepted and running. No body. |
| `400` | Unknown source, or a field outside its range. Nothing was started. |
| `403` | No `lynq-request-uuid` header. |
| `409` | A run is already in progress. Nothing was started. |

Follow the run in the pod's logs, filtered by the uuid the caller sent:

```bash
kubectl -n lynq-local-namespace logs -l app=lynq-feeders -f | grep "$REQUEST_UUID"
```

It ends on one of two lines. A successful run logs its totals:

```
message= Finished feeder run, fetched=80, deduplicated=6, enriched=74, ingested_jobs=74, ingested_skills=612, ingested_similarity_tags=388
message= Finished feeder ingest run, ingested_jobs=74
```

A failed one logs the reason and ingests nothing — skill extraction failed for some
posting, each offender on its own line, or the downstream ingest was rejected, for
instance when `LYNQ_INTERNAL_TOKEN` does not match what the backend expects:

```
message= Feeder ingest run aborted, skill extraction failed
message= Feeder ingest run aborted, the job post ingest failed
```

### `POST /lynq-feeders/verify`

Accepts one verify run and answers **`202` with an empty body**; the run starts in the
background once any ingest run in progress has finished. Without a body it checks every
configured source. An optional body narrows it:

```bash
curl -X POST http://localhost:8089/lynq-feeders/verify \
  -H "lynq-request-uuid: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sources": ["computrabajo"]}'
```

Postings of a source left out of the run are neither checked nor reported: they stay
first in tomorrow's queue.

| Status | Meaning |
| ------ | ------- |
| `202` | Accepted. No body. |
| `400` | Unknown source. Nothing was started. |
| `403` | No `lynq-request-uuid` header. |
| `409` | A verify run is already in progress. Nothing was started. |

It ends on one line with the totals, after one line per source:

```
message= Verified source, source=bumeran, requested=18, alive=13, closed=5, gone=0, unknown=0, blocked_reason=None
message= Finished feeder verify run, candidates=52, checked=40, alive=31, closed=5, gone=3, unknown=1, skipped_by_backend=0, expired=6, blocked_sources=[]
```

A failed one logs `Feeder verify run aborted, a lynq-app-backend call failed`.

### `GET /lynq-feeders/health`

Reports whether `lynq-llm` and `lynq-app-backend` are reachable, but **always answers `200`**. Unlike `lynq-llm`, a down dependency is surfaced rather than fatal: the cron fires once a day, and taking the pod out of rotation because the LLM is briefly unreachable would leave nothing to fire against.

---

## Running locally

```bash
python3.12 -m venv .venv
.venv/bin/pip install --require-hashes --only-binary :all: -r requirements.txt

source ./set_env.sh
PYTHONPATH=src .venv/bin/python src/main.py
```

The service listens on `8089`. It needs `lynq-llm` on `8084` and `lynq-app-backend` on `8082` to do anything useful; `docker compose up lynq-llm lynq-app-backend` brings both up.

To exercise one category against one portal without waiting for a full run:

```bash
FEEDER_SOURCES=computrabajo FEEDER_CATEGORIES=TECNOLOGIA FEEDER_JOBS_PER_CATEGORY=2 \
  source ./set_env.sh
```

---

## Running with Docker

```bash
docker build -t lynq-feeders:local .
docker run --rm -p 8089:8089 \
  -e LYNQ_LLM_URL=http://host.docker.internal:8084/lynq-llm \
  -e LYNQ_BACKEND_URL=http://host.docker.internal:8082/lynq-backend-app \
  -e LYNQ_INTERNAL_TOKEN=... \
  lynq-feeders:local
```

Or as part of the stack: `docker compose up lynq-feeders`.

---

## Configuration

All configuration is via environment variables (see `set_env.sh` for defaults):

| Variable                       | Default                                            | Purpose                                                        |
| ------------------------------ | -------------------------------------------------- | -------------------------------------------------------------- |
| `LYNQ_LLM_URL`                 | `http://localhost:8084/lynq-llm`                   | Base URL of the skill-extraction service.                      |
| `LYNQ_BACKEND_URL`             | `http://localhost:8082/lynq-backend-app`           | Base URL of the service that owns the database.                |
| `LYNQ_INTERNAL_TOKEN`          | `local-internal-token-not-a-secret`                | Shared secret for the `/internal/**` routes of lynq-app-backend **and** lynq-llm. |
| `LYNQ_FEEDERS_SYSTEM_USER_ID`  | `00000000-0000-0000-0000-00000000feed`             | Sent as `user-id` to `lynq-llm`'s internal route; only reaches its logs. |
| `FEEDER_CATEGORIES`                | `ADMINISTRACION,TECNOLOGIA,CONTABILIDAD,RECURSOS_HUMANOS` | Categories scraped per run.                                 |
| `FEEDER_SOURCES`               | `bumeran,computrabajo`                             | Portals scraped per run.                                       |
| `FEEDER_JOBS_PER_CATEGORY`        | `10`                                               | Postings kept per category per portal, newest first.              |
| `LYNQ_LLM_CONCURRENCY`         | `2`                                                | Concurrent skill-enhance calls.                                |
| `LYNQ_LLM_TIMEOUT`             | `300`                                              | Skill-enhance timeout, in seconds.                             |
| `HTTP_TIMEOUT`                 | `30`                                               | Timeout for the backend ingest call, in seconds.               |
| `SCRAPE_TIMEOUT`               | `25`                                               | Per-request scraping and liveness-check timeout, in seconds.   |
| `VERIFY_MAX_CHECKS`            | `40`                                               | Postings checked per verify run.                               |
| `VERIFY_MAX_CONSECUTIVE_FAILURES` | `3`                                             | Network failures in a row that cut a source for the run.       |
| `HOST` / `PORT`                | `0.0.0.0` / `8089`                                 | Bind address.                                                  |

`LYNQ_INTERNAL_TOKEN` defaults to the same throwaway value `lynq-app-backend` falls back to outside its `production` profile, so the local stack ingests with no setup. That value is deliberately worthless: `application-production.yaml` leaves the token empty, and an empty expected token rejects every `/internal/**` call, so a deploy that forgets the real secret still fails loudly on the first ingest. Never commit a real value — it belongs in the cluster Secret, or in `~/.config/mendel/credentials` locally.

---

## Scheduling and on-demand runs

The service has no scheduler of its own — it is a plain HTTP service that does nothing until something calls it. Two separate workloads make up the daily run:

- a **Deployment** serving the endpoint around the clock, and
- a **CronJob** (`infrastructure/helm/templates/cronjobs/`) whose only job is to `POST` to `/ingest` and then to `/verify` at `0 6 * * *` UTC from a throwaway `curl` pod, under one `lynq-request-uuid`. It waits for the two `202`s and exits; the runs outlive it inside the Deployment's pod — the verify one waiting for the ingest — and a second run landing on top of a live one is refused there with a `409`.

Because the schedule lives entirely in Kubernetes, the same endpoint is available on demand at any time.

### Reaching it on demand

`lynq-feeders-service` is a `ClusterIP` with no Ingress, so the endpoint is reachable from inside the cluster and from nowhere else. It is never exposed to the internet and is not relayed by `lynq-bff`.

From a machine with cluster access:

```bash
kubectl -n lynq-local-namespace port-forward svc/lynq-feeders-service 8089:8089
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"categories": ["TECNOLOGIA"], "jobs_per_category": 2}'
```

From inside the cluster, without a port-forward:

```bash
kubectl -n lynq-local-namespace run feeders-trigger --rm -i --restart=Never \
  --image=curlimages/curl:8.11.1 -- \
  curl -sS -X POST "http://lynq-feeders-service:8089/lynq-feeders/ingest" \
  -H "lynq-request-uuid: 11111111-2222-3333-4444-555555555555"
```

To replay exactly what the schedule would do, run the CronJob itself:

```bash
kubectl -n lynq-local-namespace create job --from=cronjob/lynq-feeders-cronjob feeders-manual
kubectl -n lynq-local-namespace logs -f job/feeders-manual
```

That log only carries the `202`. The run itself is in the Deployment's pod: `kubectl -n lynq-local-namespace logs -l app=lynq-feeders -f`.

---

## Testing

```bash
.venv/bin/python -m coverage run -m unittest discover -s tests -t .
.venv/bin/python -m coverage report -m --include="src/*"
```

The scrapers are tested against captured HTML and API fixtures, so the suite never reaches the network.

---

## Project layout

```
lynq-feeders/
├── Dockerfile
├── requirements.in / requirements.txt   pip-compile lock with hashes
├── set_env.sh
├── resources/log_config.json
├── src/
│   ├── main.py                 app wiring, routers, uvicorn entrypoint
│   ├── config.py               environment-backed settings
│   ├── backend_client/         lynq-app-backend internal ingest and verification client
│   ├── llm_client/             lynq-llm skill-enhance client
│   ├── middleware/             lynq-request-uuid enforcement
│   ├── model/                  ingest and verify request overrides and run plans
│   ├── response/               GlobalRestResponse envelopes
│   ├── router/                 health, ingest and verify routes; the shared run lock
│   ├── scraper/                base model, category mapping, one module per portal with
│   │                           its scraper and its liveness checker
│   └── service/                ingest and verify run orchestration
└── tests/
    └── fixtures/liveness/      captured postings the checkers are tested against
```

## Scraping policy

Both portals are scraped politely: rotating User-Agents, randomized delays between requests, and exponential backoff on `403`/`429` and Cloudflare challenges. Bumeran is read through its public `searchV2` JSON API and Computrabajo through the public SEO listing pages, neither of which is disallowed by the sites' `robots.txt`. This is for academic and development use; both sites' terms of service restrict commercial scraping.
