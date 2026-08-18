# LYNQ end-to-end tests (Selenium)

An end-to-end test that drives a real Chrome through the main flow of the
application. Everything typed into the app is in Spanish, because that is the
language the UI runs in; the scripts themselves are in English.

## What `test.js` does

1. **Registers a candidate** through the 2-step wizard and completes their
   profile: picture (`images/candidate_mock.jpeg`), current position, about,
   GitHub, LinkedIn and birth date. Then logs out.
2. **Registers a recruiter (company account)** through the 4-step wizard —
   account details, owner profile and company details including the logo — and
   completes their profile with `images/recruiter_mock.jpeg`.
3. **Publishes a job** with a title, description, remote work type, salary range
   and six skills typed by hand. Then logs out.
4. **Logs back in as the candidate**, finds the job in the feed, opens its detail
   page, applies, and checks the application shows up under *Mis Postulaciones*.

Each account and the job carry a unique per-run suffix (base36 of the
timestamp), so the test can be run over and over without colliding with data
already in the database.

## Requirements

- Node.js 20 or newer.
- Google Chrome installed (Selenium Manager downloads the matching
  `chromedriver` on its own).
- The application running: the frontend plus `lynq-iam`, `lynq-app-backend` and
  `lynq-file-storage` (uploading the pictures needs the storage service).

```bash
docker compose up -d
```

## Running the test

```bash
cd selenium_tests
npm install

npm test              # visible browser, as fast as it goes
npm run test:slow     # visible browser, 800 ms pause between actions
npm run test:headless # no browser window
```

`npm run test:slow` is the one to use when you want to *watch* the run: it waits
after every click, keystroke and page load so the navigation is easy to follow.
Any pause length works:

```bash
node test.js --delay=1500        # 1.5 s between actions
ACTION_DELAY_MS=400 npm test     # same thing through the environment
```

### Pointing at a different frontend

`BASE_URL` defaults to `http://localhost:3000`, the port the frontend is exposed
on by `docker-compose`. When the frontend is served by the Vite dev server
(`npm run dev` inside `lynq-app-frontend`) it listens on **5173** instead, so the
URL has to be passed explicitly:

```bash
BASE_URL=http://localhost:5173 npm test
BASE_URL=http://localhost:5173 npm run test:slow
```

Running against the wrong port fails right at the first step with
`unknown error: net::ERR_CONNECTION_REFUSED` — that is Chrome saying nothing is
listening there, not a problem with the test.

The cleanup script does not use `BASE_URL`: it talks straight to MySQL and to
`lynq-file-storage`, so `npm run cleanup` works either way.

### Environment variables

| Variable          | Default                 | Purpose                                     |
| ----------------- | ----------------------- | ------------------------------------------- |
| `BASE_URL`        | `http://localhost:3000` | Frontend URL (use `:5173` with `vite dev`). |
| `HEADLESS`        | `false`                 | `true` runs Chrome without a window.        |
| `ACTION_DELAY_MS` | `0`                     | Pause after each action, in milliseconds.   |
| `TIMEOUT_MS`      | `20000`                 | Wait for UI elements.                       |
| `LONG_TIMEOUT_MS` | `60000`                 | Wait for backend round-trips.               |

The console prints each step as it happens and, on success, the accounts it
created together with their password, in case you want to keep using them by
hand.

## Deleting the data the test created

`cleanup-data.js` removes the test accounts and everything hanging off them:
profiles, companies, jobs, skills, resumes, applications (both the ones they
submitted and the ones their posts received) and the uploaded pictures —
including the S3 object, because deletion goes through the `lynq-file-storage`
API.

```bash
npm run cleanup:dry-run              # show what would be deleted, touch nothing
npm run cleanup                      # delete, asking for confirmation first
npm run cleanup -- --yes             # no confirmation
npm run cleanup -- --suffix=mso05y37 # only one specific run
```

The default filter is `email LIKE '%@lynq.test'`, the domain `test.js` uses, so
it cannot reach a real account. The script refuses patterns that are too broad
(`%`) and, if the file-storage does not answer, **it stops before touching the
database**: deleting the accounts first would leave `stored_files` rows that
nothing references and that can no longer be traced. `--ignore-files` forces it
through anyway and then prints the `curl` commands needed to delete those files
by hand.

It connects to MySQL as `root` / `federico` on `localhost:3306` (the defaults in
`application.yaml`). Override with `DB_HOST`, `DB_PORT`, `DB_USER`,
`DB_PASSWORD` and `FILE_STORAGE_URL`.

> Refresh tokens left in Redis are not cleaned up: they expire on their own and
> are worthless once the IAM account is gone.
