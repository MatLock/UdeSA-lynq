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

The greeting it answers with costs nothing: it is a Jinja template per language,
`resources/greetings/{en,es}.jinja`, chosen by the conversation's `language` and falling
back to English when the locale has no template of its own. It is the one place in the
module where Spanish is allowed, because it is the only text the candidate reads that
the model did not write.

The greeting is stored as the conversation's first message so the front can render it,
but it is never replayed to the model: a turn's history is trimmed to start on a user
message. Bedrock's Converse API rejects a conversation that opens on an assistant turn
(`ValidationException: A conversation must start with a user message`), which hits both
the first turn — where the greeting is the whole history — and any later turn whose
history window happens to open on a reply. Nothing is lost by dropping it, because the
greeting is rendered from the job snapshot the system prompt already carries.

A 409 carries a `code` in the envelope so the front can tell the three cases apart:
`TURN_IN_PROGRESS` (spin and retry), `CONVERSATION_EXHAUSTED` (hide the input, keep the
apply button) and `ALREADY_APPLIED` (the `PATCH` arrived with a second resume). A 502
means the turn itself failed — it does not cost the candidate a turn.

## The turn, inside

A turn opens with the **intent step**, in `src/agent/intent.py`: one cheap model call
that reads the message of the candidate together with the last four messages of the
exchange and answers a single word, `edit` or `advise`. It exists because the agent used
to treat every message as a request to change the resume — asked *what else do you
suggest?*, it reordered the experience, rewrote the summary again and reported the work
as done. An `advise` turn is built with `find_evidence` as its only tool, so the edit
tools are not merely discouraged, they are not there; the resume cannot change, and the
prompt tells the model to answer and then recommend rather than to apply. The call is
the first thing the turn does, it does not spend a step of `AGENT_MAX_STEPS`, and it
leaves a `step=0` span named `intent` whose output is the word that was read. Anything
that goes wrong with it — a model that breaks, an answer that says neither word — falls
back to `edit` and writes a `kind='error'` span: the classifier never takes a turn down,
and the product keeps doing what it is for. `docs/queries.sql` reads that span back, and
a `resume_version` written on an `advise` turn would be a bug.

`src/agent/graph.py` then builds a ReAct loop with `langchain.agents.create_agent` over
its tools, a system prompt rendered from `resources/prompts/resume_tailor/{bedrock,ollama}.jinja`
and `response_format=TurnAnswer`, which LangChain binds as one more tool with
`tool_choice="any"`, so the turn ends with the model calling it. When a model breaks
that format — Ollama does, now and then — the JSON is unwrapped from the text before the
candidate sees it, and plain prose becomes the reply as it is.

| Tool | What it does |
| --- | --- |
| `find_evidence(claims)` | Lexical search **in code** over the base resume, never the model judging itself: normalised (lowercase, no accents, no symbols), by prefix for claims of four characters or more and by exact word for the short ones (`Go`, `C#`, `AWS`). It takes up to 20 claims and answers one `{claim, hits}` per claim, each hit a `{path, matched}` with the wording the resume already uses, and it never looks at `personal_info`. The batch is deliberate: a call costs one step whatever it carries, so a turn that looked up eight skills one at a time used to burn eight of its `AGENT_MAX_STEPS` before editing anything |
| `rewrite_summary(text)` | Replaces the summary |
| `rewrite_entry(section, index, description, achievements)` | Rewrites the prose of one entry of the experience, the education or the projects |
| `reorder_entries(section, order)` | Reorders a section without adding or dropping anything. An order that is already the one the section has answers `OK` and records nothing: a turn that "reordered" `[0, 1, 2, 3, 4, 5]` used to show up as a change that moved no line |
| `replace_skills(technical, tools, soft)` | Replaces the skill buckets it is given, and keeps the ones it is not |

The four edit tools are the only way the resume changes, and an `advise` turn is not
given them at all. Each answers `OK` or
`REJECTED: <reason>`, and the reasons are literal and stable because `docs/queries.sql`
groups by them. They are four rather than one `apply_edit` carrying a free-form payload
on purpose: Bedrock validates a tool call against the schema it was given, and an
argument list of strings, integers and lists of strings is something a model can fill
without inventing structure. The single tool took `payload: dict[str, Any]`, which
reaches Converse as an object with no properties; a model that guesses that shape wrong
takes the whole turn down with `ModelErrorException: Model produced invalid sequence as
part of ToolUse`.

Some guardrails are now the schema's job: there is no argument for a date, for a new
entry or for an operation, so none of those can even be asked for. The rest live in the
tools, in code, not in the prompt: `personal_info is immutable`, `index out of range`,
`field is not editable` — `achievements` belong to `work_experience` alone — `no
evidence in base resume`, since a skill only enters if `find_evidence` backs it and it
enters with the wording of the resume, so a posting asking for `PostgreSQL` over a resume
saying `Postgres` adds `Postgres`, and `payload language (es) does not match resume
language (en)`, a `langdetect` check bounded to prose longer than 80 characters once the
skill names are taken out.

**The model never sees `personal_info`.** The code splits it off before rendering the
prompt and pins it back when each version is serialized (§13.2 of the plan: it is the
bias channel that gets closed by construction, not by asking the model nicely).

The answer's `resume` comes from what the edit tools left behind, never from the text of
the model, which only contributes `reply` and `warnings`.

`AGENT_MAX_STEPS` is a **soft** cap: `agent/callbacks.py` counts the steps, and once the
budget is spent the tools answer `STEP LIMIT REACHED ...` and a `kind='limit'` span is
written, so the candidate gets a partial but valid answer instead of a 502. The hard cap
is LangGraph's `recursion_limit = 2 * max_steps + 6` — a ReAct cycle costs two graph
steps, so anything tighter makes the hard cap fire first.

`AGENT_MAX_EDITS` is the other soft cap, and it counts edits rather than steps: once a
turn has applied that many, the edit tools answer `EDIT LIMIT REACHED ...` and leave their
own `kind='limit'` span. Two edits a turn keep each exchange reviewable, and the prompt
has every reply close with the edits the agent would make next — the ones the budget left
out and whatever else the posting still asks for — so the candidate decides what happens
in the following turn. The last exchange is the exception: there is nothing left to
recommend for, so it closes by suggesting the candidate applies with the resume as it is.

Bedrock answers `ModelErrorException: Model produced invalid sequence as part of ToolUse`
when the model emits a tool call it cannot parse — an ambiguous tool schema, a generation
cut short, or plain bad luck. It is intermittent, so `ModelRetryMiddleware` retries the
model call `AGENT_MODEL_RETRIES` times with backoff before the turn is given up on; the
tools are not re-run, only the call that broke. `llm/errors.py` decides what is worth
retrying by the AWS error code, so an `AccessDenied` still fails at once. Every attempt
costs a step and leaves its own span, so a retried turn shows the failure and the recovery
in its trace.

The tokens of each call come from the `usage_metadata` of the `AIMessage`, never
estimated, and the tariff of the model lives in `src/llm/pricing.py` and is frozen onto
the conversation when it is created; a model that is not in the sheet costs zero and
logs a warning. The LLM span does **not** store the system prompt: it stores the
messages of the turn, the `resume_version_id` and the hash of the template
(`resume_tailor/bedrock@<hash>`), which is enough to rebuild it exactly.

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
2. **The intent step and the loop**, with no transaction open, under
   `AGENT_TURN_TIMEOUT - 30s`, so they die before anyone else can declare them dead.
3. **Persist.** `SELECT ... FOR UPDATE` again and compare the `run_token`: if it
   changed, this process is a zombie that another turn already superseded — its orphan
   user message is deleted, a `stale_run` span is written and it answers 502 without
   persisting a thing. Otherwise the spans, the assistant message and the cost rollup
   all land together, and so does a new `resume_version` — but **only when the turn
   applied at least one edit**. A turn that answered a question without touching the
   resume writes no version and answers with the one that already stands, which is `0`
   while the base resume is still the current one.

`turn_key` makes a retry safe: with its assistant message already stored the previous
answer is replayed — down to the intent, which is read back off the `intent` span of the
user message rather than guessed — and with only the user message stored — the orphan of a process
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

Two tests talk to a real model and are skipped unless `AGENT_LIVE_LLM=true`:

```bash
AGENT_LIVE_LLM=true LLM_PROVIDER=ollama python -m unittest tests.test_structured_output
AGENT_LIVE_LLM=true LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 \
  python -m unittest tests.test_structured_output
```

`tests/test_structured_output.py` is the one bet of the plan: that Nova Pro honours
`tool_choice="any"` and closes the turn with the `TurnAnswer` tool call. Only the
Bedrock run proves it — Ollama breaks the tool call format often enough that the test
is skipped there on purpose, and the loop leans on the JSON fallback instead. If the
Bedrock run fails, the fallback is already decided: drop `response_format`, ask for the
JSON in the prompt and validate it with Pydantic plus one retry.

Running the checkpoint turn of the prompt — a candidate asking for a skill the resume
does not back — against the local models (`qwen2.5:7b`, `llama3.1`) shows the split
clearly: the guardrails hold every time, the skill never enters the resume and
`personal_info` is untouched, but neither model closes the turn with the `TurnAnswer`
tool call and the reply they write is rough. That is the dev-only trade of the plan, not
something to fix in the prompt: the loop is aimed at Nova Pro.

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
| `AGENT_MAX_EDITS` | `2` | Edits one turn may apply before the edit tools answer `EDIT LIMIT REACHED`; read live from the settings, never copied onto the row |
| `AGENT_MODEL_RETRIES` | `2` | Times a model call is retried when Bedrock rejects what the model emitted; each attempt costs a step |
| `AGENT_TURN_TIMEOUT` | `600` | Seconds before a `RUNNING` turn is treated as a dead process. Operational, never copied onto the row |
| `AGENT_JOB_DESCRIPTION_MAX_CHARS` | `6000` | The posting is truncated to this before it is frozen into the snapshot |
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
| `BEDROCK_GUARDRAIL_ID` | _(empty)_ | Bedrock guardrail applied to every call; empty means none |
| `BEDROCK_GUARDRAIL_VERSION` | `DRAFT` | Version of that guardrail |
| `AGENT_LIVE_LLM` | `false` | Tests only: `true` runs the turns that need a real model |

Token prices are not an environment variable: they live in `src/llm/pricing.py` per
model and are copied onto the conversation when it is created, so changing the sheet
never rewrites what an old conversation cost.

## Dependencies

`requirements.txt` is a full lock generated from `requirements.in`:

```bash
pip-compile --generate-hashes requirements.in
```
