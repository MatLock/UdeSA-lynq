# lynq-app-backend

[![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml)
[![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/jacoco-app-backend.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml)
[![Version](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/version.svg)](https://github.com/MatLock/UdeSA-lynq/releases)

Core application service for the Lynq platform. It owns the product domain — **user profiles**, **companies**, and **job posts** — and exposes the **job feed** that mixes Lynq-native postings with externally scraped ones, ranked per candidate with a **LyNQ match score**. It also brokers **profile/company image uploads** through the [`lynq-file-storage`](../lynq-file-storage) service and calls `lynq-ml` for the two **candidate evaluations** whose payload it assembles from its own database.

Authentication is **not** handled here. Every protected request is validated against the [`lynq-iam`](../lynq-iam) identity provider, and the resolved user identity is loaded into the security context for the duration of the request.

---

## Table of contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Request lifecycle](#request-lifecycle)
- [Core flows](#core-flows)
  - [Create a job post](#1-create-a-job-post)
  - [Search the job feed](#2-search-the-job-feed)
  - [Candidate evaluations via lynq-ml](#3-candidate-evaluations-via-lynq-ml)
  - [Image upload](#4-image-upload-pre-signed-urls)
- [Data model](#data-model)
- [API reference](#api-reference)
- [Sample requests](#sample-requests)
- [Running locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Configuration](#configuration)
- [Observability](#observability)
- [Project layout](#project-layout)

---

## Technologies

| Area              | Stack                                                                        |
| ----------------- | ---------------------------------------------------------------------------- |
| Language / JDK    | Java 21                                                                      |
| Framework         | Spring Boot 4.0.6 (Web, Data JPA, Actuator, AOP, Security, Validation)       |
| Web server        | Jetty (Tomcat excluded)                                                      |
| Persistence       | MySQL 9, Hibernate / Spring Data JPA, Liquibase migrations                   |
| Inter-service     | Spring Cloud OpenFeign — clients for `lynq-iam`, `lynq-ml` and `lynq-file-storage` |
| Object storage    | None here — delegated to `lynq-file-storage`, which owns the bucket and signs the URLs |
| IDs               | `java-uuid-generator` (time-ordered UUIDv7) for domain entities             |
| Validation        | Hibernate Validator, Bean Validation (Jakarta)                              |
| Docs              | springdoc-openapi (Swagger UI)                                              |
| Logging           | Log4j2 + SLF4J MDC for per-request correlation IDs; `@AuditLog` aspect       |
| Metrics           | Micrometer + Prometheus registry                                            |
| Build             | Maven (Spring Boot plugin), Dockerfile on `eclipse-temurin:21-jre-alpine`   |
| Tests             | JUnit Jupiter, Testcontainers (MockServer for `lynq-iam` / `lynq-ml` / `lynq-file-storage`), H2, JaCoCo |

---

## Architecture

```
                          ┌───────────────────────────┐
                          │       Client (HTTP)       │
                          └─────────────┬─────────────┘
                                        │  Authorization, lynq-request-uuid
                                        ▼
              ┌───────────────────────────────────────────────────┐
              │                  Servlet filters                  │
              │   0. RequestUuidFilter          (all routes)      │
              │   1. AuthHeaderExistenceFilter  (all routes)      │
              │   2. IamAuthenticationFilter    (all routes) ─────┼──► lynq-iam
              └─────────────┬─────────────────────────────────────┘   (user-info)
                            │  SecurityContext = LynqUserPrincipal
                            ▼
        ┌──────────────┬──────────────┬───────────────┬──────────────┐
        ┌──────────────┬──────────────┬───────────────┐
        │ UserCtrl     │ CompanyCtrl  │ JobCtrl        │
        │ /dmz/user    │ /dmz/company │ /dmz/job       │
        └──────┬───────┴──────┬───────┴───────┬────────┘
               ▼              ▼               ▼
        ┌────────────┐ ┌────────────┐ ┌────────────┐──► lynq-ml
        │UserService │ │CompanyServ.│ │ JobService │    (candidate-explanation,
        └─────┬──────┘ └─────┬──────┘ └─────┬──────┘     upskilling_suggestion)
              │              │              │
              ▼              ▼              ▼
        ┌───────────────────────────────────────┐  ┌────────────────────┐
        │        Spring Data JPA repositories    │  │ FileStorageService │
        │  users / companies / job_posts / ...   │  │  (file ids only)   │
        └───────────────────┬───────────────────┘  └─────────┬──────────┘
                            ▼                                ▼
                       ┌─────────┐                 ┌───────────────────┐
                       │ MySQL 9 │                 │ lynq-file-storage │
                       └─────────┘                 │  (owns the bucket)│
                                                   └───────────────────┘
```

**Layers**

- **Controller** (`controller/`) — thin HTTP layer. Each interface (e.g. `JobController`) carries OpenAPI annotations; the `*Impl` maps HTTP verbs to service calls and wraps responses in `GlobalRestResponse<T>`. The authenticated user is injected via `@AuthenticationPrincipal LynqUserPrincipal`.
- **Service** (`service/`) — business logic. `UserService`, `CompanyService`, and `JobService` own their aggregates; `FileStorageService` is the only door to `lynq-file-storage`, which owns every stored file (this service keeps just the file ids); `JobService` also calls `lynq-ml` for the candidate evaluations.
- **Client** (`client/`) — Feign clients for the three downstream services (`LynqIamClient`, `LynqMLClient`, `LynqFileStorageClient`) plus their request/response DTOs.
- **Filters** (`filter/`) — cross-cutting request handling registered via `FilterConfig` with explicit ordering. `PublicPaths` is the single whitelist consulted by the auth filters (only Swagger assets are public).
- **Security** (`security/`) — `LynqUserPrincipal` is the identity `lynq-iam` resolves from the access token, stored as the Spring Security principal. The token's signature is verified upstream by `lynq-bff`, not here.
- **Aspect** (`aspect/`) — the `@AuditLog` annotation + `LogAspect` produce structured entry/exit logs around annotated methods, masking sensitive fields.
- **Model / Repository** (`model/`, `repository/`) — JPA entities, Spring Data interfaces, and the `JobWithDetailsProjection` used by the feed query.
- **Exception handling** (`exceptions/`, `controller/handler/`) — domain exceptions mapped to consistent error responses by `ControllerExceptionHandler`.
- **Migrations** (`resources/changelog/`) — Liquibase changelogs run on startup.

---

## Request lifecycle

Every request passes through an ordered filter chain before reaching a controller:

| Order | Filter                       | Scope                        | Purpose                                                                                          |
| :---: | ---------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------ |
| 0     | `RequestUuidFilter`          | `/*`                         | Require the `lynq-request-uuid` header; bind it to SLF4J MDC (`requestId`) and echo it back on the response for cross-service log correlation. `403` if missing. |
| 1     | `AuthHeaderExistenceFilter`  | `/*` (Swagger paths exempt)  | `401` if the `Authorization` header is missing or blank.                                         |
| 2     | `IamAuthenticationFilter`    | `/*` (Swagger paths exempt)  | Call `lynq-iam` for the token's user info, then load a `LynqUserPrincipal` into the `SecurityContext`. `401` if IAM will not resolve the token, `503` if IAM is unreachable. |

> This service does **not** verify the access token's signature. Every request reaches it through
> [`lynq-bff`](../lynq-bff) — the single entry point into the DMZ — which validates the signature
> before proxying, which is also why this service's whole API sits behind the `/dmz` prefix.

Spring Security itself is configured **stateless** and `permitAll` (`SecurityConfig`) — the filter chain above, not Spring Security, is what enforces authentication. CORS is open (`*` origins) and CSRF/form-login/HTTP-basic are disabled. Only Swagger UI / OpenAPI asset paths are public (`PublicPaths`).

> The `lynq-request-uuid` header is forwarded on every downstream call to `lynq-iam` and `lynq-ml`, so a single logical request can be traced across all services by its UUID.

---

## Core flows

The diagrams below use Mermaid (rendered natively by GitHub).

### 1. Create a job post

Only users of type `COMPANY` linked to a company may post jobs.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as JobController
    participant Svc as JobService
    participant Sec as SecurityContext
    participant DB as MySQL

    C->>Ctrl: POST /dmz/job<br/>Authorization + lynq-request-uuid<br/>{title, description, workType, salary…, skills}
    Ctrl->>Svc: createJob(...)
    Svc->>Sec: resolve LynqUserPrincipal
    Svc->>DB: findById(userId)
    alt user is not COMPANY
        Svc-->>Ctrl: BadRequestException
        Ctrl-->>C: 400 "Only users of type COMPANY can create jobs"
    else COMPANY user
        Svc->>DB: findByOwner(user) → CompanyEntity
        Svc->>Svc: build JobPostEntity (UUIDv7 id, status OPEN, source)
        Svc->>Svc: attach distinct skills
        Svc->>DB: save job + skills
        Svc-->>Ctrl: JobPostEntity
        Ctrl-->>C: 201 Created (CreateJobRestResponse)
    end
```

### 2. Search the job feed

The feed is a paginated, filterable list of **OPEN** jobs that mixes Lynq-native (`LYNQ`) and externally scraped postings (`LINKEDIN`, `COMPUTRABAJO`, `BUMERAN`). External jobs have no poster, so company and poster are `LEFT JOIN`ed. For `CANDIDATE` users, each job is annotated with a **LyNQ score**.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as JobController
    participant Svc as JobService
    participant Repo as JobPostRepository
    participant FS as lynq-file-storage

    C->>Ctrl: GET /dmz/job?page=&size=&filterValue=
    Ctrl->>Svc: searchAvailableJobs(filter, pageable)
    Svc->>Repo: searchAvailableJobs(filterValue, pageable)
    Note over Repo: WHERE jobStatus = OPEN<br/>filter LIKE title/description/company/workType/skill<br/>ORDER BY createdOn DESC
    Repo-->>Svc: Page<JobWithDetailsProjection>
    Svc->>FS: POST /dmz/files/download-urls (every company + poster file id on the page)
    FS-->>Svc: { fileId: downloadUrl }
    loop each job
        Svc->>Svc: lynqScore = % of job skills the candidate has (CANDIDATE only)
    end
    Svc-->>Ctrl: PagedRestResponse<GetJobRestResponse>
    Ctrl-->>C: 200 OK
```

The **LyNQ score** is the percentage of a job's skills that the authenticated candidate already lists (case-insensitive, trimmed). It is `null` for `COMPANY` users, or when either the job or the user has no skills.

### 3. Candidate evaluations via lynq-ml

`JobService` calls `lynq-ml` for two evaluations: `candidate-explanation` (a hiring verdict for one
applicant) and `upskilling_suggestion` (what a candidate would need to learn for a job). Both build
their payload from this service's own database — the job post, the application and the candidate —
after checking that the caller may see them, which is exactly why they cannot be called from the
browser: nobody outside this service has that payload, and nobody outside it should be trusted to
supply one.

The **skill-enhance** endpoint used to be proxied here too. It is not any more: its payload is the
job draft the user is typing into the form, so this service had nothing to add to it. The browser
now reaches it straight through [`lynq-bff`](../lynq-bff) at `POST /lynq-bff/skill-enhance`.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as JobControllerImpl
    participant Svc as JobService
    participant DB as MySQL
    participant ML as lynq-ml

    C->>Ctrl: GET /dmz/job/{jobId}/candidate/{candidateId}/candidate-explanation
    Ctrl->>Svc: explainCandidate(...)
    Svc->>DB: load application → job post + candidate
    Svc->>Svc: caller must own the job post
    Svc->>ML: POST /dmz/candidate-explanation<br/>headers: lynq-request-uuid, user-id, company-id
    ML-->>Svc: { outcome, strengths, concerns }
    Svc-->>Ctrl: CandidateExplanationResponse
    Ctrl-->>C: 200 OK
```

### 4. Image upload (pre-signed URLs)

Uploads never pass through the backend, and neither does the bucket: **`lynq-file-storage` owns every stored file**. Asking for an upload URL registers the file there and persists only the returned **file id** (`lynq_file_storage_id`) on the entity; the client PUTs the bytes straight to the short-lived (15-minute) pre-signed URL and then confirms the upload so the file becomes readable. Re-uploading replaces the stored id and deletes the file it replaces. When reading, the backend exchanges the stored ids for pre-signed **GET** URLs — a whole page of results in a single batched call.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant BE as lynq-app-backend
    participant FS as lynq-file-storage
    participant S3 as Bucket

    C->>BE: GET /user/generate-upload-image?file-name=avatar.png
    BE->>FS: POST /dmz/files/upload-url { fileName }
    FS-->>BE: { fileId, uploadUrl }
    BE->>BE: persist fileId on the user (PENDING)
    opt replacing an image
        BE->>FS: DELETE /dmz/files/{previousFileId}
    end
    BE-->>C: { preSignedUrl, fileId }
    C->>S3: PUT bytes to preSignedUrl
    C->>BE: POST /user/confirm-upload-image?file-id=…
    BE->>FS: POST /dmz/files/{fileId}/confirm
    FS->>S3: HEAD object (must exist)
    FS-->>BE: AVAILABLE
    BE-->>C: 204 No Content
```

| Upload | Confirm |
| ------ | ------- |
| `GET /dmz/user/generate-upload-image?file-name=…`    | `POST /dmz/user/confirm-upload-image?file-id=…`    |
| `GET /dmz/company/generate-upload-image?file-name=…` | `POST /dmz/company/confirm-upload-image?file-id=…` |
| `GET /dmz/user/generate-upload-resume?file-name=…`   | `POST /dmz/user/confirm-upload-resume?file-id=…`   |

---

## Data model

Liquibase provisions the `lynq_backend_db` schema on startup (`resources/changelog/`).

| Table                  | Purpose                                                                                  |
| ---------------------- | ---------------------------------------------------------------------------------------- |
| `users`                | Profile of a Lynq user. **`id` equals the `lynq-iam` user id** (no local credentials). `type` ∈ {`CANDIDATE`, `COMPANY`}. `lynq_file_storage_id` points at the profile image held by `lynq-file-storage`. |
| `companies`            | Company profile, unique `name`, `owner_user_id` → `users`. `lynq_file_storage_id` points at the logo held by `lynq-file-storage`. |
| `job_posts`            | Job postings. `job_status` ∈ {`OPEN`, `CLOSE`}, `job_post_source` ∈ {`LYNQ`, `LINKEDIN`, `COMPUTRABAJO`, `BUMERAN`}, `work_type` ∈ {`REMOTE`, `IN_OFFICE`}. FKs to `users` (poster) and `companies` — both nullable for scraped jobs. |
| `job_post_skills`      | Skills required by a job (`job_id`, `skill`), unique per pair.                           |
| `user_skills`          | Skills a user has (`user_id`, `skill`), unique per pair — drives the LyNQ score.         |
| `user_resumes`         | Uploaded/generated résumés (`resume` JSON, `language`, `lynq_file_storage_id` → the PDF held by `lynq-file-storage`). |
| `user_application_job` | A user's application to a job (unique per `job_post_id` + `user_id`).                    |

Domain entity IDs are time-ordered UUIDv7 strings, except `users.id`, which is inherited from `lynq-iam`. All JPA associations use `FetchType.LAZY`.

---

## API reference

Base path: `/lynq-backend-app` (Spring `server.servlet.context-path`).
**Every** request must include the `lynq-request-uuid` header and a valid `Authorization: Bearer <access token>` (the access token issued by `lynq-iam`).

| Method | Path                            | Body / Params                                                        | Description                                              |
| ------ | ------------------------------- | -------------------------------------------------------------------- | -------------------------------------------------------- |
| GET    | `/dmz/user`                         | —                                                                    | Get the authenticated user's profile (+ pre-signed image URL). |
| POST   | `/dmz/user`                         | `{userType, fullName, currentPosition?, about?, githubUrl?, linkedinUrl?, birthDate}` | Create the profile for the authenticated user.           |
| PATCH  | `/dmz/user`                         | Any subset of profile fields                                         | Partially update the profile (non-null fields only).    |
| GET    | `/dmz/user/generate-upload-image`   | `?file-name=`                                                        | Register the profile image in `lynq-file-storage`; returns `{preSignedUrl, fileId}`. |
| POST   | `/dmz/user/confirm-upload-image`    | `?file-id=`                                                          | Mark the uploaded profile image available (204).         |
| GET    | `/dmz/user/generate-upload-resume`  | `?file-name=`                                                        | Register a résumé PDF; returns `{preSignedUrl, fileId}` (`CANDIDATE` only). |
| POST   | `/dmz/user/confirm-upload-resume`   | `?file-id=`                                                          | Mark the uploaded résumé available (204, `CANDIDATE` only). |
| POST   | `/dmz/company`                      | `{fullName, currentPosition, userAbout, birthDate, companyName, companyAbout, companySize?, …}` | Create the authenticated user as a `COMPANY` and its company. |
| GET    | `/dmz/company/generate-upload-image`| `?file-name=`                                                        | Register the company logo in `lynq-file-storage`; returns `{preSignedUrl, fileId}`. |
| POST   | `/dmz/company/confirm-upload-image` | `?file-id=`                                                          | Mark the uploaded logo available (204).                  |
| POST   | `/dmz/job`                          | `{title, description, workType, salaryRangeDown?, salaryRangeTop?, jobPostSource, skills?}` | Create a job post (`COMPANY` users only).                |
| GET    | `/dmz/job`                          | `?page=0&size=20&filterValue=`                                       | Paginated feed of OPEN jobs; free-text filter; LyNQ score per job for candidates. |

Responses are wrapped in `GlobalRestResponse<T>`:

```json
{ "success": true, "data": { ... } }
```

Paginated endpoints wrap the payload again in `PagedRestResponse<T>`:

```json
{ "success": true, "data": {
    "content": [ ... ], "page": 0, "size": 20,
    "totalElements": 137, "totalPages": 7,
    "hasNext": true, "hasPrevious": false
} }
```

Errors are wrapped in `ErrorRestResponse` (`{ success:false, data, reason }`). Mapping (`ControllerExceptionHandler`):

| Exception                       | HTTP status |
| ------------------------------- | ----------- |
| `BadRequestException`           | 400         |
| `ForbiddenException`            | 403         |
| `NotFoundException`             | 404         |
| `IllegalArgumentException`      | 409         |
| bean-validation failure         | 400 (`{ reason: "Invalid Fields Found", data: { field → message } }`) |
| any other `Exception`           | 500         |

OpenAPI / Swagger UI is served at `/lynq-backend-app/swagger-ui.html` (enabled in the default profile, disabled in production).

---

## Sample requests

> Substitute `$UUID` with any UUID per request (e.g. `uuidgen`) and `$TOKEN` with a `lynq-iam` access token. Default profile serves on port `8082`.

**Create your user profile**

```bash
curl -X POST http://localhost:8082/lynq-backend-app/dmz/user \
  -H "Content-Type: application/json" \
  -H "lynq-request-uuid: $UUID" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "userType": "CANDIDATE",
    "fullName": "John Doe",
    "currentPosition": "Backend Engineer",
    "about": "10 years building JVM services",
    "githubUrl": "https://github.com/johndoe",
    "linkedinUrl": "https://linkedin.com/in/johndoe",
    "birthDate": "1990-05-01"
  }'
```

**Get a pre-signed URL, upload the image straight to the bucket, then confirm it**

```bash
UPLOAD=$(curl -s "http://localhost:8082/lynq-backend-app/dmz/user/generate-upload-image?file-name=avatar.png" \
  -H "lynq-request-uuid: $UUID" -H "Authorization: Bearer $TOKEN")
URL=$(echo "$UPLOAD" | jq -r '.data.preSignedUrl')
FILE_ID=$(echo "$UPLOAD" | jq -r '.data.fileId')

curl -X PUT "$URL" --upload-file ./avatar.png

curl -X POST "http://localhost:8082/lynq-backend-app/dmz/user/confirm-upload-image?file-id=$FILE_ID" \
  -H "lynq-request-uuid: $UUID" -H "Authorization: Bearer $TOKEN"
```

**Register as a company**

```bash
curl -X POST http://localhost:8082/lynq-backend-app/dmz/company \
  -H "Content-Type: application/json" \
  -H "lynq-request-uuid: $UUID" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "fullName": "Jane Roe",
    "currentPosition": "Head of Talent",
    "userAbout": "Hiring the best",
    "birthDate": "1985-02-11",
    "companyName": "Acme Inc",
    "companyAbout": "We build rockets",
    "companySize": 250
  }'
```

**Create a job post**

```bash
curl -X POST http://localhost:8082/lynq-backend-app/dmz/job \
  -H "Content-Type: application/json" \
  -H "lynq-request-uuid: $UUID" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Senior Java Engineer",
    "description": "Build the Lynq backend",
    "workType": "REMOTE",
    "salaryRangeDown": 90000,
    "salaryRangeTop": 130000,
    "jobPostSource": "LYNQ",
    "skills": ["Java", "Spring Boot", "MySQL"]
  }'
```

**Search the job feed**

```bash
curl "http://localhost:8082/lynq-backend-app/dmz/job?page=0&size=20&filterValue=java" \
  -H "lynq-request-uuid: $UUID" \
  -H "Authorization: Bearer $TOKEN"
```


---

## Running locally

**Prerequisites**

- JDK 21
- Maven 3.9+ (or the bundled `./mvnw`)
- A reachable MySQL 9 (`lynq_backend_db`), a running `lynq-iam`, and — for uploads — a running `lynq-file-storage`

The default `application.yaml` targets `localhost:3306` (MySQL, `root` / `federico`), `lynq-iam` at `http://localhost:8080/lynq-iam`, `lynq-ml` at `http://localhost:8084/lynq-ml`, and `lynq-file-storage` at `http://localhost:8085/lynq-file-storage`. Override anything that differs.

**Steps**

```bash
# 1. Bring up the data services, IAM and file storage (easiest via the repo-root compose file)
cd ..
docker compose up -d mysql localstack lynq-iam lynq-file-storage

# 2. Build and run the backend
cd lynq-app-backend
./mvnw clean package
java -jar target/lynq-app-backend.jar

# Or run directly:
./mvnw spring-boot:run
```

Liquibase creates the schema and tables on first startup.

Service URLs (default profile):
- API: `http://localhost:8082/lynq-backend-app`
- Swagger UI: `http://localhost:8082/lynq-backend-app/swagger-ui.html`
- Actuator / Prometheus: `http://localhost:8083/actuator`

**Tests** (Testcontainers spins up a MockServer per downstream service — `lynq-iam`, `lynq-ml` and `lynq-file-storage`; Docker must be running):

```bash
./mvnw test
```

---

## Running with Docker

The repo-root `docker-compose.yaml` provisions the whole platform — MySQL, LocalStack, `lynq-iam`, `lynq-ml` (+ Ollama), `lynq-file-storage`, this backend, and the frontend. Run compose from the repository root (one level up):

```bash
# Build the jar first (the image just COPYs it in)
./mvnw clean package

cd ..
docker compose up --build
```

In compose the backend runs the `production` profile and is published on host ports **8082** (API) and **8083** (management), talking to `lynq-iam` at `http://lynq-iam:8080/lynq-iam` and `lynq-file-storage` at `http://lynq-file-storage:8080/lynq-file-storage`. Only `lynq-file-storage` holds bucket credentials.

---

## Configuration

Two profiles ship with the project:

- **`application.yaml`** (default) — local development; hard-coded credentials and ports (`8082`/`8083`), Swagger enabled.
- **`application-production.yaml`** — env-var driven, ports `8080`/`8081`, Swagger disabled. Activate with `SPRING_PROFILES_ACTIVE=production`.

| Variable                | Used by                                    | Notes |
| ----------------------- | ------------------------------------------ | ----- |
| `DB_URL`                | `spring.datasource.url` (JDBC URL)         | |
| `DB_USERNAME`           | MySQL user                                 | |
| `DB_PASSWORD`           | MySQL password                             | |
| `LYNQ_IAM_URL`          | `lynq.iam.url` (Feign client)              | default `http://lynq-iam:8080/lynq-iam` |
| `LYNQ_ML_URL`           | `lynq.ml.url` (Feign client)               | default `http://localhost:8084/lynq-ml` |
| `LYNQ_FILE_STORAGE_URL` | `lynq.file-storage.url` (Feign client)     | default `http://lynq-file-storage:8080/lynq-file-storage`. No `AWS_*` variables here — the bucket belongs to `lynq-file-storage` |

---

## Observability

- **Logs** — Log4j2 (`log4j2-spring.xml`). Every entry carries the `requestId` MDC key set by `RequestUuidFilter`, so logs for one request correlate across `lynq-app-backend`, `lynq-iam`, and `lynq-ml` by the same UUID.
- **Audit logs** — methods annotated with `@AuditLog` are wrapped by `LogAspect`, which logs entry/exit + (sanitized) arguments. Fields named `password`, `newPassword`, `refreshToken`, and `accessToken` are masked, recursively, in both parameters and serialized bodies.
- **Health** — `/actuator/health` with `liveness`/`readiness` probes, on the management port (`8083` default / `8081` prod).
- **Metrics** — `/actuator/prometheus` exports Micrometer metrics in Prometheus format.

---

## Project layout

```
src/
├── main/
│   ├── java/com/lynq/backend/
│   │   ├── LynqAppBackendApplication.java
│   │   ├── aspect/        # @AuditLog + LogAspect (sensitive-field masking)
│   │   ├── client/        # Feign clients for lynq-iam, lynq-ml & lynq-file-storage + DTOs
│   │   ├── config/        # App (Jackson), Security, Filter, OpenAPI beans
│   │   ├── controller/    # Controller interfaces + impls, request/response DTOs, error handler
│   │   ├── enums/         # UserType, WorkType, JobStatus, JobPostSource, Language
│   │   ├── exceptions/    # BadRequest / Forbidden / NotFound
│   │   ├── filter/        # RequestUuid + auth-header + IAM-authentication filters, PublicPaths
│   │   ├── model/         # JPA entities
│   │   ├── repository/    # Spring Data repositories + JobWithDetailsProjection
│   │   ├── security/      # LynqUserPrincipal
│   │   └── service/       # User / Company / Job / FileStorage services
│   └── resources/
│       ├── application.yaml
│       ├── application-production.yaml
│       ├── log4j2-spring.xml
│       └── changelog/     # Liquibase DDL
└── test/
    └── java/com/lynq/backend  # JUnit + Testcontainers (E2E, controllers, services, filters, models)
```