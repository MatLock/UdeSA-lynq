# lynq-ml

Machine-learning service for the Lynq platform. A FastAPI app that augments the platform with LLM-backed features, served behind the standard `lynq-request-uuid` correlation header and the platform's `GlobalRestResponse` envelope. It currently exposes a **skill-enhancement** endpoint that extracts key technical skills from a job posting, backed by a pluggable LLM client (a local **Ollama** model by default, or **OpenAI**).

---

## Table of contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Request lifecycle](#request-lifecycle)
- [Skill extraction flow](#skill-extraction-flow)
- [API reference](#api-reference)
- [Sample requests](#sample-requests)
- [Running locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Configuration](#configuration)
- [Observability](#observability)
- [Testing](#testing)
- [Project layout](#project-layout)

---

## Technologies

| Area              | Stack                                                                     |
| ----------------- | ------------------------------------------------------------------------- |
| Language          | Python 3.12                                                               |
| Framework         | FastAPI 0.139 (Starlette), served by Uvicorn 0.50                         |
| Validation        | Pydantic 2                                                                |
| LLM backends      | Ollama (`/api/generate`, raw mode) or OpenAI-compatible `/chat/completions` |
| Prompting         | Jinja2 templates, one variant per provider                                |
| HTTP client       | httpx (async)                                                             |
| Document parsing  | pypdf + python-docx (resume reader helpers)                               |
| Logging           | stdlib `logging` + `contextvars` MDC for per-request correlation IDs      |
| Build             | Dockerfile on `python:3.12-slim`                                          |
| Tests             | `unittest` (stdlib), FastAPI `TestClient`                                 |

---

## Architecture

```
                        ┌───────────────────────────┐
                        │       Client (HTTP)       │
                        └─────────────┬─────────────┘
                                      │  lynq-request-uuid, user-id, company-id
                                      ▼
                ┌───────────────────────────────────────────┐
                │            HTTP middleware                │
                │   require_request_uuid   (all routes)     │
                │   + standard error-envelope handlers      │
                └─────────────┬─────────────────────────────┘
                              │
                              ▼
                ┌─────────────────────────────────┐
                │        skill_enhance router     │  POST /lynq-ml/skill-enhance
                └─────────────┬───────────────────┘
                              │
              ┌───────────────┼────────────────────┐
              ▼               ▼                     ▼
      ┌──────────────┐ ┌──────────────┐    ┌──────────────────┐
      │ get_llm_client│ │ render_key_  │    │  LLMClient       │
      │ (factory)     │ │ extractor_   │    │  .generate()     │
      │               │ │ prompt (jinja)│   │                  │
      └───────┬───────┘ └──────────────┘    └────────┬─────────┘
              │                                       │
              ▼                                       ▼
        selects provider                    ┌────────────────────┐
        from LLM_PROVIDER                    │ Ollama  /  OpenAI  │
                                             │  HTTP API          │
                                             └────────────────────┘
```

**Layers**

- **Entrypoint** (`main.py`) — builds the FastAPI app, registers the request-UUID middleware, wires the standard exception handlers (`HTTPException`, `RequestValidationError`, catch-all), mounts the `/lynq-ml` router, and configures logging.
- **Middleware** (`middleware/`) — `require_request_uuid` enforces the `lynq-request-uuid` header on every non-exempt route and binds it to the logging context.
- **Feature router** (`skill_enhance/`) — the `POST /skill-enhance` endpoint plus its request/response models and the Jinja prompt renderer.
- **LLM clients** (`llm_client/`) — a common `LLMClient` interface with `OllamaClient` and `OpenAIClient` implementations, selected by the `get_llm_client()` factory from environment configuration.
- **Prompts** (`prompts/`) — provider-specific Jinja templates (`skill_extractor/ollama.jinja`, `skill_extractor/openai.jinja`).
- **Response envelopes** (`response/`) — `GlobalRestResponse` / `ErrorRestResponse`, mirroring the Java services.
- **Logging context** (`logging_context.py`) — the MDC-style request-UUID contextvar and logging filter.
- **Document helpers** (`file_downloader/`, `file_reader/`) — download a resume from a presigned S3 URL and extract text from PDF/DOCX. Building blocks not yet exposed via an endpoint.

---

## Request lifecycle

Every request passes through the middleware and shared error handling before reaching a route:

| Step | Component                     | Scope                     | Purpose                                                                         |
| :--: | ----------------------------- | ------------------------- | ------------------------------------------------------------------------------- |
| 1    | `require_request_uuid`        | all routes except `/lynq-ml/health` | 403 if `lynq-request-uuid` is missing/blank; bind it to the logging context (MDC). |
| 2    | Route handler                 | matched route             | Validates headers/body via Pydantic; runs the feature logic.                    |
| 3    | Exception handlers            | all routes                | Map failures to the standard error envelope (see below).                        |

**Error envelope** — all failures return `ErrorRestResponse`:

| Situation                                   | Status | Body                                                              |
| ------------------------------------------- | :----: | ----------------------------------------------------------------- |
| Missing `lynq-request-uuid` header          | 403    | `{ success:false, reason:"Missing required header: lynq-request-uuid" }` |
| Invalid/malformed request fields            | 400    | `{ success:false, data:{<field>:<msg>}, reason:"Invalid Fields Found" }` |
| Raised `HTTPException` (e.g. LLM failure)    | 4xx/5xx | `{ success:false, reason:"<detail>" }`                            |
| Unhandled exception                         | 500    | `{ success:false, reason:"<message>" }`                           |

---

## Skill extraction flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant M as require_request_uuid
    participant R as skill_enhance router
    participant F as get_llm_client
    participant P as render_key_extractor_prompt
    participant L as LLM (Ollama / OpenAI)

    C->>M: POST /lynq-ml/skill-enhance<br/>headers: lynq-request-uuid, user-id, company-id<br/>body: {title, description, work_type}
    M->>R: forward (request UUID bound to logging context)
    R->>F: get_llm_client()  (reads LLM_PROVIDER)
    F-->>R: LLMClient (Ollama or OpenAI)
    R->>P: render prompt for client.provider
    P-->>R: rendered prompt
    R->>L: client.generate(prompt)
    alt transport error
        L-->>R: httpx.HTTPError
        R-->>C: 502 "LLM request failed: ..."
    else response returned
        L-->>R: raw completion (JSON)
        R->>R: parse {"skills": [...]}, validate list of strings
        alt malformed output
            R-->>C: 502 "LLM returned malformed output"
        else valid
            R-->>C: 200 { success:true, data:{ skills:[...] } }
        end
    end
```

---

## API reference

Base path: `/lynq-ml`. All routes require the `lynq-request-uuid` header **except** `/lynq-ml/health`.

| Method | Path                   | Extra headers required          | Description                                            |
| ------ | ---------------------- | ------------------------------- | ------------------------------------------------------ |
| POST   | `/skill-enhance`       | `user-id`, `company-id`         | Extract 5–15 key technical skills from a job posting.  |
| GET    | `/health`              | —                               | Liveness/readiness probe; reports service + LLM status.|

**`POST /skill-enhance`** request body:

```json
{
  "title": "Senior Backend Java Developer",
  "description": "Building scalable services with Java, Spring and AWS.",
  "work_type": "REMOTE"
}
```

`work_type` is an enum: `REMOTE` or `IN_OFFICE`. The response is wrapped in `GlobalRestResponse<SkillEnhanceResponse>`:

```json
{ "success": true, "data": { "skills": ["Java", "Spring", "AWS", "REST", "Docker"] } }
```

**`GET /health`** returns `200` when the configured LLM is reachable, `503` otherwise (this route is *not* wrapped in `GlobalRestResponse`):

```json
{ "status": "UP", "llm": { "provider": "ollama", "status": "UP" } }
```

---

## Sample requests

> Substitute `$UUID` with any UUID you generate per request (e.g. `uuidgen`).

**Extract skills**

```bash
curl -X POST http://localhost:8084/lynq-ml/skill-enhance \
  -H "Content-Type: application/json" \
  -H "lynq-request-uuid: $UUID" \
  -H "user-id: user-1" \
  -H "company-id: company-1" \
  -d '{
    "title": "Senior Backend Java Developer",
    "description": "Building scalable services with Java, Spring and AWS.",
    "work_type": "REMOTE"
  }'
```

**Health check**

```bash
curl http://localhost:8084/lynq-ml/health
```

---

## Running locally

**Prerequisites**

- Python 3.12
- A reachable LLM backend — either a local Ollama server (default) or an OpenAI API key.

**Steps**

```bash
# 1. Create the virtualenv and install dependencies
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 2. Start an LLM backend. Easiest: the Ollama services from the repo-root compose file,
#    which expose Ollama on localhost:11434 and pull llama3.1:
docker compose -f ../docker-compose.yaml up -d ollama ollama-pull

# 3. Export the service environment (defaults to Ollama on localhost)
source ./set_env.sh

# 4. Run the service
python main.py
```

Service URL: `http://localhost:8084/lynq-ml` (health at `/lynq-ml/health`).

To use OpenAI instead of a local model:

```bash
LLM_PROVIDER=openai OPENAI_API_KEY=sk-... source ./set_env.sh
python main.py
```

---

## Running with Docker

The repo-root `docker-compose.yaml` provisions `lynq-ml` together with an Ollama server and a one-shot job that pulls the default model. Run compose from the repository root (one level up from this module):

```bash
cd ..
docker compose up --build lynq-ml ollama ollama-pull
```

In compose, `lynq-ml` reaches Ollama on the compose network at `http://ollama:11434` and waits for the model pull to finish (healthchecks gate startup). The service is published on `http://localhost:8084/lynq-ml`.

To bring up the whole platform instead:

```bash
docker compose up --build
```

---

## Configuration

All configuration is via environment variables (see `set_env.sh` for defaults):

| Variable          | Default                     | Used when            | Purpose                                              |
| ----------------- | --------------------------- | -------------------- | ---------------------------------------------------- |
| `LLM_PROVIDER`    | `ollama`                    | always               | Selects the LLM backend: `ollama` or `openai`.       |
| `LLM_TIMEOUT`     | `60`                        | always               | LLM request timeout, in seconds.                     |
| `OLLAMA_BASE_URL` | `http://localhost:11434`    | `LLM_PROVIDER=ollama`| Ollama server base URL.                              |
| `OLLAMA_MODEL`    | `llama3.1`                  | `LLM_PROVIDER=ollama`| Ollama model name.                                   |
| `OPENAI_API_KEY`  | — (required)                | `LLM_PROVIDER=openai`| OpenAI API key.                                      |
| `OPENAI_MODEL`    | `gpt-4o-mini`               | `LLM_PROVIDER=openai`| OpenAI model name.                                   |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | `LLM_PROVIDER=openai`| OpenAI-compatible API base URL.                      |
| `HOST`            | `0.0.0.0`                   | always               | Bind host (`python main.py`).                        |
| `PORT`            | `8084`                      | always               | Bind port.                                           |
| `LOG_LEVEL`       | `INFO`                      | always               | Root log level.                                      |
| `LOG_COLORS`      | `true`                      | always               | Colourised per-level logs; set `false` for files/CI. |

---

## Observability

- **Logs** — stdlib `logging` configured in `main.py` with Uvicorn's colourising formatters, streamed to stdout. Every log line is prefixed with the request UUID (`[<lynq-request-uuid>]`) via a `contextvars`-backed MDC filter (`logging_context.py`), mirroring the `%X{requestId}` pattern used by the Java services. Logs emitted outside a request show `[-]`.
- **Health** — `GET /lynq-ml/health` reports the service status and whether the configured LLM backend is reachable (`200 UP` / `503 DOWN`). It is exempt from the request-UUID header so infra probes can call it directly.

---

## Testing

Tests use the standard-library `unittest` framework with FastAPI's `TestClient`; LLM and HTTP collaborators are mocked, so no network or model is required. Run from the module root:

```bash
python -m unittest discover
```

Coverage includes the `skill-enhance` and `health` endpoints, the request-UUID middleware and logging context, the prompt renderer, the `get_llm_client` factory, and the Ollama/OpenAI client HTTP behaviour.

---

## Project layout

```
lynq-ml/
├── main.py                     # FastAPI app, middleware, exception handlers, logging
├── logging_context.py          # Request-UUID contextvar + logging filter (MDC)
├── middleware/
│   └── request_uuid.py         # require_request_uuid middleware
├── skill_enhance/
│   ├── router.py               # POST /skill-enhance
│   ├── models.py               # SkillEnhanceRequest/Response, WorkType enum
│   └── prompts.py              # render_key_extractor_prompt (Jinja)
├── llm_client/
│   ├── base.py                 # LLMClient interface, LLMProvider enum
│   ├── ollama_client.py        # Ollama implementation
│   ├── openai_client.py        # OpenAI implementation
│   └── __init__.py             # get_llm_client() factory
├── prompts/
│   └── skill_extractor/        # ollama.jinja, openai.jinja
├── response/
│   └── models.py               # GlobalRestResponse, ErrorRestResponse
├── file_downloader/            # presigned-URL download helper
├── file_reader/                # resume text extraction (PDF/DOCX)
├── tests/                      # unittest suite
├── set_env.sh                  # environment defaults
├── Dockerfile
└── requirements.txt
```
