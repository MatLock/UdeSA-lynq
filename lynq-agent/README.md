# lynq-agent

CV Tailor: the conversational agent that adapts a candidate's resume to a concrete
job posting. FastAPI + LangChain (ReAct loop), MySQL for the conversation and its
traces.

The service listens on **8090** (8089 belongs to `lynq-feeders`) and exposes two
prefixes: `/lynq-agent` for the health probe and `/lynq-agent/dmz` for the
conversation API, which is reachable only from `lynq-bff`.

## Endpoints

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/lynq-agent/health` | Liveness/readiness probe. Exempt from the request-uuid header |

The conversation API (`POST /conversation`, `POST /conversation/{id}/turn`,
`GET /conversation/{id}`, `PATCH /conversation/{id}/applied`) is not implemented yet.

## The database

`lynq_agent_db` holds four tables — `conversation`, `message`, `resume_version` and
`trace_span`. The conversation freezes the job posting, the base resume and the token
prices in force when it was created: that is what makes a trace reproducible and what
keeps an old conversation costing what it cost.

Migrations are **Liquibase** changesets under `changelog/ddl`, applied on startup when
`DB_MIGRATE_ON_STARTUP` is true (`src/db/migrations.py` turns `DB_URL` into a JDBC URL
and shells out to the Liquibase bundled in the image). The tables are deliberately not
schema-qualified, so `DB_URL` stays the single source of truth for which database the
service reads and migrates.

### Housekeeping

An `asyncio` task started in the same lifespan runs every
`AGENT_HOUSEKEEPING_INTERVAL` seconds and does three things:

1. Marks `ABANDONED` every `AWAITING_CONFIRMATION` or `ACTIVE` conversation untouched
   for `AGENT_ABANDON_AFTER_DAYS`. Nothing else ever sets that status.
2. Nulls `trace_span.input` and `trace_span.output` on conversations closed more than
   `AGENT_TRACE_TTL_DAYS` ago. Tokens, cost and latency stay, so the cost analytics
   survive the purge; the repeated resume does not.
3. Deletes conversations older than `AGENT_CONVERSATION_TTL_DAYS`, children first.

`scripts/run_housekeeping.py` runs one pass by hand — drop the three TTLs to 0 and it
empties the database.

> **`scripts/export_eval_corpus.py` has to run before the long TTL wipes the
> conversations.** It writes the (job posting, base resume, tailored resume) triples
> to JSONL for the thesis corpus, without `personal_info` and with the candidate id
> hashed under `EVAL_CORPUS_SALT`. The export is the copy that outlives the TTL, not
> the database.
>
> ```bash
> EVAL_CORPUS_SALT=<salt> python scripts/export_eval_corpus.py corpus.jsonl --only-applied
> ```

Every route but the health probe requires the `lynq-request-uuid` and `user-id`
headers, and answers with the platform-wide `GlobalRestResponse` envelope.

## Running it

```bash
source ./set_env.sh
python src/main.py
```

or, from the repository root:

```bash
docker compose up lynq-agent
curl -s http://localhost:8090/lynq-agent/health
```

## Tests

```bash
pip install aiosqlite
coverage run -m unittest discover -s tests -t .
coverage report -m
```

`aiosqlite` is test-only: the suite runs against a throwaway SQLite file, so it never
needs a MySQL container.

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8090` | HTTP port |
| `DB_URL` | `mysql+aiomysql://root:root@localhost:3306/lynq_agent_db` | SQLAlchemy URL; Liquibase migrates whatever database it names |
| `DB_ECHO` | `false` | Log every statement |
| `DB_MIGRATE_ON_STARTUP` | `true` | Run `liquibase update` in the lifespan |
| `AGENT_HOUSEKEEPING_ENABLED` | `true` | Start the housekeeping task |
| `AGENT_HOUSEKEEPING_INTERVAL` | `3600` | Seconds between housekeeping passes |
| `AGENT_ABANDON_AFTER_DAYS` | `7` | Idle days before an open conversation is `ABANDONED` |
| `AGENT_TRACE_TTL_DAYS` | `30` | Days before the spans of a closed conversation lose their payload |
| `AGENT_CONVERSATION_TTL_DAYS` | `180` | Days before a conversation is deleted outright |
| `LLM_PROVIDER` | `ollama` | `ollama` for local development, `bedrock` in the cloud |
| `LLM_TIMEOUT` | `300` | Seconds allowed for a single LLM call |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama endpoint |
| `OLLAMA_MODEL` | `qwen2.5:7b` | Model pulled into Ollama |
| `BEDROCK_MODEL_ID` | — | Any model id the Converse API accepts, e.g. `amazon.nova-pro-v1:0` |
| `BEDROCK_REGION` | `us-east-1` | Bedrock region |
| `BEDROCK_MAX_TOKENS` | `4096` | Cap on a single completion |
| `BEDROCK_TEMPERATURE` | `0` | Sampling temperature |

## Dependencies

`requirements.txt` is a full lock generated from `requirements.in`:

```bash
pip-compile --generate-hashes requirements.in
```
