# LYNQ end-to-end tests (Selenium)

An end-to-end test that drives a real Chrome through the main flow of the
application: registration, job publication, deprecating a post and reviewing the
publications, the three ways a resume comes to exist, and an application sent
with one of them. Everything typed into the app is in Spanish, because that is the
language the UI runs in; the scripts themselves are in English.

## What `test.js` does

1. **Registers a candidate** through the 2-step wizard and completes their
   profile: picture (`images/candidate_mock.jpeg`), current position, about,
   GitHub, LinkedIn and birth date. Then logs out.
2. **Registers a recruiter (company account)** through the 4-step wizard —
   account details, owner profile and company details including the logo — and
   completes their profile with `images/recruiter_mock.jpeg`.
3. **Publishes a job** with a title, description, remote work type and salary
   range, and lets the *Generar habilidades* button generate the skills with AI
   (the backend's `POST /ml/skill-enhance`) instead of typing them by hand. The
   test asserts at least one skill chip comes back before submitting.
4. **Publishes a second job and deprecates it**, so the owner's list holds one
   post of each lifecycle state and the two can be compared. The second post is
   first confirmed to be searchable in the public feed; then it is opened from
   *Mis publicaciones* through its own *Editar* action — the only route the UI
   offers — its status is flipped from `OPEN` to `CLOSE` and saved. Back on
   *Mis publicaciones*, the test contrasts what the list draws for each state:
   the live post is badged **Abierto** and rendered as a normal card, the
   deprecated one is badged **Cerrado** and carries the dimmed `is-closed`
   styling, both keep their *Editar* action (closing is reversible from the same
   form), and the counter reports both posts. Finally it checks the difference
   candidates actually see: the closed post drops out of the public feed, which
   is built from `OPEN` posts only, while the open one is still searchable. Then
   logs out.
5. **Logs back in as the candidate** and builds up three resumes — applying asks
   which one to send, so they come before the application:
   1. **From a PDF, and named**: uploads `files/resume_mock_en.pdf` through the
      wizard's upload path (the ML service reads the document into a structured
      resume, so this step waits up to `EXTRA_LONG_TIMEOUT_MS`), then assigns an
      alias from the viewer's *Asignar alias* button and overrides it —
      asserting the success toast, that the button switches to *Editar alias*,
      and that reopening the dialog comes prefilled with the alias on file.
   2. **By translating it into Spanish**: opens *Traducir CV*, picks *Español*
      explicitly as the target language, waits for the ML translation, generates
      the template preview and asserts the PDF canvas actually draws content
      (fraction of non-white pixels), then confirms with *Usar esta plantilla* —
      asserting the success toast and that the switcher now offers a second
      resume carrying the `ES` tag. The imported CV is in **English** precisely
      so Spanish is on offer: the dialog only lists languages the candidate
      holds no resume in.
   3. **By filling in the form**: opens the wizard again from the viewer, takes
      the *Completá el formulario* path and types the resume in — personal
      details, one study, one job — picks the `CLASSIC` template, asserts the
      rendered preview is not blank, and finishes. Each of the three resumes is
      given a distinct alias, which is what the picker shows next.
6. **Applies to the open job with a chosen resume**: finds the job in the feed,
   opens its detail page and clicks *Postularme*, which opens the resume picker. The
   test asserts all three resumes are on offer, selects the one made with the
   form (the dialog preselects the first, so the pick is a real choice), applies,
   and checks the application shows up under *Mis Postulaciones*.

### Fixtures

| File | Used for |
| ---- | -------- |
| `images/candidate_mock.jpeg`, `images/recruiter_mock.jpeg` | The two profile pictures. |
| `files/resume_mock_en.pdf` | The CV imported in step 5.1. It is in English so that Spanish is still on offer in the translation dialog. |
| `files/resume_mock.pdf` | The same CV in Spanish. Not imported by the test any more — it is the sample the form fixture mirrors, and what to switch to for testing an import that needs no translation. |

Each account and each job carry a unique per-run suffix (base36 of the
timestamp), so the test can be run over and over without colliding with data
already in the database.

## Requirements

- Node.js 20 or newer.
- Google Chrome installed (Selenium Manager downloads the matching
  `chromedriver` on its own).
- The application running: the frontend plus `lynq-iam`, `lynq-app-backend`,
  `lynq-bff`, `lynq-file-storage` (uploading the pictures needs the storage
  service) and `lynq-ml` (the resume import and the AI skill generation).

```bash
docker compose up -d
```

## Running the test

```bash
cd selenium_tests
npm install

npm test              # visible browser, paced for watching
npm run test:slow     # visible browser, 1.5 s pause between actions
npm run test:headless # no browser window
```

The run is paced for being *watched*, not for being fast:

- **Every action is padded by 500 ms** (`BASE_ACTION_DELAY_MS`), so even a plain
  `npm test` waits after each click, keystroke and page load.
- **It opens on the login screen and holds there for 7 s** (`INTRO_PAUSE_MS`)
  before anything starts moving, which is the window to introduce the app.

`npm run test:slow` adds another second on top. Any pause length works, and it
stacks with the 500 ms padding:

```bash
node test.js --delay=1500        # 500 ms + 1.5 s = 2 s between actions
ACTION_DELAY_MS=400 npm test     # same thing through the environment
```

To strip the pacing out entirely — CI, or just an impatient run:

```bash
BASE_ACTION_DELAY_MS=0 INTRO_PAUSE_MS=0 npm run test:headless
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
| `BASE_ACTION_DELAY_MS` | `500`              | Padding added to every action, in milliseconds. `0` removes it. |
| `ACTION_DELAY_MS` | `0`                     | Extra pause after each action, added on top of the padding. |
| `INTRO_PAUSE_MS`  | `7000`                  | How long the run holds on the login screen before starting. |
| `TIMEOUT_MS`      | `20000`                 | Wait for UI elements.                       |
| `LONG_TIMEOUT_MS` | `60000`                 | Wait for backend round-trips.               |
| `EXTRA_LONG_TIMEOUT_MS` | `300000`          | Wait for LLM-backed flows (resume import, translation, template render). |
| `CHROME_HOST_RESOLVER_RULES` | unset      | Passed to Chrome as `--host-resolver-rules`. Needed when the stack runs under `docker compose`: the pre-signed S3 URLs carry the compose hostname, which the browser on the host cannot resolve — `MAP localstack 127.0.0.1` fixes the uploads and the PDF previews. |

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
