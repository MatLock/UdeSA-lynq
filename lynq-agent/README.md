# lynq-agent

CV Tailor: a conversational agent that adapts a candidate's resume to one job
posting, without inventing anything.

It is a FastAPI service running a ReAct loop (`langchain.agents.create_agent`)
over three tools, with the conversation, every resume version and the whole trace
persisted in MySQL.

```
lynq-bff ──► lynq-agent ──► MySQL (lynq_agent_db)   ← conversation + trace
                  │
                  ├──► Bedrock (Nova Pro) / Ollama (local dev)
                  └──► lynq-ml (/dmz/skill-enhance)
```

## What it guarantees

The agent may reorder, prioritise, rewrite prose and adopt the posting's
vocabulary. It may not invent. That is enforced **in code**, inside `apply_edit`
— not asked for in the prompt:

- `personal_info` is immutable. It is not even in the model's context: the code
  keeps it aside and re-attaches it when the resume is serialised. That removes
  a bias channel by construction rather than promising it away.
- No new entries in `work_experience` or `education`, and no changes to a
  company, a position, an institution, a degree or any date.
- A skill can only be added when the base resume supports it: it is already in
  the skills list, or `find_evidence` locates it in the prose. Rescuing a skill
  the prose mentions and the skills list forgot is the most valuable thing the
  agent does.
- The posting's spelling of a technology the candidate already has (`Postgres` →
  `PostgreSQL`) is added alongside, never instead. Which names are the same
  technology is decided by `agent/skill_aliases.py`, a curated table — not by
  the model. `React` does not authorise `React Native`.
- A rejection returns to the model as an observation, so it can correct itself
  within the same turn, and it reaches the candidate as a warning.

`find_evidence` is a lexical search in code (lowercased, accent-folded, expanded
through the alias table), never the model judging itself — otherwise the
evidence chain the thesis evaluation rests on would be circular.

## The API

Prefix `/lynq-agent/dmz`, `GlobalRestResponse` envelope, `lynq-request-uuid` and
`user-id` headers required (same as lynq-ml).

| Method | Route | What it does |
|---|---|---|
| `POST` | `/conversation` | Freezes the posting and the base resume, returns `conversationId` and a templated greeting |
| `POST` | `/conversation/{id}/turn` | One turn: runs the loop, applies the edits, returns the current resume |
| `GET` | `/conversation/{id}` | Status, thread, current resume and versions — for resuming the modal |
| `PATCH` | `/conversation/{id}/applied` | Closes the conversation once the candidate applied, storing `scoreAfter` |

`GET /lynq-agent/health` is exempt from the request-uuid header.

Errors: `403` missing header or someone else's conversation, `404` unknown
conversation, `409` a turn is already running or the conversation is exhausted,
`502` the LLM failed.

### The turn runs in two transactions

The `RUNNING` flag is committed **before** the LLM is called, and the loop runs
with no transaction open. With a single transaction the flag would be invisible
until commit, so a second click would not get a `409` — it would block on the
row lock for as long as the loop takes.

If the process dies mid-loop the conversation would stay `RUNNING` forever, so a
`RUNNING` row older than `AGENT_TURN_TIMEOUT` is treated as a dead process and
taken over. When a turn fails, the user's message is deleted, an `error` span is
written and the status goes back to `ACTIVE`: the candidate can retry with the
same `turnKey` and it does not cost them a turn.

## The two limits

`AGENT_MAX_STEPS` (default 12) bounds one turn's loop. The soft cap lives in the
tools: once it is reached they answer "stop editing and reply", so the candidate
gets a valid, partial answer instead of an error. The LangGraph recursion limit is
only the backstop, and it has to sit **above** the soft cap or it turns that
partial answer into a 502 — a ReAct cycle costs two graph steps, not one, so it is
`2 * max_steps + 6`. When the soft cap bites, a `trace_span` with `kind='limit'`
records it — otherwise a trace read three weeks later looks like the agent stopped
for no reason.

`AGENT_MAX_TURNS` (default 10) bounds the conversation, validated before a token
is spent. `EXHAUSTED` loses nothing: the current resume is still applicable.

Both are **copied onto the conversation row when it is created**, so changing
the variable never alters a conversation already under way, and the trace can
say which limits each one ran with. `AGENT_TURN_TIMEOUT` is operational instead
and is read live.

## Cost

Every `llm` span stores its tokens (from `usage_metadata`, never estimated) and
its cost; the conversation carries a denormalised rollup. The rates are frozen
onto the conversation row at creation time, in `DECIMAL`: tokens are the
immutable fact and the cost is an interpretation, so it stays recomputable when
prices change. A model missing from the table bills zero with a WARN — better a
visible zero than a turn lost to a price list.

In local dev `llm_provider='ollama'` bills zero. Filter by provider in analytics
or the averages come out doped.

`docs/queries.sql` has the queries to read all of this.

## Running it

```bash
source ./set_env.sh
python src/main.py
```

Or `docker compose up lynq-agent` from the repo root. The schema is migrated on
startup (`DB_MIGRATE_ON_STARTUP`, Liquibase); `liquibase update` is idempotent, so
several replicas booting at once is safe.

The changelog lives in `changelog/`, laid out like the one in lynq-app-backend: a
master `db.changelog-config.xml` that `includeAll`s `changelog/ddl`, and one
formatted-SQL file per change. A new change is a new numbered file in `ddl`;
never edit a file that has already run, because Liquibase checksums it.

Liquibase is a Java tool, so the image carries a headless JRE on top of
`python:3.12-slim` and the Liquibase distribution under `/opt/liquibase`, pinned
by version and sha256 in the Dockerfile. The distribution ships no MySQL driver,
so `mysql-connector-j` is fetched into `/opt/liquibase/lib` in the same step. To
run the service outside Docker you need `java` plus either `LIQUIBASE_HOME` or
`liquibase` on the `PATH`; `DB_MIGRATE_ON_STARTUP=false` skips the whole thing.

Credentials reach Liquibase through `LIQUIBASE_COMMAND_USERNAME` and
`LIQUIBASE_COMMAND_PASSWORD`, never as arguments, so the password stays out of
the process list. `DB_URL` stays the single source of truth: the JDBC URL is
derived from it, schema name included, so pointing the service at another
database moves the migration with it.

Tests:

```bash
pip install aiosqlite
python -m unittest discover -s tests -t .
```

They run on SQLite, so no database is needed (`aiosqlite` is test-only and is
therefore not in `requirements.in`). Two MySQL-only details do not
show up there: `short_id` is `AUTO_INCREMENT` in the changelog and stays `NULL`
on SQLite, and `DECIMAL` is exact in MySQL while SQLite goes through float.

## Thesis

`scripts/export_eval_corpus.py` exports the (posting, base resume, tailored
resume) triples as JSONL for the evaluation corpus, dropping `personal_info` and
hashing the candidate id with a salt.

`score_before` and `score_after` are both computed from the **resume's own**
skills — base for the first, tailored for the second — and never from the
candidate profile, which merges every resume the candidate ever uploaded. Against
that superset a perfect tailoring could come out lower, and the delta would mean
nothing.
