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
| `POST` | `/lynq-agent/dmz/conversation` | Opens a conversation over a job posting and a base resume, and answers the greeting |
| `POST` | `/lynq-agent/dmz/conversation/{id}/turn` | One exchange: the candidate's message in, the tailored resume out |
| `GET` | `/lynq-agent/dmz/conversation/{id}` | The thread, the current resume and the list of versions |
| `PATCH` | `/lynq-agent/dmz/conversation/{id}/applied` | Closes the conversation once `lynq-bff` applied with one of its resumes |

Creating a conversation calls lynq-ml's `POST /dmz/skill-enhance` once and freezes the
answer into `job_snapshot.extractedSkills`; if lynq-ml is unavailable the skills the
posting already declares are used instead, and the conversation still opens.

A 409 carries a `code` in the envelope so the front can tell the three cases apart:
`TURN_IN_PROGRESS` (spin and retry), `CONVERSATION_EXHAUSTED` (hide the input, keep the
apply button) and `ALREADY_APPLIED` (the `PATCH` arrived with a second resume). A 502
means the turn itself failed — it does not cost the candidate a turn.

> **The agent does not talk to a language model yet.** The loop in `src/agent/graph.py`
> returns the resume untouched with a fixed reply, and writes its spans like the real
> one will. Everything around it — the two transactions, the run token, the idempotency
> by `turn_key`, the rescue of a stuck `RUNNING` and the `max_turns` rejection — is the
> definitive implementation. Stage 3 replaces `run_turn` and nothing else.

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

### A turn, transaction by transaction

A turn is **two** transactions with the expensive work outside both, so a second click
gets a 409 instead of blocking on the row lock for as long as the loop runs:

1. **Claim.** `SELECT ... FOR UPDATE`; a `RUNNING` newer than `AGENT_TURN_TIMEOUT` is a
   turn in flight (409), an older one is a dead process and gets taken over. The
   conversation flips to `RUNNING` with a fresh `run_token`, the user message is
   inserted, commit.
2. **The loop**, with no transaction open, under `AGENT_TURN_TIMEOUT - 30s`, so it dies
   before anyone else can declare it dead.
3. **Persist.** `SELECT ... FOR UPDATE` again and compare the `run_token`: if it
   changed, this process is a zombie that another turn already superseded — its orphan
   user message is deleted, a `stale_run` span is written and it answers 502 without
   persisting a thing. Otherwise the spans, the new `resume_version` (with the
   `is_current` flip), the assistant message and the cost rollup all land together.

`turn_key` makes a retry safe: with its assistant message already stored the previous
answer is replayed, and with only the user message stored — the orphan of a process
that died between the two transactions — that row is reused and the turn runs again.

The rescue is worth exercising by hand once: take a turn, kill the process between the
two transactions, and check that the conversation is stuck in `RUNNING` until
`AGENT_TURN_TIMEOUT` passes and the next turn takes it over.

`docs/queries.sql` has the queries for reading a conversation and its trace.

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
| `AGENT_MAX_TURNS` | `10` | Exchanges a conversation accepts; copied onto the row at creation |
| `AGENT_MAX_STEPS` | `12` | Steps the loop may take in one turn; copied onto the row at creation |
| `AGENT_TURN_TIMEOUT` | `600` | Seconds before a `RUNNING` turn is treated as a dead process. Operational, never copied onto the row |
| `AGENT_JOB_DESCRIPTION_MAX_CHARS` | `6000` | The posting is truncated to this before it is frozen into the snapshot |
| `AGENT_INPUT_PRICE_PER_1M` | `0` | USD per million prompt tokens, frozen onto the conversation. Forced to 0 on `ollama` |
| `AGENT_OUTPUT_PRICE_PER_1M` | `0` | USD per million completion tokens, same treatment |
| `LYNQ_ML_URL` | `http://localhost:8084/lynq-ml` | Where the skill extraction of the posting is asked for |
| `ML_TIMEOUT` | `300` | Seconds allowed for the lynq-ml call |
| `LYNQ_AGENT_SYSTEM_USER_ID` | — | The `user-id` the agent presents to lynq-ml; the caller's own is forwarded when empty |
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
