# tools/seed

Loads the synthetic demo data in `corpus.json` into a running Lynq, so the analytics have something to show. The corpus holds 5 companies with an account, 200 job posts (35 LYNQ and 165 external), 100 candidates with their resumes, and the applications, views and closures between them. Everything goes in through Lynq's own endpoints (BFF, plus the feeder's internal routes for external job posts). lynq-llm generates the skills and similarity tags while loading; nothing is written to a database directly.

## Before running

Start the stack natively: lynq-iam, lynq-app-backend, lynq-llm, lynq-file-storage, lynq-bff and lynq-analytics, plus Redis and LocalStack. You also need Python 3.12.

## Run

```bash
./tools/seed/run.sh
```

The script:

1. creates `.venv` the first time;
2. takes `LYNQ_INTERNAL_TOKEN` from `lynq-feeders/set_env.sh`;
3. asks for the password of the seed accounts;
4. runs `check`, `load` and `replay`.

Export `LYNQ_SEED_PASSWORD` beforehand to skip the prompt.

Loading takes a while: about 300 lynq-llm calls and 100 PDF renders. If it stops, run it again; it skips whatever is already in Lynq. Extra arguments go to `load`:

```bash
./tools/seed/run.sh --steps candidates,views   # only some steps: companies, externals, candidates, views, close
./tools/seed/run.sh --refresh-externals        # enhance and ingest the external job posts again
```

## Accounts for the demo

- Candidates: `testuser1` to `testuser100`. `testuser14` has the strongest market fit, `testuser13` sits in the middle, and `testuser58` has a weak fit with a salary expectation above the market.
- Companies: `testcompany1` to `testcompany5`. At `testcompany1`, job post `lynq-001` gets the most applicants and `lynq-031` gets none.

## Notes

- Only external job posts keep their original posting date. The API dates everything else on the day of the load.
- `testuser1` and `testcompany1` may already exist locally. The load signs into them and overwrites their profile and company.
- Tests: `.venv/bin/python -m unittest discover -s tests -t .` (run from `tools/seed`).
