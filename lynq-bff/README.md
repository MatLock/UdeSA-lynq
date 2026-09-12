# lynq-bff

The backend-for-frontend gateway: the single entry point from the browser into the Lynq platform.

It does five things.

1. **Verifies the access token's signature.** lynq-iam mints access tokens signed with an HMAC-SHA
   key derived from a shared secret, so the same secret is enough to check a token's integrity —
   no call to lynq-iam needed. A request whose signature does not check out never leaves this
   service.
2. **Forwards the verified caller id.** The `sub` claim of the token it just verified goes
   downstream as the `user-id` header, replacing anything the client sent. lynq-ml and
   lynq-file-storage read that header as the caller's identity, so it is the one thing the gateway
   must not take on trust from the browser.
3. **Relays a request that one service can answer.** Method, path, query string, remaining headers,
   request body, status code and response body all cross unchanged. Relaying deliberately has no
   request or response model: re-serializing what merely passes through would be a second copy of
   three APIs to keep in sync.
4. **Relays the auth calls to lynq-iam.** Registration, both logins, the token refresh, the
   password update and the two pre-registration availability checks cross from here, under the same
   paths, so the browser has a single origin and lynq-iam needs no public route of its own. These
   are the routes where there is no verified caller yet — that is the point of them — so they are
   the one opening in the rule above, listed as exact paths in `PublicPaths` rather than as an
   `/auth` prefix. lynq-iam keeps checking every credential it is handed; the gateway adds nothing.
5. **Orchestrates the flows no single service can answer.** Where a screen needs several services
   in a fixed order — with a rollback when a step in the middle fails — that sequence lives here,
   behind one endpoint, and the browser never learns it. This is the part that makes the gateway a
   BFF rather than a proxy: see [Flows the gateway owns](#flows-the-gateway-owns). Downstream
   services stay unaware of each other, and none of them orchestrates another.

Everything behind it — lynq-app-backend, lynq-ml, lynq-file-storage — exposes its API under a
`/dmz` prefix and is reached only through here. That is why none of them checks the token's
signature for itself.

lynq-iam is the exception in both directions: it is not behind a `/dmz` prefix and it validates
every credential itself, because it is the service that mints them. It has no Ingress either — the
gateway reaches it inside the cluster, and so does lynq-app-backend, which resolves the caller
against `/auth/user-info` on every request.

---

## Routing

Routing is **by resource, not by service**. The path a caller writes is the path the owning service
sees below its prefix — `/dmz` for the three DMZ services, `/auth` for lynq-iam; which service that
is never appears in the URL.

| Gateway path                                              | Owner              |
| --------------------------------------------------------- | ------------------ |
| `/lynq-bff/user/**`, `/lynq-bff/company/**`, `/lynq-bff/job/**` | lynq-app-backend   |
| `/lynq-bff/files/**`                                      | lynq-file-storage  |
| `/lynq-bff/skill-enhance`, `/lynq-bff/translate`, `/lynq-bff/detect-language` | lynq-ml |
| `/lynq-bff/auth/register`, `/lynq-bff/auth/login/username`, `/lynq-bff/auth/login/email`, `/lynq-bff/auth/refresh`, `/lynq-bff/auth/update-password`, `/lynq-bff/auth/check-username`, `/lynq-bff/auth/check-email` | lynq-iam |

For example:

```
GET  /lynq-bff/user/generate-upload-image?file-name=avatar.png
  -> GET  /lynq-backend-app/dmz/user/generate-upload-image?file-name=avatar.png

POST /lynq-bff/skill-enhance
  -> POST /lynq-ml/dmz/skill-enhance

POST /lynq-bff/files/upload-url
  -> POST /lynq-file-storage/dmz/files/upload-url

POST /lynq-bff/auth/login/email
  -> POST /lynq-iam/auth/login/email
```

Nothing is rewritten: the gateway only picks who to talk to. That keeps the topology out of the URL,
so a resource can move between services without every caller having to change.

The mappings are an **allowlist**. A downstream endpoint not named in `DmzProxyControllerImpl` — or,
for the auth routes, in `IamAuthProxyControllerImpl` — is unreachable from the browser and answers
`404`, so a new endpoint is closed by default: the right way round for a gateway. The price is that
a genuinely new top-level resource has to be added here too. The auth mappings are stricter still:
exact paths, one exact verb each, so `GET /auth/login/email` is a `405` rather than a relay.

### What is not routed

- **lynq-iam's `/auth/validate` and `/auth/user-info`.** No browser flow calls them:
  lynq-app-backend resolves the caller against `/auth/user-info` from inside the cluster, and the
  frontend learns whether a token is still good from the answer to the request it just made. They
  are not in the allowlist, so they are a plain `404` here. Every other route lynq-iam grows is
  closed the same way until it is named — the auth relay is an allowlist of exact paths, one exact
  verb each, not an `/auth` prefix.
- **lynq-ml's `/health`.** It sits outside the DMZ so infra probes can reach it without a token.
- **lynq-ml's evaluations** — `upskilling_suggestion` and `candidate-explanation`. Their payload is
  a job post plus a candidate, assembled from lynq-app-backend's database once it has checked the
  caller may see them. The browser does not have that payload and must not be trusted to supply one,
  so these are reached through `/lynq-bff/job/{jobId}/…` instead. `403` here.
- **lynq-ml's URL-taking endpoints** — `parse-resume` and `resume-template-creation`. They hand a
  caller-supplied URL (`preSignedUrl`, `profile_url`, `put_resume_url`) straight to
  `urllib.request.urlopen` server-side, with no check on the scheme or the host. That is safe while
  the URLs come from lynq-file-storage and never from a browser; relaying them from here would make
  the gateway an SSRF vector. `403` until lynq-ml validates the URLs it is given.
  `resume-template-creation` is instead **driven** by `POST /lynq-bff/resume/preview` below, which
  signs both URLs itself one step earlier in the same flow — so the browser never supplies one.

`skill-enhance` **is** relayed: its payload is the job draft the user is typing into the form, so
lynq-backend has nothing to add to it and the gateway sends it straight on.

## Flows the gateway owns

A flow is an endpoint of **this** service: it has a request model, a response model, and a sequence
of downstream calls it is responsible for finishing or undoing. It exists when the answer a screen
needs cannot come from one service, and it lives here rather than in lynq-app-backend so that no
downstream service has to know another one exists.

| Gateway path                                    | What it composes                                  |
| ----------------------------------------------- | ------------------------------------------------- |
| `POST /lynq-bff/resume/preview`                 | lynq-app-backend + lynq-file-storage + lynq-ml    |
| `DELETE /lynq-bff/resume/preview/{fileId}`      | lynq-file-storage                                 |
| `POST /lynq-bff/resume/document/{fileId}/import`| lynq-app-backend + lynq-file-storage + lynq-ml    |
| `DELETE /lynq-bff/resume/{resumeId}`            | lynq-app-backend + lynq-file-storage              |

### Resume deletion

A saved resume lives in two services: the row in lynq-app-backend and the PDF in
lynq-file-storage. Neither can see the other, so deleting one is a composition.

```
DELETE /lynq-bff/resume/{resumeId}
  1. DELETE /dmz/user/resume/{resumeId}   → the row; answers the PDF's fileId
  2. DELETE /dmz/files/{fileId}           → the PDF
  → 204
```

The order is the point. The row goes first: it is what the candidate sees, and the only step that
can legitimately fail (a resume that is not theirs answers 404, which surfaces as a 502 here).
Dropping the PDF afterwards is **best effort** — once the row is gone the file is unreachable from
the product, so a storage failure leaves an orphan in the bucket and a warning in the log, rather
than an error for something the candidate correctly saw happen.

### Resume preview

The candidate has filled in the resume wizard and picked a template. Before the resume becomes
theirs, they get to look at the actual PDF that would be stored — so the PDF has to exist before
anything is persisted.

```
POST /lynq-bff/resume/preview   { resume, template }

  1. GET    lynq-app-backend  /dmz/user                     who is calling, and their avatar URL
  2. POST   lynq-file-storage /dmz/files/upload-url          register the PDF, get a signed PUT
  3. POST   lynq-ml           /dmz/resume-template-creation  render the template, PUT the PDF
  4. POST   lynq-file-storage /dmz/files/{fileId}/confirm    mark it available
  5. GET    lynq-file-storage /dmz/files/{fileId}/download-url  sign a URL to read it

  -> 201 { "success": true, "data": { "fileId": "…", "pdfUrl": "…" } }
```

Three things this flow is responsible for, and a proxy could not be:

- **The URLs lynq-ml fetches are signed inside the flow** (step 2 for the PUT, step 1 for the
  avatar). That is what makes calling `resume-template-creation` safe here while relaying it from a
  browser stays refused.
- **Nothing is persisted.** The `fileId` goes back to the browser, which either sends it to
  lynq-app-backend's `POST /user/resume` to keep the document, or back to
  `DELETE /lynq-bff/resume/preview/{fileId}` to throw it away — that second call is what the
  wizard's "back" button does.
- **A half-done flow cleans up after itself.** If step 3 or 4 fails, the file registered in step 2
  is deleted before the `502` is returned, so a failed preview never leaves a metadata row with no
  object behind it.

`403` when the caller is not a `CANDIDATE`, enforced by `@HasRole(Role.CANDIDATE)` on
`ResumeControllerImpl` against the `roles` claim of the verified token.

### Resume import

The other way to get a resume onto Lynq: the candidate uploads a PDF or Word file instead of
filling in the wizard. The bytes go straight from the browser to the bucket through a pre-signed
URL — they never cross our services — and what the browser cannot do is everything after that.

```
GET  /lynq-bff/user/generate-upload-resume?file-name=cv.pdf   (relayed, not a flow)
PUT  <pre-signed S3 URL>                                      (browser -> bucket)

POST /lynq-bff/resume/document/{fileId}/import?language=es

  1. POST   lynq-app-backend  /dmz/user/confirm-upload-resume  the document is really there
  2. GET    lynq-file-storage /dmz/files/{fileId}/download-url  sign a URL to read it
  3. POST   lynq-ml           /dmz/parse-resume                 read it into resume JSON
  4. POST   lynq-ml           /dmz/detect-language              which language is it written in
  5. POST   lynq-ml           /dmz/resume/skill-extraction      generalize its skills into tags
  6. POST   lynq-app-backend  /dmz/user/resume                  store it against the candidate

  -> 201 { "success": true, "data": { …the stored resume… } }
```

- **Before this flow existed the upload path dead-ended.** The browser asked for the URL, PUT the
  file and stopped: nothing ever confirmed the document and nothing ever called `parse-resume`, so
  no resume was created from an upload. The frontend's "we are processing your resume" panel was
  waiting for something that never happened.
- **The language is classified, not assumed.** A candidate using the app in Spanish may well upload
  a resume written in English, and the stored language is what later decides which of their resumes
  is shown. `?language=` is only the fallback for a resume with no prose to classify. The text sent
  to lynq-ml is the resume's own prose (summary, headline, the descriptions of its sections) —
  `ParsedResume` is the one place the gateway looks inside a resume, and it skips the skill lists and
  the English JSON keys that would drag the classification towards English.
- **The skills are parsed, the capabilities are derived.** Step 3 only recovers what the candidate
  literally wrote down, and the app-backend copies those into their skill list on its own. The
  generalized similarity tags — "Asynchronous Messaging" rather than Kafka or RabbitMQ — have no
  other source, and they are half of the LyNQ score: without them an imported resume can only match
  a posting on exact skill names, scoring below the same resume typed into the wizard, where the
  candidate presses "generate skills" and gets them. Step 5 closes that gap. It is best-effort — the
  second LLM round-trip on this path, so a failure stores the resume without tags instead of losing
  an otherwise valid import; the tags can be derived again from the stored resume, the uploaded
  document cannot.
- **The document does not outlive a failed import.** Once step 1 has confirmed it, any later failure
  deletes it before the `502` is returned — the candidate still has the original file, so a retry
  starts clean rather than accumulating documents no resume points at.

`403` when the caller is not a `CANDIDATE`, enforced by `@HasRole(Role.CANDIDATE)` on
`ResumeControllerImpl` — the roles come from the verified token, so no round-trip to
lynq-app-backend is needed to know who is calling.

### Identity headers

`user-id` is always overwritten with the verified token's subject, and `company-id` is always
dropped — the gateway has no way to establish one, and the endpoints that need it go through
lynq-backend. Both are stripped from the incoming request regardless of casing before the trusted
`user-id` is added, so a client-supplied value can never survive the hop.

Downstream, `user-id` is what lynq-file-storage records as a file's owner, and what it checks before
letting anyone confirm or delete that file.

---

## Filter chain

| Order | Filter                     | Applies to                     | Behaviour                                                              |
| ----- | -------------------------- | ------------------------------ | ---------------------------------------------------------------------- |
| -1    | `CorsFilter`               | all routes                     | Answers browser preflights. Runs first on purpose: a preflight carries neither header below, so the filters after it would reject it and the real request would never be sent. |
| 0     | `RequestUuidFilter`        | all routes except Swagger      | 403 if `lynq-request-uuid` is missing/blank; echoes it back and binds it to the logging context (MDC). |
| 1     | `AuthHeaderExistenceFilter`| all routes except Swagger and the anonymous auth routes | 401 if the `Authorization` header is missing.                          |
| 2     | `JwtSignatureFilter`       | as above, and also not `/auth/refresh` | 401 if the token's signature does not verify, it has expired, or it carries no subject. Publishes the verified subject for the proxy to forward as `user-id`, and loads it — with the token's `roles` claim as authorities — into the `SecurityContext`. |
| 3     | `RelayRoleFilter`          | as `JwtSignatureFilter`        | 403 for the relayed routes whose role is structural (`RelayRoleRules`). **Default allow**: a route no rule covers is relayed untouched. |

`PublicPaths` holds those two exceptions, and the difference between them matters:

- **Anonymous** — `/auth/register`, `/auth/login/username`, `/auth/login/email`,
  `/auth/check-username`, `/auth/check-email`. No `Authorization` header at all: the first three
  carry a password in the body, the checks run before an account exists.
- **Signature-exempt but not anonymous** — `/auth/refresh`. Its bearer credential is the opaque,
  Redis-backed refresh token, so the header has to be there (401 without it), but it is not a JWT
  and no secret here could check it. Only lynq-iam can, so it crosses untouched.
- `/auth/update-password` is in neither list: it carries an access token, so the signature is
  verified here first and lynq-iam validates it again before rotating the password.

`lynq-request-uuid` is still required on every one of them — the correlation id is what ties a login
across the two services in the logs.

Authorization is method security on top of those authorities (`SecurityConfig`,
`@EnableMethodSecurity`): `@HasRole(Role.CANDIDATE)` on `ResumeControllerImpl` is what makes the whole
resume flow candidate-only, read from the token instead of asking lynq-app-backend who is calling.
Spring Security's own filter chain is `permitAll` and stateless — the filters above are what enforce
authentication.

### Roles on the relayed routes

The flows the gateway owns are authorized here, but the relayed routes belong to the service that
owns the data: lynq-app-backend carries its own `@HasRole` on every endpoint that belongs to one kind
of user, and it is the only place that can also enforce ownership ("this resume is yours"). So the
role check downstream is the one that decides.

`RelayRoleFilter` is an **optimization, not the enforcement point**: it bounces the requests it knows
are doomed before they cost a hop to lynq-app-backend and another from there to lynq-iam. Everything
it does not recognize is relayed, so a rule missing from `RelayRoleRules` can never open a hole nor
invent a `403` — the worst case is the request travelling one service further to get the same answer.

| Role | Relayed routes |
| ---- | -------------- |
| `R_COMPANY` | `POST /job`, `GET /job/mine`, `POST /company` |
| `R_CANDIDATE` | `POST /job/{jobId}/apply`, `GET /job/{jobId}/upskilling-suggestion`, `GET /user/generate-upload-resume`, `POST /user/confirm-upload-resume`, `GET|POST /user/resume`, `GET /user/resume/languages`, `PUT /user/resume/{resumeId}/alias`, `DELETE /user/resume/{resumeId}`, `GET /user/application`, `GET /user/upskilling-suggestion/{jobPostId}` |

The rules are exact method + path templates on purpose, never prefixes. A prefix would bounce
whatever appears under it later, and the gateway is the one place that cannot tell an endpoint it has
never heard of from one that is open by design — the downstream service can. Adding a role-scoped
endpoint there without adding a rule here is a missed optimization; adding a prefix rule here is a
bug waiting for the next endpoint.

---

## Status codes

Any status a DMZ service returns — 200, 201, 404, 409, 500 — is passed straight back. A 404 from a
reachable service is an answer, not a gateway failure. The gateway only produces a status of its own
in these cases:

| Status | When                                                                    |
| ------ | ----------------------------------------------------------------------- |
| 400    | A flow's request is missing something it needs (e.g. a preview with no resume or no template). |
| 401    | Missing `Authorization` header — including on `/auth/refresh`, whose credential is not verified here but must be present — or an invalid/expired token signature. A correctly signed token with no `sub` claim is rejected here too: the caller id is forwarded downstream, so an anonymous one is no good. |
| 403    | Missing `lynq-request-uuid` header, a lynq-ml endpoint the gateway does not relay (see above), or a flow the caller's role may not run (`@HasRole`). |
| 404    | A resource the gateway does not route — the mappings are an allowlist.  |
| 405    | An HTTP verb the gateway does not relay (only GET/POST/PUT/PATCH/DELETE).|
| 502    | A relayed service could not be reached at all — a DMZ one or lynq-iam — or a step of a flow failed — in which case the flow has already undone what it had done. |

---

## Configuration

| Property / env var                        | Default (local)                              | Purpose                                       |
| ----------------------------------------- | -------------------------------------------- | --------------------------------------------- |
| `JWT_SECRET`                              | the shared development secret                | Must be **the same secret lynq-iam signs with**. |
| `LYNQ_IAM_URL`                            | `http://localhost:8080/lynq-iam`             | lynq-iam base URL, for the relayed auth routes. |
| `LYNQ_BACKEND_URL`                        | `http://localhost:8082/lynq-backend-app`     | lynq-app-backend base URL, context path included. |
| `LYNQ_ML_URL`                             | `http://localhost:8084/lynq-ml`              | lynq-ml base URL.                             |
| `LYNQ_FILE_STORAGE_URL`                   | `http://localhost:8085/lynq-file-storage`    | lynq-file-storage base URL.                   |

The service listens on `8087` (management/actuator on `8088`) with context path `/lynq-bff`. Under
the `production` profile those become `8080` and `8081`, matching the other Java services.

---

## Implementation notes

- **Two kinds of Feign client, for the two kinds of work.** The relay clients (`*DmzClient`) extend
  `DmzClient`, which declares one method per verb with a catch-all path variable, a query map, a
  header map and a `byte[]` body. Their return type is `feign.Response`, which Feign hands back
  undecoded — so a non-2xx answer arrives as data instead of a `FeignException`, which is exactly
  what relaying wants. The flow clients (`LynqBackendClient`, `LynqFileStorageClient`,
  `LynqMlClient`) are typed, one method per endpoint the gateway actually calls, and a non-2xx
  answer raises — a flow needs to know a step failed so it can roll back.
- **The auth relay is its own client, not a `DmzClient` sibling.** `LynqIamAuthClient` repeats the
  five-argument shape against `/auth/{path}` instead of inheriting it: lynq-iam is not behind the
  `/dmz` prefix, and Feign allows a client interface a single level of inheritance, so
  `LynqIamAuthClient extends DmzClient`-style reuse would break the relay clients that already do.
  It declares only the verbs the relayed routes use (GET, POST, PATCH). The copying both relays do —
  body, query string, headers, response — lives once in `RelayExchange`; what stays in each service
  is who to call, and whether the verified caller id is injected. The DMZ relay injects it; the auth
  relay deliberately does not, because lynq-iam reads the credential rather than a header.
- **Feign timeouts are per client name, so the relay clients need their own.** The lynq-ml
  endpoints are LLM generations either way, whether a flow calls them or the browser's request is
  relayed to them — but `spring.cloud.openfeign.client.config.lynqMl` only covers the typed client.
  `lynqMlDmz` carries the same 310s read timeout, or the gateway answers `502` at 60s while lynq-ml
  is still writing a perfectly good answer.
- **Apache HttpClient 5, explicitly.** Feign's default client is backed by `HttpURLConnection`,
  which throws on PATCH — and lynq-app-backend exposes PATCH on profiles, companies and job posts.
  `FeignConfig` wires in `ApacheHttp5Client` rather than leaving it to classpath detection.
- **A pass-through encoder, scoped to the relay clients.** Feign's default `SpringEncoder` would run
  the body through Spring's message converters; for a relay the bytes are already encoded by whoever
  called us, so `DmzPassThroughFeignConfig` writes them untouched. That config is attached per
  client rather than registered as a global `@Bean`: globally it would also apply to the typed flow
  clients and silently drop the objects they send.
- **Hop-by-hop headers are dropped**, in both directions (RFC 9110 §7.6.1), along with
  `Content-Length` and `Host`, which the next hop must compute for itself. The downstream
  `lynq-request-uuid` is dropped too, because this service already echoes the caller's own.

---

## Build & run

```bash
mvn clean verify        # build + tests + JaCoCo report
mvn spring-boot:run     # run locally on :8087
```

Swagger UI is available at `http://localhost:8087/lynq-bff/swagger-ui.html` (disabled under the
`production` profile).
