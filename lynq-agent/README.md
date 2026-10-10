# lynq-agent

CV Tailor: the conversational agent that adapts a candidate's resume to a concrete
job posting. FastAPI, a LangGraph of four agents — intent, advise, edit, judge — where the
code decides only where a change goes and the judge decides whether it may, and MySQL
for the conversation and its traces.

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

Creating a conversation calls lynq-llm's `POST /dmz/skill-enhance` once and freezes the
answer into `job_snapshot.extractedSkills`; if lynq-llm is unavailable the skills the
posting already declares are used instead, and the conversation still opens.

The greeting it answers with costs nothing: it is a Jinja template per language,
`resources/greetings/{en,es}.jinja`, chosen by the conversation's `language` and falling
back to English when the locale has no template of its own. It is the one place in the
module where Spanish is allowed, because it is the only text the candidate reads that
the model did not write. It already asks the first question of the conversation: the
skills the posting asks for that the resume names nowhere — the gaps, computed in code
by `apply.gaps_in` from `extractedSkills` against the base resume — are listed, four
at most, with a request to say which of them the candidate has, in which job and
doing what.

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

A turn is four agents, each with its own prompt and its own single answer, and no
agent holds a tool. They are the nodes of a LangGraph `StateGraph` in
`src/agent/graph.py`, together with the `apply` step; `src/agent/turn.py` builds the
state and runs it. The graph is fixed and acyclic but for one edge: a rejected proposal
goes back to the editor once.

```mermaid
---
config:
  flowchart:
    curve: linear
---
graph TD;
	__start__([<p>__start__</p>]):::first
	classify(classify)
	advise(advise)
	propose(propose)
	judge(judge)
	apply(apply)
	__end__([<p>__end__</p>]):::last
	__start__ --> classify;
	apply -.-> __end__;
	apply -.-> propose;
	classify -.-> advise;
	classify -.-> propose;
	judge --> apply;
	propose --> judge;
	advise --> __end__;
	classDef default fill:#f2f0ff,line-height:1.2
	classDef first fill-opacity:0
	classDef last fill:#bfb6fc
```


[`docs/un-turno-por-dentro.html`](docs/un-turno-por-dentro.html) walks through a
conversation step by step — the message, the node the turn is on, what the model proposed
and what was let in — with the trace spans each step leaves. Open it in a browser; it
is one file with no build. Turns 1 to 4 come from runs against `qwen2.5:7b` on
2026-09-28, made under the earlier design where a set of rules in code stood where the
judge stands now and the cap was two passes; the page shows the current graph and the
rejections with the judge's kinds, and says so. Turns 5 and 6 are not a run: they walk
the gap flow of 2026-10-10 — the greeting that asks, the yes that is an `edit`, the skill
that enters the bucket while the editor asks where, the bullet that enters the entry the
candidate named — with the model's answers written by hand to show the expected path. `TurnGraphState` is what flows between the nodes: the
context and the turn state the service built, the model handles, and what each node
leaves for the next — the intent, the thread, the proposal, the parts, the verdict and how
many passes have been made.

**1. The intent agent** (`src/agent/intent.py`, prompt `resources/prompts/intent/`)
reads the message of the candidate together with the last four messages of the exchange
and answers one word, `edit` or `advise`. It exists because an agent that treats every
message as a request to change the resume — asked *what else do you suggest?* — rewrites
things and reports the work as done. A yes to a skill CV Tailor asked about is `edit`,
because the skill was offered as an addition and the yes asks for it; a no, or a message
that only tells something about the candidate, is `advise`, so that it never ends in
the "nothing changed" notice an edit turn appends. It is the first thing the turn does and it leaves a
`step=0` span named `intent` whose output is the word that was read. Anything that goes
wrong with it — a model that breaks, an answer that says neither word — falls back to
`edit` and writes a `kind='error'` span: the classifier never takes a turn down. It
answers one word, so it may run on a cheaper model (`BEDROCK_INTENT_MODEL_ID`); its span
is priced with that model's own sheet, not with the rates frozen on the conversation.

**2. The advising agent** (`src/agent/advisor.py`, prompt `resources/prompts/advise.jinja`)
takes an `advise` turn. It answers the question and recommends the edits it would apply
next, as data in `recommendations` — `{id, section, entry, what}`, numbered by the code,
1..n, whatever the model wrote — and the code appends that numbered list to `reply`
under the answer, dropping any line the model already wrote for one of them, so the
chat, the stored message and the history of the next turn all carry the same list with
the same numbers; the model is told not to write it in `reply` itself. It is given no way to
change the resume: its answer schema has no field for one, so a `resume_version` written
on an `advise` turn would be a bug. The recommendations are stored on the assistant
message, and the next turn's editing agent reads them, so *do the second one* means what
the candidate read, not what the model reconstructs from the thread. It also carries
the gap question — see *What the candidate says is true* below — and records in
`confirmed` what the candidate told it about themselves.

**3. The editing agent** (`src/agent/editor.py`, prompt `resources/prompts/edit.jinja`)
takes an `edit` turn. It answers **once**, with the whole change of the turn as one
`EditProposal`: the new summary, the entries of the work experience it rewrites — each
named by `company` and `position`, never by index — and the skill buckets it replaces,
plus `reply`, `warnings` and `confirmed`. That is the entire surface: the summary, the
description and achievements of an experience entry, and the skills. Reordering, the
education, the projects, the certifications, a company, a position, a date — the schema
has no field for any of them, so they cannot be asked for, let alone done. A skill the
candidate confirmed enters the skills bucket in the same turn; it enters an entry of the
experience only once the candidate has said in which job they used it and what they did
with it, and until then the editor asks instead of guessing the entry.

**4. The judge** (`src/agent/judge.py`, prompt `resources/prompts/judge.jinja`) reads the
proposal part by part, each part beside the text it replaces in the **base** resume, and
says for each one whether the resume, or the candidate, supports it. It never rewrites:
`ok`, or a `kind` and a `reason` written in the candidate's language. The one rule it
applies is that a change may only say what the candidate's own resume already says, or
what the candidate themselves said in the conversation — where, in the order and in the
language the resume says it. Tailoring is a choice of words: the text after the change
carries the same facts, the same lines and the same skills as before, plus what the
resume or the candidate already said. The checks run in a fixed order and the first
failure names the kind:

| kind | what the judge saw |
| --- | --- |
| `language` | prose not in the language of the resume — a translation into the candidate's chat language included |
| `dropped_content` | fewer lines than the original, a line with no counterpart at its position, or two lines merged into one |
| `reordered` | the same lines, or the same skills, in another order; a new skill anywhere but at the end of its bucket |
| `dropped_skill` | a bucket that leaves out a skill the current one has: a bucket may be grown at its end, never shrunk |
| `invented` | a number, a duration, a result, a team size, a responsibility, a role or an employer that neither the resume states nor the candidate said — a requirement of the posting written in as if met counts |
| `unsupported_skill` | a technology that neither the resume nor the candidate names anywhere — and in an entry of the experience, one that entry does not name and the candidate did not place there: a technology never moves into a job that never used it |
| `wording` | the posting's spelling of a technology the resume spells otherwise (`PostgreSQL` over `Postgres`) |
| `padding` | much more text than the original, not a rephrasing |

Each part reaches the judge with its lines numbered and counted — one line for the role,
one per bullet, one per achievement, one per skill — because a model asked to notice
that eleven lines became nine does far better when it reads `lines="11"` and
`lines="9"` than when it has to count prose. The editing agent is held to the same
shape from its side: its prompt says a description is rewritten line for line, the
achievements item for item, a bucket in its order with new skills at the end, and
everything in the language of the resume whatever language the candidate writes in.

The judge has to **quote before it decides**: for every part it writes into `evidence`
the two line counts and then the words of the resume, or of the candidate, that back the
change — copied from the resume or from the candidate's statements, never from the
proposal, since Nova Pro was seen quoting the proposed sentence as its own evidence —
and it may only approve what it quoted. A
model that has to find the sentence is far less likely to say "the resume does not
mention Kubernetes" when the Acme entry lists it — which is exactly what `qwen2.5:7b` did
before this field existed. The quote stays in the trace, so a wrong verdict can be
checked against what the judge thought it saw.

The judge reads short texts and answers yes or no, so it runs on a cheaper model —
`BEDROCK_JUDGE_MODEL_ID`, Nova Lite by default in `set_env.sh` — and its spans are
priced with that model's sheet. A part the judge leaves out of its answer is **not**
approved (`unjudged`): the safe default costs a correction pass, never an invention.

**The `apply` step** (`src/agent/apply.py`) is the only code that touches the resume, and
it decides nothing about content. Before the judge, `plan` resolves each part to its
place — which entry a `company`/`position` names (a paraphrased position still finds its
entry by company when that is unambiguous), which bucket — and rejects on its own what
is mechanical to check: an entry the resume does not have (`unknown_entry`), and a part
whose **shape** changed. The shape of a text is its lines, its numbers and the
technologies it names; the shape of a bucket is its skills and their order; and the
rule is the same for both: everything the base resume has stays in its place, and
anything new goes after it and has to be found in the resume. A description, a summary
or an achievements list with fewer lines than the base, without one of its numbers, or
without a technology the base named — any name from the skill buckets or the
`technologies` of an entry — is `cut`; one with a line moved as it was is `moved`. A
bucket missing a skill is `cut`, one that moves a skill or inserts before the last
original one is `moved`, and one that appends a skill that neither the resume nor the
candidate names anywhere is `unbacked`. A name is found whatever way its spaces, dots
and dashes are drawn — `springboot` in the chat backs `Spring Boot` in the resume — and
a two-word name the candidate typed as one is matched as the pair. All of this happens
before the judge reads a word. These are the code's kinds, and `docs/queries.sql` groups them apart from the
judge's, so the trace says how often the guard fired and how often the judge did. The
achievements a model sends as one string with newlines in it are split back into one
item each before any of this, and an entry reaches the judge whole on both sides — a
field the proposal left alone is shown as the base has it, not as missing. A paraphrase
that drops or moves content without changing the counts, an added line that says what
the resume does not, still reach the judge, which is what the judge is for. After the
judge, `commit` writes the approved parts and records each as a change.

The parts are independent — a summary the judge rejects does not hold back skills it
approves — and a rejection goes back to the editor up to **twice** (`MAX_PASSES = 3`),
with its reason, as the next message of the same thread: the model re-proposes the
rejected parts with the reason in hand, and what is still rejected after the last pass
stays out — and reaches the candidate as a warning, in their language (`resources/rejections/`), because the reply is the model's
and the document is the judge's, and the chat must never promise what the resume beside it
does not say. Each pass leaves a `kind='tool'` span named `apply` whose input is the
parts and whose output is `OK` or the list of rejections with their kinds;
`docs/queries.sql` groups by kind. The `tool` kind is the trace's word for "a step the
code ran", kept from the loop design: no agent holds a tool, and nothing a model says can
call `apply`.

What this design gives up, and what it gives: the previous guard was a set of lexical
rules in code — digits, alias tables, prefix matches — that could be *proven* to stop an
invented number or technology, and could not see an invented responsibility at all. The
judge sees all of it, and none of it is provable: it is a model's reading, measured, not
guaranteed. The structure guard in `plan` is the one piece of that older idea kept on
purpose: that nothing is removed or reordered is a property of counts and positions,
provable in code, and Nova Pro was seen approving a bucket missing `Git` with the
original list quoted as its evidence. The trace keeps every verdict, so the thesis can report how often the judge
agreed with a person on a labelled sample, which is a result the rules could never give.

### What the candidate says is true

The candidate's own messages are evidence with the same weight as the resume. What
they say in the chat — that they have a skill, where they used it, what they did with
it — backs a change exactly as a line of the resume does, for the code and for the
judge. Three things make that work across a whole conversation:

- **Statements.** Every user message of the conversation, not just the four pairs the
  model reads, reaches the turn as `TurnContext.statements`, together with the facts
  recorded on `conversation.confirmed` (changelog `02-conversation-confirmed.sql`).
  `apply.plan` adds them to the backing of every part, and the judge gets them in a
  `<candidate_statements>` block.
- **Confirmed facts.** A yes to *do you have Spring Boot?* does not name Spring Boot,
  so a regex over the messages cannot back it. The editing and the advising agent
  therefore return `confirmed`: what the candidate stated this turn about themselves,
  in their words. The code treats it as backing in the same turn, the service appends
  it to `conversation.confirmed`, and every later turn reads it as the candidate's. The
  judge sees, in `<this_turn>`, what CV Tailor asked and what the candidate answered,
  so a yes can be checked against the question it answers. This is the one place where
  a model's reading of the conversation becomes evidence; it is logged in the trace like
  everything else.
- **Gaps.** `apply.gaps_in` lists the posting skills that neither the resume nor the
  statements name. The greeting asks about them from the first message, and both agents
  get them in a `<gaps>` block with the instruction to end the reply with one short
  question about the ones that matter most, three at most, and never to cover a gap in
  the resume. The check is lexical, so a posting that says `PostgreSQL` over a resume
  that says `Postgres` is listed; the prompts tell the model that another spelling of
  what the resume has is not a gap.

The flow the feature is built for: the greeting or a reply asks about a gap; the
candidate says yes, which the intent agent reads as `edit`; the editor adds the skill
to its bucket, records the confirmation, and asks in which job it was used and what
they did; the candidate answers; the editor adds a bullet to that entry, in the
resume's language and voice, backed by the candidate's words, and the judge approves
it against `<candidate_statements>`. What this gives up is that the judge no longer
guards against the candidate's own claims — only against the model's. That is by
design: the resume is theirs, and a candidate who wanted to claim a skill could always
have written it into the resume and uploaded that one instead. Guarding against the
candidate here would protect nothing; guarding against the model is what the judge is
for.

**The model never sees `personal_info`.** The code splits it off before rendering any
prompt and pins it back when each version is serialized (§13.2 of the plan: it is the
bias channel that gets closed by construction, not by asking the model nicely).

The answer's `resume` comes from what the judge let through, never from the text of the
model, which only contributes `reply` and `warnings`. An edit turn that applied nothing
gets a notice appended to its warnings (`resources/notices/`), so the candidate is told
rather than left to wonder.

Every agent answers through `with_structured_output`, which binds its schema as the only
tool the model may call. When a model breaks that protocol and answers in text — Ollama
does, now and then — the JSON is unwrapped from the text (`src/agent/answer.py`), and
plain prose becomes the reply as it is. Bedrock answers `ModelErrorException: Model
produced invalid sequence as part of ToolUse` when the model emits a tool call it cannot
parse; it is intermittent, so `src/agent/structured.py` retries the call
`AGENT_MODEL_RETRIES` times, and `llm/errors.py` decides what is worth retrying by the AWS
error code, so an `AccessDenied` still fails at once.

Why no tools and no ReAct loop: the previous design ran one over edit tools, and the
model decided when to stop, which entry an index pointed at after it had reordered the
section, and whether to look for evidence before adding a skill. Each of those was a way
for a turn to go wrong that no prompt closed. With the edit surface this small, the whole
change fits in one schema, the code resolves every reference and checks every part, and
a turn costs one or two model calls instead of up to twelve. LangGraph still runs the
turn, but every edge of its graph is decided by code — the only decision a model takes
is the intent — and its `recursion_limit` is a backstop the routes never reach, not a
budget the model spends.

The tokens of each call come from the `usage_metadata` of the `AIMessage`, never
estimated, and the tariff of the model lives in `src/llm/pricing.py` and is frozen onto
the conversation when it is created; a model that is not in the sheet costs zero and
logs a warning. The LLM span does **not** store the system prompt: it stores the
messages of the turn, the `resume_version_id` and the hash of the template
(`edit@<hash>`, `advise@<hash>`), which is enough to rebuild it exactly.

## The database

`lynq_agent_db` holds four tables — `conversation`, `message`, `resume_version` and
`trace_span`. The conversation freezes the job posting, the base resume and the token
prices in force when it was created: that is what makes a trace reproducible and what
keeps an old conversation costing what it cost. A message written by the advising agent
also carries its `recommendations`, the list the next turn may refer to by number.
`conversation.max_steps` is kept for the rows that were written under the loop design;
nothing reads it any more.

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

Every route but the health probe requires the `lynq-request-uuid` and `Authorization`
headers, and answers with the platform-wide `GlobalRestResponse` envelope. The caller is
resolved from the token against lynq-iam's `/auth/user-info`, the same way lynq-app-backend
resolves it, and that resolved user is the one a conversation belongs to — a `user-id`
header names nobody. A missing `Authorization` is a 401, a token lynq-iam refuses is a 401,
and lynq-iam being unreachable is a 503. Creating a conversation relays the same credential
to lynq-llm, which resolves it for itself.

The roles that token carries are read too: tailoring a resume is a candidate's operation, so
the four conversation routes sit behind `CandidatePrincipal` and answer 403 to anyone else.
lynq-bff carries the same check on `ResumeControllerImpl` and reaches these routes first —
this one is here so the guarantee does not depend on who the caller came through.

### A turn, transaction by transaction

A turn is **two** transactions with the expensive work outside both, so a second click
gets a 409 instead of blocking on the row lock for as long as the loop runs:

1. **Claim.** `SELECT ... FOR UPDATE`; a `RUNNING` newer than `AGENT_TURN_TIMEOUT` is a
   turn in flight (409), an older one is a dead process and gets taken over. The
   conversation flips to `RUNNING` with a fresh `run_token`, the user message is
   inserted, commit.
2. **The agents**, with no transaction open, under `AGENT_TURN_TIMEOUT - 30s`, so
   they die before anyone else can declare them dead.
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

`tests/test_live.py` talks to a real model and is skipped unless `AGENT_LIVE_LLM=true`:

```bash
AGENT_LIVE_LLM=true LLM_PROVIDER=ollama python -m unittest tests.test_live
AGENT_LIVE_LLM=true LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 \
  python -m unittest tests.test_live
```

It runs one edit turn and one advise turn end to end and checks what the code
guarantees whatever the model does: the resume changed only where the schema allows,
`personal_info` and the order of the experience are untouched, nothing without evidence
entered, and the advise turn wrote no change. On Bedrock it also checks that the model
answered through the schema's tool call rather than the text fallback — the one bet the
design makes on Nova Pro. Ollama breaks the tool call format often enough that only the
guarantees are checked there.

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
| `AGENT_MAX_STEPS` | `12` | Copied onto the row at creation for the sake of old rows; nothing reads it since the loop went |
| `AGENT_MODEL_RETRIES` | `2` | Times a model call is retried when Bedrock rejects what the model emitted |
| `AGENT_TURN_TIMEOUT` | `600` | Seconds before a `RUNNING` turn is treated as a dead process. Operational, never copied onto the row |
| `AGENT_JOB_DESCRIPTION_MAX_CHARS` | `6000` | The posting is truncated to this before it is frozen into the snapshot |
| `LYNQ_LLM_URL` | `http://localhost:8084/lynq-llm` | Where the skill extraction of the posting is asked for |
| `LYNQ_LLM_TIMEOUT` | `300` | Seconds allowed for the lynq-llm call |
| `LYNQ_IAM_URL` | `http://localhost:8080/lynq-iam` | Where the caller's token is resolved |
| `LYNQ_IAM_TIMEOUT` | `10` | Seconds allowed for that lookup |
| `LLM_PROVIDER` | `ollama` | `ollama` for local development, `bedrock` in the cloud |
| `LLM_TIMEOUT` | `300` | Seconds allowed for a single LLM call |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama endpoint |
| `OLLAMA_MODEL` | `qwen2.5:7b` | Model pulled into Ollama |
| `OLLAMA_INTENT_MODEL` | _(empty)_ | Model for the intent agent; `OLLAMA_MODEL` when empty |
| `OLLAMA_JUDGE_MODEL` | _(empty)_ | Model for the judge; `OLLAMA_MODEL` when empty |
| `BEDROCK_MODEL_ID` | — | Any model id the Converse API accepts, e.g. `amazon.nova-pro-v1:0` |
| `BEDROCK_INTENT_MODEL_ID` | _(empty)_ | Model for the intent agent, e.g. `amazon.nova-lite-v1:0`; `BEDROCK_MODEL_ID` when empty |
| `BEDROCK_JUDGE_MODEL_ID` | `amazon.nova-lite-v1:0` in `set_env.sh` | Model for the judge; `BEDROCK_MODEL_ID` when empty |
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
