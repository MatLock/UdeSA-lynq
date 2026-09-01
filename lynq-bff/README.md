# lynq-bff

The backend-for-frontend gateway: the single entry point from the browser into the Lynq platform.

It does three things, and deliberately nothing else.

1. **Verifies the access token's signature.** lynq-iam mints access tokens signed with an HMAC-SHA
   key derived from a shared secret, so the same secret is enough to check a token's integrity —
   no call to lynq-iam needed. A request whose signature does not check out never leaves this
   service.
2. **Forwards the verified caller id.** The `sub` claim of the token it just verified goes
   downstream as the `user-id` header, replacing anything the client sent. lynq-ml and
   lynq-file-storage read that header as the caller's identity, so it is the one thing the gateway
   must not take on trust from the browser.
3. **Relays the rest of the request.** Method, path, query string, remaining headers, request body,
   status code and response body all cross unchanged. There is no request or response model in this
   codebase: a gateway that re-serializes what passes through it is a second copy of three APIs to
   keep in sync.

Everything behind it — lynq-app-backend, lynq-ml, lynq-file-storage — exposes its API under a
`/dmz` prefix and is reached only through here. That is why none of them checks the token's
signature for itself.

---

## Routing

Routing is **by resource, not by service**. The path a caller writes is the path the owning service
sees below its `/dmz` prefix; which service that is never appears in the URL.

| Gateway path                                              | Owner              |
| --------------------------------------------------------- | ------------------ |
| `/lynq-bff/user/**`, `/lynq-bff/company/**`, `/lynq-bff/job/**` | lynq-app-backend   |
| `/lynq-bff/files/**`                                      | lynq-file-storage  |
| `/lynq-bff/skill-enhance`, `/lynq-bff/translate`, `/lynq-bff/detect-language` | lynq-ml |

For example:

```
GET  /lynq-bff/user/generate-upload-image?file-name=avatar.png
  -> GET  /lynq-backend-app/dmz/user/generate-upload-image?file-name=avatar.png

POST /lynq-bff/skill-enhance
  -> POST /lynq-ml/dmz/skill-enhance

POST /lynq-bff/files/upload-url
  -> POST /lynq-file-storage/dmz/files/upload-url
```

Nothing is rewritten: the gateway only picks who to talk to. That keeps the topology out of the URL,
so a resource can move between services without every caller having to change.

The mappings are an **allowlist**. A downstream endpoint not named in `DmzProxyControllerImpl` is
unreachable from the browser and answers `404`, so a new endpoint is closed by default — the right
way round for a gateway. The price is that a genuinely new top-level resource has to be added here
too.

### What is not routed

- **lynq-iam.** Sign-in, registration and token refresh are public by definition and there is
  nothing for this service to verify yet, so the frontend keeps talking to lynq-iam directly.
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

`skill-enhance` **is** relayed: its payload is the job draft the user is typing into the form, so
lynq-backend has nothing to add to it and the gateway sends it straight on.

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
| 1     | `AuthHeaderExistenceFilter`| all routes except Swagger      | 401 if the `Authorization` header is missing.                          |
| 2     | `JwtSignatureFilter`       | all routes except Swagger      | 401 if the token's signature does not verify, it has expired, or it carries no subject. Publishes the verified subject for the proxy to forward as `user-id`. |

---

## Status codes

Any status a DMZ service returns — 200, 201, 404, 409, 500 — is passed straight back. A 404 from a
reachable service is an answer, not a gateway failure. The gateway only produces a status of its own
in these cases:

| Status | When                                                                    |
| ------ | ----------------------------------------------------------------------- |
| 401    | Missing `Authorization` header, or an invalid/expired token signature. A correctly signed token with no `sub` claim is rejected here too: the caller id is forwarded downstream, so an anonymous one is no good. |
| 403    | Missing `lynq-request-uuid` header, or a lynq-ml endpoint the gateway does not relay (see above). |
| 404    | A resource the gateway does not route — the mappings are an allowlist.  |
| 405    | An HTTP verb the gateway does not relay (only GET/POST/PUT/PATCH/DELETE).|
| 502    | The DMZ service could not be reached at all.                            |

---

## Configuration

| Property / env var                        | Default (local)                              | Purpose                                       |
| ----------------------------------------- | -------------------------------------------- | --------------------------------------------- |
| `JWT_SECRET`                              | the shared development secret                | Must be **the same secret lynq-iam signs with**. |
| `LYNQ_BACKEND_URL`                        | `http://localhost:8082/lynq-backend-app`     | lynq-app-backend base URL, context path included. |
| `LYNQ_ML_URL`                             | `http://localhost:8084/lynq-ml`              | lynq-ml base URL.                             |
| `LYNQ_FILE_STORAGE_URL`                   | `http://localhost:8085/lynq-file-storage`    | lynq-file-storage base URL.                   |

The service listens on `8087` (management/actuator on `8088`) with context path `/lynq-bff`. Under
the `production` profile those become `8080` and `8081`, matching the other Java services.

---

## Implementation notes

- **Feign, untyped.** The three `@FeignClient` interfaces all extend `DmzClient`, which declares one
  method per verb with a catch-all path variable, a query map, a header map and a `byte[]` body. The
  return type is `feign.Response`, which Feign hands back undecoded — so a non-2xx answer arrives as
  data instead of a `FeignException`, which is exactly what a gateway wants.
- **Apache HttpClient 5, explicitly.** Feign's default client is backed by `HttpURLConnection`,
  which throws on PATCH — and lynq-app-backend exposes PATCH on profiles, companies and job posts.
  `FeignConfig` wires in `ApacheHttp5Client` rather than leaving it to classpath detection.
- **A pass-through encoder.** Feign's default `SpringEncoder` would run the body through Spring's
  message converters. The bytes are already encoded by whoever called us, so `FeignConfig` replaces
  it with an encoder that writes them untouched.
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
