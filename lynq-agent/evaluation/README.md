# CV Tailor evaluation

The thesis experiment behind `lynq-agent`: does a judge that must quote its
evidence, on top of a shape guard in code, reduce the invalid edits of CV Tailor
against a single-pass ReAct agent, on a corpus of resumes and postings labelled
by humans, and at what cost per turn?

Everything in this folder and under `src/evaluation/` exists for that question.
The service never imports it.

## The question and the arms

**Question.** On the same corpus, the same model and the same scripted
conversations, which design lets fewer invalid edits into the resume, and what
does each design cost in model calls, dollars and seconds?

An edit is *invalid* when the text after it says something the resume or the
candidate never said (`invented`, `unsupported_skill`), loses what was there
(`dropped_content`, `dropped_skill`), moves what was there (`reordered`), changes
the language (`language`), rewrites a technology with the posting's spelling
(`wording`), or pads the text with much more than a rephrasing (`padding`).
These are the judge's own kinds, so the judge can be scored on the vocabulary
the humans use.

**Arms.** Three designs, all taking the same `TurnContext` and answering the
same `TurnOutcome` (`src/evaluation/arms/__init__.py`):

| arm | what runs | where it lives |
|---|---|---|
| `react` | the baseline: one agent over two tools, `find_evidence` and `edit`, up to 12 steps and 2 edits per turn, the tools writing as they are called. `edit` reaches the summary, one entry, the order of a section and the skill buckets through flat typed arguments, which is what the thesis document describes; the first `apply_edit` with a free-form payload broke on Bedrock and is not the baseline | `src/evaluation/arms/react/`, restored from the commit that removed it (`3582bd6`) with the four typed edit tools folded back into one, prompts in `resources/evaluation/react/` |
| `guard` | the current graph with the judge switched off: the editing agent proposes once, `agent.apply.plan` rejects what lost lines, numbers or technologies, what moved, and what names something unbacked, and everything that passes is applied | `agent.turn.run_turn(judge_enabled=False)` |
| `judge` | the current design: proposal, shape guard, judge with quoted evidence, apply, up to `editor.MAX_PASSES` passes | `agent.turn.run_turn` |

What is held fixed across the arms: the intent agent and its model, the editor
model, the trace collector, the greeting, the history window and the statements
the service passes. The `react` arm reads `unknown` from the intent agent as
`edit`, the fallback it always had, because its prompt knows two intents.

The judge runs on `BEDROCK_JUDGE_MODEL_ID` when set, as in production. For a
comparison where the model is not a variable, leave it unset and the judge
runs on the editor model.

## The corpus

`corpus/pairs.jsonl`: one (job posting, base resume) pair per line, with no
`personal_info`. Fields: `id`, `category`, `language` (the chat), `resume_language`,
`source`, `job` (the snapshot the service freezes: `title`, `company`, `description`,
`workType`, `extractedSkills`) and `base_resume` (the resume JSON the service
edits: `summary`, `work_experience`, `education`, `skills`).

The eight pairs committed today are synthetic: resumes and postings written for
the pilot, in Spanish, for the profiles the business case targets (administration,
accounting, human resources, sales, marketing, treasury) plus one technical one.
They exist to run the harness end to end and to tune the scripts and the
annotation guide. The corpus of the thesis replaces them with 30 to 50 pairs
whose postings are real (the scraped ones in `job_posts`, with the skills
`lynq-llm` extracted) and whose resumes are real resumes with consent, stripped
of `personal_info`, or exported conversations (`scripts/export_eval_corpus.py`).
A pair needs at least two gaps, skills the posting asks for and the resume does
not back, or the `gap` and `adversarial` scripts cannot be written for it.

`corpus/scripts.json`: the conversations, the same for every pair, with
placeholders the pair fills (`{gap1}`, `{gap2}`, `{company1}`, `{position1}`,
`{company2}`...). Four scripts:

| script | turns | what it is for |
|---|---|---|
| `tailor` | a plain request, a question, "do the first one" | the everyday path, including an advise turn |
| `gap` | confirms the first gap, names its job, asks for a summary | changes the candidate backs; a design that refuses them is useless |
| `paraphrase` | a rewrite with a no-invention clause, then the summary | where dropping, moving or padding content shows |
| `adversarial` | an invented number, an unclaimed skill, a reorder | what every design must keep out |

## Running it

```bash
cd lynq-agent && source .venv/bin/activate
LLM_PROVIDER=bedrock BEDROCK_MODEL_ID=amazon.nova-pro-v1:0 \
python scripts/run_eval.py --output evaluation/runs/pilot.jsonl --limit 3 --dry-run
```

Drop `--dry-run` to run. The output is one JSON line per turn, appended as it
finishes, so a crash costs one turn and the same command picks up where it
stopped. A failed turn is recorded with its error and ends its conversation. A
conversation interrupted half-way is run again from its first turn; the loader
keeps the last record of every key. Every record carries the resume before and
after, the diff as units, the rejected parts, the structural faults, the spans
with tokens, cost and latency, and the gaps before and after.

Against Ollama for a dry pass of the harness itself: `LLM_PROVIDER=ollama`. The
numbers of that pass are not the thesis's.

## Annotation

```bash
python scripts/export_annotation.py evaluation/runs/pilot.jsonl \
    --sheet evaluation/runs/pilot-annotation.csv --key evaluation/runs/pilot-key.csv
```

The sheet is what the annotators get: every applied part and every rejected
part, shuffled, with the text before and after, what the candidate said so far
and the posting's skills, and nothing that tells the arm or the system's verdict.
The key maps each row back; it stays with whoever runs the analysis. Two
annotators fill two copies of the same sheet; `GUIA_ANOTACION.md` is their guide.
A label is `valid` or one of the kinds above, or `other`.

## Metrics

```bash
python scripts/eval_metrics.py evaluation/runs/pilot.jsonl \
    --labels evaluation/runs/pilot-annotation-A.csv evaluation/runs/pilot-annotation-B.csv \
    --json evaluation/runs/pilot-metrics.json
```

One column per arm:

- what the code counts without a human: turns, failed turns, parts applied and
  rejected (by the system's kind), turns with a structural fault (entries
  reordered, added or dropped, an immutable field changed, education or
  `personal_info` touched, skills dropped or reordered), gap coverage, model
  calls, tool calls, passes, cost and latency per turn;
- with the labels: the invalid rate over applied parts and by kind, the edit
  turns with at least one invalid part, and the rejected parts the humans called
  valid (the price of the guard and the judge in refused good edits).

Then the paired differences `judge − react`, `judge − guard`, `guard − react`
with a 95 % bootstrap interval over conversations (the same (pair, script)
conversations drawn for both arms), the judge and the guard scored against the
human as a rejector (precision, recall, kappa over every labelled part they
saw), and the agreement between the two annotators (kappa on valid/invalid and
on the kind).

Gap coverage and the invalid rate are read together: a design that applies
nothing has a perfect invalid rate and zero coverage.

## What is not here yet

- The real corpus (see above). The pilot pairs are synthetic.
- The annotation itself: two annotators, a pilot of five pairs to settle the
  guide, then the full set.
- The baseline is the last ReAct version that ran in the service, with its
  typed arguments; its four edit tools were folded back into the one `edit` the
  thesis document describes. The very first version, with a free-form payload,
  is in the history (`c16a253^`) and is not an arm.
