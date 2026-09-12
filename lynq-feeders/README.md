# lynq-feeders

Job-listing feeder service for the Lynq platform. A FastAPI app that scrapes the Argentine job portals, asks `lynq-ml` to extract skills and similarity tags from each posting, and hands the batch to `lynq-app-backend` for persistence. A daily Kubernetes CronJob calls it; the same endpoint can be invoked on demand from inside the cluster, optionally scoped to a subset of portals and rubros.

It replaces a set of standalone scripts that wrote scraped JSON to disk and then inserted it straight into MySQL. Those are gone: this service is the only thing that feeds external listings into the platform, and it does so through `lynq-app-backend` rather than by touching the database.

---

## Table of contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Rubros](#rubros)
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
   │  Bumeran   │   │Computrabajo│   │    lynq-ml     │
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

1. For every configured source and rubro, scrape the latest `FEEDER_JOBS_PER_RUBRO` postings.
2. Drop duplicates by `(source, external_id)` — Bumeran merges administración and contabilidad into one area, so the same posting can surface under two rubros.
3. For every posting with a description, call `lynq-ml` `/dmz/skill-enhance` to get `skills` and `similarity_tags`. Calls are bounded by `ML_CONCURRENCY`.
4. Post the whole batch to `lynq-app-backend`.

A posting whose skill extraction fails is still ingested, with empty skills and tags. A scraper that fails for one rubro does not abort the others — the failure is reported per source in the response.

---

## Rubros

Four rubros are scraped by default. The mappings were verified against both portals' live responses:

| Rubro               | Bumeran area (`idSemantico`)               | Bumeran query  | Computrabajo slug  |
| ------------------- | ------------------------------------------ | -------------- | ------------------ |
| `ADMINISTRACION`    | `administracion-contabilidad-y-finanzas`   | `administracion` | `administracion` |
| `TECNOLOGIA`        | `tecnologia-sistemas-y-telecomunicaciones` | —              | `sistemas`         |
| `CONTABILIDAD`      | `administracion-contabilidad-y-finanzas`   | `contabilidad` | `contabilidad`     |
| `RECURSOS_HUMANOS`  | `recursos-humanos-y-capacitacion`          | —              | `recursos-humanos` |

Bumeran has no separate accounting area, so `ADMINISTRACION` and `CONTABILIDAD` share area id 1 and are narrowed by a query. Computrabajo has no category API at all, so it is searched by keyword slug — `sistemas` rather than `tecnologia`, because the latter matches maintenance technicians.

A rubro that is not in the table falls back to a keyword derived from its name, so `FEEDER_RUBROS` can name one that was never mapped.

---

## API reference

| Method | Path                    | Purpose                                      |
| ------ | ----------------------- | -------------------------------------------- |
| `GET`  | `/lynq-feeders/health`  | Liveness/readiness probe.                    |
| `POST` | `/lynq-feeders/ingest`  | Run one feed and ingest the results. Scoped by an optional body. |

Every route except the probe requires the `lynq-request-uuid` header; a request without it is rejected with `403`.

### `POST /lynq-feeders/ingest`

Runs one feed. Called with no body it runs the configured defaults — this is what the CronJob does:

```bash
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)"
```

An on-demand call can scope the run with an optional body. Every field is optional and falls back to the configured default, so a manual trigger does not have to be the full ~80-posting run:

```bash
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sources": ["computrabajo"], "rubros": ["TECNOLOGIA"], "jobs_per_rubro": 2}'
```

| Field | Default | Notes |
| ----- | ------- | ----- |
| `sources` | `FEEDER_SOURCES` | `bumeran` and/or `computrabajo`. An unknown source is a `400`. |
| `rubros` | `FEEDER_RUBROS` | A rubro with no mapping falls back to a keyword search. |
| `jobs_per_rubro` | `FEEDER_JOBS_PER_RUBRO` | Between 1 and 50. |

The response's `plan` echoes what actually ran, so a scoped call is self-documenting.

```json
{
  "success": true,
  "data": {
    "plan": {
      "sources": ["bumeran", "computrabajo"],
      "rubros": ["ADMINISTRACION", "TECNOLOGIA", "CONTABILIDAD", "RECURSOS_HUMANOS"],
      "jobs_per_rubro": 10
    },
    "fetched": 80,
    "deduplicated": 6,
    "enriched": 71,
    "enrichment_failed": 3,
    "ingested": { "jobs": 74, "companies": 41, "skills": 612, "similarity_tags": 388, "skipped": 0 },
    "per_source": [
      { "source": "bumeran", "rubro": "TECNOLOGIA", "fetched": 10, "error": null }
    ]
  }
}
```

Returns `502` when the downstream ingest fails — for instance when `LYNQ_INTERNAL_TOKEN` does not match what the backend expects.

### `GET /lynq-feeders/health`

Reports whether `lynq-ml` and `lynq-app-backend` are reachable, but **always answers `200`**. Unlike `lynq-ml`, a down dependency is surfaced rather than fatal: the cron fires once a day, and taking the pod out of rotation because the LLM is briefly unreachable would leave nothing to fire against.

---

## Running locally

```bash
python3.12 -m venv .venv
.venv/bin/pip install --require-hashes --only-binary :all: -r requirements.txt

source ./set_env.sh
PYTHONPATH=src .venv/bin/python src/main.py
```

The service listens on `8089`. It needs `lynq-ml` on `8084` and `lynq-app-backend` on `8082` to do anything useful; `docker compose up lynq-ml lynq-app-backend` brings both up.

To exercise one rubro against one portal without waiting for a full run:

```bash
FEEDER_SOURCES=computrabajo FEEDER_RUBROS=TECNOLOGIA FEEDER_JOBS_PER_RUBRO=2 \
  source ./set_env.sh
```

---

## Running with Docker

```bash
docker build -t lynq-feeders:local .
docker run --rm -p 8089:8089 \
  -e LYNQ_ML_URL=http://host.docker.internal:8084/lynq-ml \
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
| `LYNQ_ML_URL`                  | `http://localhost:8084/lynq-ml`                    | Base URL of the skill-extraction service.                      |
| `LYNQ_BACKEND_URL`             | `http://localhost:8082/lynq-backend-app`           | Base URL of the service that owns the database.                |
| `LYNQ_INTERNAL_TOKEN`          | — (empty)                                          | Shared secret for the backend's `/internal/**` routes.         |
| `LYNQ_FEEDERS_SYSTEM_USER_ID`  | `00000000-0000-0000-0000-00000000feed`             | Sent as `user-id` to `lynq-ml`; only reaches its logs.         |
| `FEEDER_RUBROS`                | `ADMINISTRACION,TECNOLOGIA,CONTABILIDAD,RECURSOS_HUMANOS` | Rubros scraped per run.                                 |
| `FEEDER_SOURCES`               | `bumeran,computrabajo`                             | Portals scraped per run.                                       |
| `FEEDER_JOBS_PER_RUBRO`        | `10`                                               | Postings kept per rubro per portal, newest first.              |
| `ML_CONCURRENCY`               | `2`                                                | Concurrent skill-enhance calls.                                |
| `ML_TIMEOUT`                   | `300`                                              | Skill-enhance timeout, in seconds.                             |
| `HTTP_TIMEOUT`                 | `30`                                               | Timeout for the backend ingest call, in seconds.               |
| `SCRAPE_TIMEOUT`               | `25`                                               | Per-request scraping timeout, in seconds.                      |
| `HOST` / `PORT`                | `0.0.0.0` / `8089`                                 | Bind address.                                                  |

`LYNQ_INTERNAL_TOKEN` is empty by default on purpose: a deploy that forgets it fails loudly on the first ingest instead of silently posting unauthenticated. Never commit a value — it belongs in the cluster Secret, or in `~/.config/mendel/credentials` locally.

---

## Scheduling and on-demand runs

The service has no scheduler of its own — it is a plain HTTP service that does nothing until something calls it. Two separate workloads make up the daily run:

- a **Deployment** serving the endpoint around the clock, and
- a **CronJob** (`infrastructure/helm/templates/cronjobs/`) whose only job is to `POST` to that endpoint at `0 6 * * *` UTC from a throwaway `curl` pod, with `concurrencyPolicy: Forbid` so a slow run is never overlapped by the next one.

Because the schedule lives entirely in Kubernetes, the same endpoint is available on demand at any time.

### Reaching it on demand

`lynq-feeders-service` is a `ClusterIP` with no Ingress, so the endpoint is reachable from inside the cluster and from nowhere else. It is never exposed to the internet and is not relayed by `lynq-bff`.

From a machine with cluster access:

```bash
kubectl -n lynq-local-namespace port-forward svc/lynq-feeders-service 8089:8089
curl -X POST http://localhost:8089/lynq-feeders/ingest \
  -H "lynq-request-uuid: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"rubros": ["TECNOLOGIA"], "jobs_per_rubro": 2}'
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
│   ├── backend_client/         lynq-app-backend internal ingest client
│   ├── ml_client/              lynq-ml skill-enhance client
│   ├── middleware/             lynq-request-uuid enforcement
│   ├── model/                  ingest request overrides and run plan
│   ├── response/               GlobalRestResponse envelopes
│   ├── router/                 health + ingest routes
│   ├── scraper/                base model, rubro mapping, one module per portal
│   └── service/                run orchestration
└── tests/
```

## Scraping policy

Both portals are scraped politely: rotating User-Agents, randomized delays between requests, and exponential backoff on `403`/`429` and Cloudflare challenges. Bumeran is read through its public `searchV2` JSON API and Computrabajo through the public SEO listing pages, neither of which is disallowed by the sites' `robots.txt`. This is for academic and development use; both sites' terms of service restrict commercial scraping.
