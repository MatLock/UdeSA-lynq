# lynq-analytics

Analytics service for the Lynq platform. It serves the numbers that sit next to a job post — the estimated **time to fill** a company can expect, a candidate's **standing** among the applicants of a post, and **salary medians** for a position and for similar candidates — and the ones behind the analytics page: a candidate's **market fit** and their position among similar candidates, the **market** the platform sees, and a company's comparison of its own job posts.

It does not read `lynq_backend_db`. Its data will arrive as domain events published by `lynq-app-backend` (SNS topic `lynq-domain-events` → SQS queue `lynq-analytics-events`) and be projected into its own schema, `lynq_analytics_db`.

Today the service boots, authenticates every request against [`lynq-iam`](../lynq-iam), answers with the platform's response envelope, and records every domain event it receives in an append-only `domain_events` table. Job post, candidate and application events are also projected into a read model, over which the service resolves similar job posts and similar candidates. Served today: the **time to fill** of the job posts similar to a company's own, a candidate's **standing** among the applicants of a job post, the **salary** medians of the position and of similar candidates, the candidate's **market fit** against the open job posts and their percentile among similar candidates, the **market** (skill demand, salaries by category and work type, job posts published per week) and the **company job posts** comparison. The first three are computed on request; market fit and market read a **daily snapshot** that a Kubernetes CronJob triggers.

---

## Technologies

| Area              | Stack                                                                        |
| ----------------- | ---------------------------------------------------------------------------- |
| Language / JDK    | Java 21                                                                      |
| Framework         | Spring Boot 4.0.6 (Web, Data JPA, Actuator, AOP, Security, Validation)       |
| Web server        | Jetty (Tomcat excluded)                                                      |
| Persistence       | MySQL 9 (`lynq_analytics_db`), Hibernate / Spring Data JPA, Liquibase migrations |
| Inter-service     | Spring Cloud OpenFeign — clients for `lynq-iam` and `lynq-app-backend`       |
| Messaging         | Spring Cloud AWS 4 SQS (`@SqsListener`), AWS SDK v2                          |
| Cache             | Spring Cache over Redis (Spring Data Redis, Lettuce)                         |
| Docs              | springdoc-openapi (Swagger UI)                                              |
| Logging           | Log4j2 + SLF4J MDC for per-request correlation IDs; `@AuditLog` aspect       |
| Metrics           | Micrometer + Prometheus registry                                            |
| Build             | Maven (Spring Boot plugin), Dockerfile on `eclipse-temurin:21-jre-alpine`   |
| Tests             | JUnit Jupiter, Testcontainers (MySQL, LocalStack, Redis, MockServer for `lynq-iam`), Awaitility, JaCoCo |

---

## Request lifecycle

Every request passes through an ordered filter chain before reaching a controller:

| Order | Filter                       | Scope                        | Purpose                                                                                          |
| :---: | ---------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------ |
| 0     | `RequestUuidFilter`          | `/*`                         | Require the `lynq-request-uuid` header; bind it to SLF4J MDC (`requestId`) and echo it back on the response. `403` if missing. |
| 1     | `InternalTokenFilter`        | `/internal/*`                | `401` unless the `lynq-internal-token` header matches `lynq.internal.token`; an empty configured token refuses every call. |
| 2     | `AuthHeaderExistenceFilter`  | `/*` (Swagger and `/internal` exempt) | `401` if the `Authorization` header is missing or blank.                                |
| 3     | `IamAuthenticationFilter`    | `/*` (Swagger and `/internal` exempt) | Call `lynq-iam` for the token's user info, then load a `LynqUserPrincipal` — with the roles it reports as authorities — into the `SecurityContext`. `401` if IAM will not resolve the token, `503` if IAM is unreachable. |

`/internal/**` has no user behind it — the caller is the snapshot CronJob — so it skips the bearer-token filters and is guarded by the shared internal token instead, as `lynq-app-backend`'s internal routes are.

> This service does **not** verify the access token's signature. Every request reaches it through
> [`lynq-bff`](../lynq-bff), which validates the signature before proxying, which is also why the
> API will sit behind the `/dmz/analytics` prefix.

Spring Security is configured **stateless** and `permitAll` (`SecurityConfig`); the filter chain above is what enforces authentication. Authorization is method security: `@HasRole(Role.CANDIDATE)` / `@HasRole(Role.COMPANY)` over `@PreAuthorize("hasRole('{value}')")`, with `GrantedAuthorityDefaults("R_")` mapping `hasRole('CANDIDATE')` onto the `R_CANDIDATE` authority. A caller without the role gets `403` with the usual `ErrorRestResponse`.

Every response uses the platform envelope: `GlobalRestResponse` (`success`, `data`) on success and `ErrorRestResponse` (adds `reason`) on failure, mapped from exceptions in `ControllerExceptionHandler`.

### Roles per endpoint

The role check lives here, not in lynq-bff: the gateway relays the route and passes this service's
`403` back. Each endpoint carries its `@HasRole` from the PR that adds it; a route without one is
open to any authenticated caller. The role is only the first gate — whether a company owns the job
post, or a candidate applied to it, is checked against the read model by the endpoint itself.

| Endpoint                                      | `@HasRole`  | Also checked                       | Item |
| --------------------------------------------- | ----------- | ---------------------------------- | ---- |
| `GET /dmz/analytics/job/{jobId}/time-to-fill` | `COMPANY`   | the caller owns the job post       | F8   |
| `GET /dmz/analytics/company/me/jobs`          | `COMPANY`   | —                                  | K8   |
| `GET /dmz/analytics/job/{jobId}/standing`     | `CANDIDATE` | the caller applied to the job post | F7   |
| `GET /dmz/analytics/candidate/me/benchmark`   | `CANDIDATE` | —                                  | K5   |
| `GET /dmz/analytics/job/{jobId}/salary`       | none        | —                                  | F9   |
| `GET /dmz/analytics/market`                   | none        | —                                  | K8   |
| `POST /internal/snapshot`                     | —           | the internal token, not a user     | K3   |

`HasRoleAuthorizationTest` pins the mechanism on a test-only controller: each role reaches its
route, the other role and a caller without roles get `403` with the role named in `reason`, and an
unannotated route lets any of them through.

---

## Domain events

`lynq-app-backend` publishes to the SNS topic `lynq-domain-events`; the SQS queue `lynq-analytics-events` is subscribed to it with **raw message delivery**, so each message body is the event envelope exactly as published:

```json
{
  "eventId": "5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10",
  "eventType": "ApplicationSubmitted",
  "aggregateType": "APPLICATION",
  "aggregateId": "33333333-3333-3333-3333-333333333333",
  "occurredOn": "2026-09-29T14:03:27.125Z",
  "payload": {
    "applicationId": "33333333-3333-3333-3333-333333333333",
    "jobId": "77777777-7777-7777-7777-777777777777",
    "userId": "11111111-1111-1111-1111-111111111111",
    "appliedOn": "2026-09-29",
    "lynqScore": 72
  }
}
```

| Field           | Rule                                                                       |
| --------------- | -------------------------------------------------------------------------- |
| `eventId`       | UUID, required. Deterministic on the producer side, so a replay repeats it |
| `eventType`     | required, up to 64 characters                                              |
| `aggregateType` | required, up to 32 characters (`JOB_POST`, `APPLICATION`, `CANDIDATE`)     |
| `aggregateId`   | required, up to 36 characters                                              |
| `occurredOn`    | ISO-8601 instant, required: when the fact happened, not when it was sent   |
| `payload`       | JSON object, required; stored as received                                  |

`DomainEventListener` hands each message to `DomainEventService`, which stores it in `domain_events` (`received_on` stamped on arrival). An event of any type is stored, so a projection added later can be rebuilt from the table.

- **Idempotency** — `event_id` is `UNIQUE` in `domain_events`; there is no separate `processed_events` table. An event already stored is acknowledged and skipped. Two consumers racing on the same event both reach the insert; the loser hits the constraint, sees the row and acknowledges too.
- **Failures** — a message that is not JSON, or an envelope missing a field, is not acknowledged. SQS redelivers it and, after 5 receives, moves it to `lynq-analytics-events-dlq` (kept 14 days).
- **Missing queue** — `queue-not-found-strategy: fail`: the service does not start if the queue is missing, instead of creating one without its dead-letter queue.

Locally, `localstack-init/02-init-domain-events.sh` creates the topic, the queue, the DLQ and the subscription.

### Projections

Once an event is stored, `DomainEventService` hands it to every `DomainEventProjector` that supports its type, **in the same transaction**. If a projection fails, the stored event is rolled back with it and SQS redelivers the message. Event types no projector supports yet are stored and nothing else.

### Job post read model

`JobPostProjector` keeps three tables up to date from the four job post events:

| Table             | Key               | Holds                                                                                          |
| ----------------- | ----------------- | ---------------------------------------------------------------------------------------------- |
| `job_posts`       | `id` (the job id) | title, category, work type, source, company, author, salary range and currency, status, `published_on`, `closed_on`, `close_reason`, `reopened_on` |
| `job_post_skills` | `job_id`, `skill` | the skills the post asks for                                                                   |
| `job_post_tags`   | `job_id`, `tag`   | its similarity tags, indexed by `tag`                                                          |

| Event              | Payload                                                                                                             | Projection                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `JobPostPublished` | `jobId`, `title`, `workType`, `source`, `publishedOn` required; `category`, `companyId`, `createdByUserId`, `salaryRangeDown`, `salaryRangeTop`, `salaryCurrency`, `skills`, `similarityTags` | creates the post `OPEN`, or refreshes it                                    |
| `JobPostUpdated`   | `jobId`, `title`, `workType` required; `salaryRangeDown`, `salaryRangeTop`, `salaryCurrency`, `skills`, `similarityTags` | overwrites those fields and replaces the skills and tags: a missing value clears it, as the backend's update does |
| `JobPostClosed`    | `jobId`, `closedOn` required; `closeReason`                                                                         | `CLOSE` with its date and reason                                            |
| `JobPostReopened`  | `jobId`, `reopenedOn` required                                                                                      | `OPEN`, stamps `reopened_on`, clears the close; `published_on` is kept      |

- **Order** — SQS standard does not guarantee order, so each post keeps two watermarks: `details_occurred_on` (published and updated) and `status_occurred_on` (published, closed and reopened). An event older than the watermark it would move is discarded; it stays in `domain_events`. Two watermarks rather than one keep an update that arrives first from swallowing a close that happened before it.
- **Before the publish** — an update, close or reopen for a post not published yet fails with `UnknownJobPostException`. SQS redelivers it, by then the publish has usually arrived, and after 5 receives it goes to the DLQ.
- **Validation** — the payload's `jobId` must match the envelope's `aggregateId`. A missing required field or an unreadable payload fails with `InvalidDomainEventException` and ends in the DLQ. Unknown fields are ignored, so the backend can add fields first.
- **Skills and tags** — trimmed, blanks dropped, duplicates removed ignoring case, since MySQL's collation compares them that way.

### Candidate read model

`CandidateProjector` and `ApplicationProjector` keep four more tables up to date:

| Table              | Key                     | Holds                                                                               |
| ------------------ | ----------------------- | ----------------------------------------------------------------------------------- |
| `candidates`       | `id` (the user id)      | expected salary and its currency                                                    |
| `candidate_skills` | `candidate_id`, `skill` | the candidate's skills                                                              |
| `candidate_tags`   | `candidate_id`, `tag`   | their similarity tags, indexed by `tag`                                             |
| `applications`     | `id` (the application id) | job post, candidate, `applied_on`, the `lynq_score` the backend computed when the candidate applied; unique by job post and candidate, indexed by candidate |

| Event                            | Aggregate     | Payload                                                                                    | Projection                                                                                  |
| -------------------------------- | ------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| `CandidateSkillsUpdated`         | `CANDIDATE`   | `userId` required; `skills`, `similarityTags`                                              | creates the candidate if needed and replaces its skills and tags; the salary is kept        |
| `CandidateExpectedSalaryUpdated` | `CANDIDATE`   | `userId` required; `expectedSalary`, `currency` (required with a salary)                   | creates the candidate if needed and sets its salary; no `expectedSalary` clears both; skills and tags are kept |
| `ApplicationSubmitted`           | `APPLICATION` | `applicationId`, `jobId`, `userId`, `appliedOn`, `lynqScore` required                       | creates the application, or refreshes it                                                    |

- **Order** — each candidate keeps two watermarks, `skills_occurred_on` and `salary_occurred_on`, for the same reason a job post does: a salary change that arrives late must not be dropped because the skills moved since. An application keeps `occurred_on`. An event older than the watermark it would move is discarded and stays in `domain_events`.
- **Candidates without skills** — a candidate appears with whichever event arrives first. Applications do not create or require one: the standing only needs the application's score, and a candidate who applied without ever updating their skills has no row.
- **Before the publish** — an application for a job post not published yet fails with `UnknownJobPostException`, redelivered by SQS like an early job post update and sent to the DLQ after 5 receives. `applications.job_id` is a foreign key to `job_posts`.
- **Score** — `lynqScore` is the integer from 0 to 100 that `lynq-app-backend` computes; analytics does not recompute it. A score outside that range is an invalid event and ends in the DLQ, as does an `expectedSalary` that is not positive.
- **Validation** — the payload's `userId` or `applicationId` must match the envelope's `aggregateId`. Skills and tags are normalised as for job posts.

---

## Similarity

The analytics compare a job post with **similar job posts** (time to fill, salary of the position) and with **similar candidates** (their expected salary). Similarity is set overlap on similarity tags, each tag weighted by how rare it is among job posts. There is no trained model and no embeddings: the semantic step already happens when `lynq-llm` turns skills into generalised tags.

### Tag weights

`tag_frequency` holds, for every tag, `df` (how many job posts carry it) and its `weight`:

```
weight = ln((N + 1) / (df + 1))        N = job posts
```

A tag in every job post weighs `0`; a tag seen once weighs `ln((N + 1) / 2)`. Candidate tags do not change the weights: rarity is what the market asks for. Tags are stored lower-cased and compared ignoring case. A tag missing from the table, from a post published after the last run, weighs as much as the rarest known one; with an empty table every tag weighs `1` and the metrics reduce to counting tags.

The table is recomputed as the first step of the [daily snapshot](#daily-snapshot), and on startup if it is empty. Every tag seen is upserted and the ones no job post has anymore are removed.

The **median weight** the thresholds use is taken over occurrences — every row of `job_post_tags` with the weight of its tag — not over distinct tags. Most distinct tags appear in one or two posts, so a median over them sits near the maximum weight and no threshold would admit anything; per occurrence it is the weight of a typical tag in a typical post.

### Metrics

Both live behind `TagSimilarity` (`score`, `threshold`), with `m` the median weight and `k` the number of median tags asked for:

| Comparison                     | Class                       | Score                                        | Threshold                          |
| ------------------------------ | --------------------------- | -------------------------------------------- | ---------------------------------- |
| job post against job post      | `WeightedJaccardSimilarity` | `Σ w(mine ∩ other) / Σ w(mine ∪ other)`      | `min(1, k · m / Σ w(mine))`        |
| candidate against the job post | `WeightedOverlapSimilarity` | `Σ w(job ∩ candidate)`                       | `k · m`                            |

Jaccard keeps a broad post that contains mine from counting as the same position. The candidate score does not divide by the union, so extra tags do not count against a candidate, and a single rare tag can admit on its own.

`SimilarityService.findSimilarJobPosts(jobId, eligible)` and `findSimilarCandidates(jobId, eligible)`:

1. load the posts or candidates that share at least one tag with the job post (the post itself excluded) and keep the `eligible` ones — closed posts, posts with a salary in a currency, candidates with an expected salary: whatever the analytic needs;
2. score them and drop the ones that score `0`, so a shared tag that weighs nothing admits nobody;
3. admit those at or above the threshold for `k = 2`; if fewer than 5 pass, admit those at or above `k = 1` and flag the result as `fallback`.

Matches are ordered by score and then by skills in common. `Distribution.of(values)` gives the `n`, median, p25 and p75 of a sample, interpolating linearly between values.

| Property                                          | Default         | |
| ------------------------------------------------- | --------------- | - |
| `lynq.analytics.similarity.threshold-tags`        | `2`             | `k` of the threshold |
| `lynq.analytics.similarity.fallback-threshold-tags` | `1`           | `k` when the sample is short |
| `lynq.analytics.similarity.min-sample`            | `5`             | below it, fall back |
| `lynq.analytics.standing.min-applicants`          | `5`             | below it, the standing has no `medianScore` |

---

## Time to fill

`GET /dmz/analytics/job/{jobId}/time-to-fill` — how long job posts like this one stayed open
before they closed. Only the `COMPANY` user who published the job post: another company gets
`403`, and so does everyone for a scraped post, which has no author.

```json
{
  "success": true,
  "data": {
    "median": 21.0, "p25": 14.0, "p75": 25.0, "n": 12, "insufficientData": false,
    "externalJobPosts": 10,
    "expiredByPolicy": 3, "expiredAfterDays": 25,
    "daysOpen": 9,
    "overall": null
  }
}
```

- **Sample** — the [similar job posts](#similarity) that are `CLOSE` with a `closed_on`. Each
  counts the days from when it opened to `closed_on`: from `published_on`, or from
  `reopened_on` when it was reopened later, so a reopened post measures its last open period.
  A post that closed before it opened is bad data and is left out.
- **Censored closes** — a post closed as `EXPIRED_BY_POLICY` was still open when the policy
  closed it: its real time to fill is unknown and longer than what it shows. It stays out of the
  median and is counted in `expiredByPolicy`; `expiredAfterDays`
  (`lynq.analytics.time-to-fill.expired-after-days`, 25, the same window as the backend's
  `lynq.verification.expire-after-days`) says after how long the policy closes a post.
- **Days on the market** — a scraped posting can be taken down without being filled, so for
  those the days measure how long it stayed up. `externalJobPosts` says how many of the `n`
  are such postings, so the screen can say so.
- **Short samples** — below `lynq.analytics.time-to-fill.min-sample` (5) closes, `median`,
  `p25` and `p75` are null and `insufficientData` is `true`, and `overall` carries the same
  figures over every closed post of the platform (the post itself and the censored ones
  excluded), with its own `insufficientData`. It is the platform's median, not that of similar
  posts, and the screen labels it so. With enough similar closes `overall` is `null`.
- **This post** — `daysOpen` counts from when the post opened until today, or until its
  `closed_on` if it is closed, in the service clock's zone.
- **Cache** — an hour per job post and caller; see [Cache](#cache).

| Property                                         | Default | |
| ------------------------------------------------ | ------- | - |
| `lynq.analytics.time-to-fill.min-sample`         | `5`     | below it, the similar posts' statistics are withheld and `overall` is sent |
| `lynq.analytics.time-to-fill.expired-after-days` | `25`    | the days after which the backend's policy closes an external post |

---

## Standing

`GET /dmz/analytics/job/{jobId}/standing` — where the caller stands among the applicants of a job post. Only `CANDIDATE` users, and only for a job post they applied to.

```json
{
  "success": true,
  "data": {
    "rank": 2,
    "totalApplicants": 6,
    "percentile": 66.66666666666667,
    "score": 72,
    "medianScore": 66.0
  }
}
```

| Field             | Meaning                                                                                       |
| ----------------- | --------------------------------------------------------------------------------------------- |
| `rank`            | one plus the applicants who scored higher, so ties share a rank and the next one skips it (90, 72, 72, 60 ranks 1, 2, 2, 4) |
| `totalApplicants` | applications to the job post, the caller's included                                           |
| `percentile`      | `100 · (below + tied / 2) / totalApplicants`, the caller counted among the tied              |
| `score`           | the `lynqScore` of the caller's application, as the backend computed it when they applied    |
| `medianScore`     | median of every applicant's score; `null` below `lynq.analytics.standing.min-applicants` (5) |

`StandingService` reads two things from the read model: the caller's application, and the scores of every application to the job post. No other applicant is identified, and below five applicants the median is withheld: with two applicants, the median and the caller's own score give the other one's score away. The rank and the percentile only say how many scored higher, so they are always answered.

- **Refusals** — a `COMPANY` caller gets `403` from `@HasRole`. A candidate without an application to the job post gets `403`, and `404` if analytics holds no job post with that id.
- **Freshness** — an application reaches the read model through `ApplicationSubmitted`, so a candidate who has just applied can get `403` for the seconds the event takes to arrive. After that, the answer is cached for an hour per job post and candidate: new applicants do not move a cached rank until it expires.
- **Naming** — the frontend shows it as "your position among the applicants", with its rank and N side by side; `percentile` is in the contract but the app keeps that word for the peer benchmark.

---

## Salary

`GET /dmz/analytics/job/{jobId}/salary` — what the position pays and what similar candidates expect. Open to any authenticated user, candidate or company.

```json
{
  "success": true,
  "data": {
    "positionSalary": {
      "median": 300000.0, "p25": 200000.0, "p75": 400000.0,
      "n": 5, "currency": "ARS", "insufficientData": false
    },
    "peersExpectedSalary": {
      "median": null, "p25": null, "p75": null,
      "n": 2, "currency": "ARS", "insufficientData": true
    }
  }
}
```

| Block                 | Sample                                                                                      |
| --------------------- | ------------------------------------------------------------------------------------------- |
| `positionSalary`      | the [similar job posts](#similarity) with a salary in the currency, each counted at the middle of its range, or at its only bound |
| `peersExpectedSalary` | the [similar candidates](#similarity) with an expected salary declared in the currency     |

- **Currency** — the job post's `salary_currency`; a post without a salary is compared in `lynq.analytics.salary.default-currency` (`ARS`). Salaries in another currency are left out rather than converted.
- **Median, not mean** — scraped salaries mix monthly and yearly figures and carry typos; the quartiles show the spread without letting one of them drag the number.
- **Short samples** — below `lynq.analytics.salary.min-sample` (5) values a block answers `insufficientData: true` with `median`, `p25` and `p75` null, and `n` still visible. With two candidates, a median gives their expected salaries away.
- **Freshness** — expected salaries arrive through `CandidateExpectedSalaryUpdated` and start empty, so `peersExpectedSalary` reads "insufficient data" until candidates fill it in. The answer is cached for an hour per job post.
- **Refusals** — `404` if analytics holds no job post with that id.

| Property                                    | Default | |
| ------------------------------------------- | ------- | - |
| `lynq.analytics.salary.min-sample`          | `5`     | below it, the block withholds its statistics |
| `lynq.analytics.salary.default-currency`    | `ARS`   | currency of a job post without a salary |

---

## Daily snapshot

Some numbers cannot be computed on request. A candidate's percentile among similar candidates needs the market fit of every one of them, and a chart with a date on its axis needs the state of each day, which the read model overwrites. Those are written once a day by the snapshot, which reads the **current state of the read model** — never the events — and replaces the rows of the day, so running it twice the same day gives the same result.

`POST /internal/snapshot` takes it. There is no schedule inside the service: the `lynq-analytics-snapshot-cronjob` in the Helm chart calls it every day at 05:00 in Buenos Aires (08:00 UTC), two hours after the feeders, so the day includes what they ingested and closed.

- **Trigger** — authenticated with the `lynq-internal-token` header, not a bearer token. It answers `202` with no body and runs in the background; a second call while one is running gets `409`. The run is logged under the `lynq-request-uuid` of the trigger, and that uuid travels to `lynq-app-backend` with the score batches.
- **Date** — today in `lynq.analytics.snapshot.zone` (`America/Argentina/Buenos_Aires`).
- **Steps** — in order: the [tag weights](#tag-weights), the [daily aggregates](#market) of the open job posts, and the [candidate benchmark](#market-fit). A failing step is logged and the next one still runs; each step writes all of its rows in one transaction or none.
- **Holes** — a day the snapshot did not run, or a step failed, has no rows. The charts show it as a hole, which is honest; nothing reconstructs it automatically.

```bash
curl -X POST http://localhost:8091/lynq-analytics/internal/snapshot \
  -H "lynq-request-uuid: $(uuidgen)" -H "lynq-internal-token: local-internal-token-not-a-secret"
```

| Table                          | Key                                           | Written by        |
| ------------------------------ | --------------------------------------------- | ----------------- |
| `candidate_daily_benchmark`    | `snapshot_on`, `candidate_id`                 | market fit        |
| `candidate_daily_skill_unlocks`| `snapshot_on`, `candidate_id`, `skill`        | market fit        |
| `job_daily_stats`              | `snapshot_on`, `category`                     | daily aggregates  |
| `skill_daily_demand`           | `snapshot_on`, `skill`                        | daily aggregates  |
| `category_daily_salary`        | `snapshot_on`, `category`, `work_type`, `currency` | daily aggregates |

Skills and categories are grouped the way MySQL's collation compares them — ignoring case and accents — and stored under their most frequent spelling. A job post without a category is stored under the empty category and answered as `null`.

---

## Market fit

`GET /dmz/analytics/candidate/me/benchmark` — where the caller stands in the market, beyond any single job post. Only `CANDIDATE` users, and always the caller: there is no user id in the path, or anyone could read anyone's profile.

The `lynqScore` is a candidate against a job post, not a property of the candidate, so the standing of a job post has no counterpart across posts. The snapshot gives each candidate three numbers a day:

| Number | Definition |
| ------ | ---------- |
| **Market fit** | the median `lynqScore` of the candidate against the open job posts relevant to them |
| **Reach** | the share of those job posts where the score is above `reach-threshold` (60) |
| **Percentile among peers** | where the market fit falls among the candidates similar to them, the candidate included among the tied |

- **Relevant job posts** — the open ones whose tags [overlap](#metrics) the candidate's by at least one median tag weight (`relevance-threshold-tags`, 1). Weighted, as everywhere else in the service: a tag every post carries does not make the whole market relevant.
- **Peers** — the candidates whose tags overlap the candidate's by at least two median tag weights (`peer-threshold-tags`, 2), and who have a market fit of their own.
- **Scores** — analytics does not score: it only receives scores inside `ApplicationSubmitted`, for real applications. The snapshot sends every candidate and relevant job post pair to `lynq-app-backend`'s `POST /internal/score/batch`, the same calculator as a pure function, in requests of up to `pairs-per-request` (2000) pairs. If the backend is down, the step writes nothing.
- **Skill coverage** — of the skills the relevant job posts ask for, counted per job post, the share the candidate has; compared with the median coverage of the peers.
- **Skills that unlock job posts** — for each skill the candidate lacks in the relevant job posts at or below the threshold, the snapshot scores the candidate again with that skill added, against those posts, and counts the ones that go above the threshold. The top `skill-unlocks` (5) are kept.
- **Thresholds** — below `min-relevant-jobs` (5) relevant job posts the fit, the reach and the coverage are `null`; `jobs_scored` is kept, since it is what makes the number auditable. Below `min-peers` (5) peers the percentile and the peers' quartiles are `null`, and `peer_group_size` is still answered: with four people, each point of a distribution is someone a colleague can recognise.
- **Without skills or tags** — a candidate with neither gets no row: a hole is better than a zero that looks like data.
- **The threshold is stored** — every row carries the `reach_threshold` it was computed with. Changing `lynq.analytics.benchmark.reach-threshold` does not rewrite the past rows, and the series shows where it changed.

```json
{
  "success": true,
  "data": {
    "snapshotOn": "2026-10-03", "marketFit": 61, "jobsScored": 40,
    "aboveThresholdPct": 55, "reachThreshold": 60,
    "peerPercentile": 72, "peerGroupSize": 38,
    "peerFitP25": 40, "peerFitMedian": 52, "peerFitP75": 66,
    "skillCoveragePct": 45, "peerCoverageMedian": 38,
    "skillUnlocks": [{ "skill": "Kafka", "jobsUnlocked": 4 }],
    "series": [{ "snapshotOn": "2026-10-03", "marketFit": 61, "aboveThresholdPct": 55,
                 "peerPercentile": 72, "peerGroupSize": 38 }]
  }
}
```

The answer is the latest row of the caller with the series of the last `series-days` (90). Before the first snapshot that includes them, `snapshotOn` is `null` and the lists are empty.

| Property                                         | Default | |
| ------------------------------------------------ | ------- | - |
| `lynq.analytics.benchmark.reach-threshold`       | `60`    | stored with each row |
| `lynq.analytics.benchmark.min-relevant-jobs`     | `5`     | below it, no fit, reach or coverage |
| `lynq.analytics.benchmark.min-peers`             | `5`     | below it, no percentile |
| `lynq.analytics.benchmark.relevance-threshold-tags` | `1`  | median tag weights a relevant job post shares |
| `lynq.analytics.benchmark.peer-threshold-tags`   | `2`     | median tag weights a peer shares |
| `lynq.analytics.benchmark.skill-unlocks`         | `5`     | skills kept per candidate |
| `lynq.analytics.benchmark.pairs-per-request`     | `2000`  | pairs per score batch; the backend takes up to 5000 |
| `lynq.analytics.benchmark.series-days`           | `90`    | days of the series |

---

## Market

`GET /dmz/analytics/market?currency=ARS` — what the platform sees, for any authenticated user.

| Field              | Source | |
| ------------------ | ------ | - |
| `snapshotOn`, `openJobPosts`, `openWithSalary` | `job_daily_stats` of the latest snapshot | |
| `skillDemand`      | `skill_daily_demand` | the `top-skills` (10) skills most open job posts ask for, with `weeklyChange` against the snapshot seven days earlier, `null` without one |
| `salary`           | `category_daily_salary` in `currency` | per category and work type, the n, median, p25 and p75 of the open job posts with a salary, each counted at the middle of its range; the statistics are withheld below `min-sample` (5) and `insufficientData` is `true` |
| `publishedPerWeek` | the `job_posts` read model | job posts published in each of the last `weeks` (12) complete weeks, Monday to Sunday |

The published job posts are counted from the read model and not from the snapshot: `published_on` is a fact with a date, so the count is exact and covers the history a replay brings, where a snapshot only knows the days since it started. Open posts, demand and salaries change state, so those need the snapshot.

`currency` is `ARS` or `USD` (`lynq.analytics.market.currencies`), `ARS` when absent; any other is a `400`.

### Company job posts

`GET /dmz/analytics/company/me/jobs` — every job post the caller published, newest first, with its status, its applications and the median score of its applicants, `null` with `insufficientData` below `lynq.analytics.standing.min-applicants` (5). Only `COMPANY` users, and always the caller. Computed on request over the read model.

---

## Cache

The analytics are computed on request over the read model, and Redis keeps each answer for an hour (`CacheConfig`, a `RedisCacheManager` behind `@EnableCaching`). There is one cache per endpoint, declared in `AnalyticsCaches`; a cache not declared there does not exist, so a misspelt name fails instead of creating a cache without its TTL.

| Cache          | Endpoint                                  | Key                |
| -------------- | ----------------------------------------- | ------------------ |
| `time-to-fill` | `GET /dmz/analytics/job/{jobId}/time-to-fill` | `jobId:userId` |
| `salary`       | `GET /dmz/analytics/job/{jobId}/salary`   | `jobId`            |
| `standing`     | `GET /dmz/analytics/job/{jobId}/standing` | `jobId:userId`     |
| `benchmark`    | `GET /dmz/analytics/candidate/me/benchmark` | `userId`         |
| `market`       | `GET /dmz/analytics/market`               | `currency`         |
| `company-jobs` | `GET /dmz/analytics/company/me/jobs`      | `userId`           |

The standing is keyed by the candidate too: it holds the caller's own rank and score, and one candidate must never be served another's. The benchmark and the company job posts are keyed by the caller for the same reason. The time to fill is keyed by the caller because its ownership check runs inside the cached method: keyed by the job post alone, the owner's answer would be served to any company that asked after. The salary is the same for every caller of a job post.

- **Keys** — `lynq-analytics::<cache>::<key>` in Redis, e.g. `lynq-analytics::standing::7777…:1111…`.
- **TTL** — `lynq.analytics.cache.ttl`, default `PT1H`, the same for every cache. Nothing is evicted when an event arrives: an answer can be up to an hour behind the read model.
- **After the snapshot** — each snapshot step that succeeds clears its cache whole, `market` after the daily aggregates and `benchmark` after the candidate benchmark, once its rows are committed. A failed step evicts nothing, so yesterday's answer stays.
- **Values** — JSON with the type of the value recorded, so a response record comes back as itself; only types under `com.lynq.analytics`, `java.lang`, `java.util` and `java.time` are read back. Nulls are not cached, and neither is an exception, so a `403` or `404` is never stored.
- **Redis down** — a cache error is logged and the call goes on uncached (`LoggingCacheErrorHandler`). Command and connect timeouts are 500 ms, so an outage costs half a second per request, not the driver's default minute.
- **Writes** — immediate: a request waits for Redis to confirm the entry. Spring Data Redis 4 writes in the background by default with Lettuce, which lets an eviction overtake the write it follows and hides write failures from the error handler; waiting costs under a millisecond next to computing a median.
- **Metrics** — hits, misses, puts and evictions per cache, as `cache.gets{cache,result}`, `cache.puts` and `cache.evictions` on `/actuator/prometheus`. They are what tells whether the hour is right.

Each endpoint's service method declares `@Cacheable(cacheNames = AnalyticsCaches.…, key = …)` with the key above; the time to fill (`TimeToFillService`), the standing (`StandingService`) and the salary (`SalaryService`) do.

---

## Running locally

**Prerequisites**

- JDK 21
- Maven 3.9+
- A reachable MySQL 9 with the `lynq_analytics_db` database, a running `lynq-iam`, Redis, and LocalStack with the domain events queue (`docker compose up localstack redis`)
- A running `lynq-app-backend` to take the [daily snapshot](#daily-snapshot); the rest of the service works without it

The default `application.yaml` targets `localhost:3306` (MySQL, `root` / `federico`), `lynq-iam` at `http://localhost:8080/lynq-iam`, `lynq-app-backend` at `http://localhost:8082/lynq-backend-app` with the local internal token, SQS at `http://localhost:4566` and Redis at `localhost:6379` (`root` / `password`, the compose defaults).

```bash
mvn clean package
java -jar target/lynq-analytics.jar
```

Liquibase applies the changesets under `changelog/ddl` on startup.

Service URLs (default profile):
- API: `http://localhost:8091/lynq-analytics`
- Swagger UI: `http://localhost:8091/lynq-analytics/swagger-ui.html`
- Actuator / Prometheus: `http://localhost:8092/actuator`

**Tests** (Testcontainers spins up MySQL, LocalStack with SNS and SQS, Redis, and a MockServer for `lynq-iam`; Docker must be running):

```bash
mvn test
```

---

## Configuration

- **`application.yaml`** (default) — local development; hard-coded credentials and ports (`8091`/`8092`), Swagger enabled.
- **`application-production.yaml`** — env-var driven, ports `8080`/`8081`, Swagger disabled. Activate with `SPRING_PROFILES_ACTIVE=production`.

| Variable       | Used by                            | Notes |
| -------------- | ---------------------------------- | ----- |
| `DB_URL`       | `spring.datasource.url` (JDBC URL) | |
| `DB_USERNAME`  | MySQL user                         | |
| `DB_PASSWORD`  | MySQL password                     | |
| `LYNQ_IAM_URL` | `lynq.iam.url` (Feign client)      | default `http://lynq-iam:8080/lynq-iam` |
| `LYNQ_BACKEND_URL` | `lynq.backend.url` (Feign client) | default `http://lynq-app-backend:8080/lynq-backend-app` |
| `LYNQ_INTERNAL_TOKEN` | `lynq.internal.token`          | shared secret: checked on `/internal/**` and presented to `lynq-app-backend`; empty refuses every internal call |
| `LYNQ_ANALYTICS_EVENTS_QUEUE` | `lynq.analytics.events.queue` | default `lynq-analytics-events` |
| `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | AWS SDK default chains | unset in EKS when the pod role provides them |
| `SPRING_CLOUD_AWS_SQS_ENDPOINT` | SQS endpoint override | only against LocalStack, e.g. `http://localstack:4566` |
| `REDIS_ADDRESS`, `REDIS_PORT` | `spring.data.redis.host` / `port` | |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | Redis ACL user | |

---

## Observability

- **Logs** — Log4j2 (`log4j2-spring.xml`). Every entry carries the `requestId` MDC key set by `RequestUuidFilter`.
- **Audit logs** — methods annotated with `@AuditLog` are wrapped by `LogAspect`, which logs entry/exit and sanitized arguments; `password`, `newPassword`, `refreshToken` and `accessToken` are masked.
- **Health** — `/actuator/health` with `liveness`/`readiness` probes, on the management port (`8092` default / `8081` prod).
- **Metrics** — `/actuator/prometheus`.

---

## Project layout

```
lynq-analytics/
├── Dockerfile
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/lynq/analytics/
    │   │   ├── LynqAnalyticsApplication.java
    │   │   ├── aspect/          # @AuditLog + LogAspect
    │   │   ├── cache/           # AnalyticsCaches: one cache per endpoint
    │   │   ├── client/          # LynqIamClient and LynqBackendClient (Feign), their requests and responses
    │   │   ├── config/          # AppConfig, FilterConfig, OpenApiConfig, SecurityConfig, SimilarityConfig, StandingConfig, SalaryConfig, CacheConfig, SnapshotConfig and their properties
    │   │   ├── controller/      # AnalyticsController, InternalSnapshotController and their impls, ControllerExceptionHandler, response envelope
    │   │   ├── enums/           # JobStatus
    │   │   ├── exceptions/      # BadRequest, Conflict, Forbidden, NotFound, InvalidDomainEvent, UnknownJobPost
    │   │   ├── filter/          # RequestUuid, InternalToken, AuthHeaderExistence, IamAuthentication, PublicPaths
    │   │   ├── listener/        # DomainEventListener (SQS), the envelope and the event payloads
    │   │   ├── model/           # JPA entities
    │   │   ├── repository/      # Spring Data repositories
    │   │   ├── security/        # LynqUserPrincipal, Role, @HasRole
    │   │   ├── similarity/      # TagSimilarity, its two metrics, TagWeights and the match results
    │   │   ├── stats/           # Distribution, PercentileRank, Folding; Standing; SalaryDistribution and SalaryInsights; MarketFit, CandidateBenchmark, Market and CompanyJobs
    │   │   └── service/         # DomainEventService and the projectors; TagFrequencyService; SimilarityService; StandingService; SalaryService; DailySnapshotService, DailyAggregatesService, CandidateBenchmarkService and BatchScorer; the benchmark, market and company job posts queries
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-production.yaml
    │       ├── changelog/       # Liquibase, lynq_analytics_db
    │       └── log4j2-spring.xml
    └── test/
```
