# lynq-analytics

Analytics service for the Lynq platform. It will serve the numbers that sit next to a job post: the estimated **time to fill** a company can expect, a candidate's **standing** among the applicants of a post, and **salary medians** for a position and for similar candidates.

It does not read `lynq_backend_db`. Its data will arrive as domain events published by `lynq-app-backend` (SNS topic `lynq-domain-events` → SQS queue `lynq-analytics-events`) and be projected into its own schema, `lynq_analytics_db`.

Today the service boots, authenticates every request against [`lynq-iam`](../lynq-iam), answers with the platform's response envelope, and records every domain event it receives in an append-only `domain_events` table. The read model and the endpoints come next.

---

## Technologies

| Area              | Stack                                                                        |
| ----------------- | ---------------------------------------------------------------------------- |
| Language / JDK    | Java 21                                                                      |
| Framework         | Spring Boot 4.0.6 (Web, Data JPA, Actuator, AOP, Security, Validation)       |
| Web server        | Jetty (Tomcat excluded)                                                      |
| Persistence       | MySQL 9 (`lynq_analytics_db`), Hibernate / Spring Data JPA, Liquibase migrations |
| Inter-service     | Spring Cloud OpenFeign — client for `lynq-iam`                               |
| Messaging         | Spring Cloud AWS 4 SQS (`@SqsListener`), AWS SDK v2                          |
| Docs              | springdoc-openapi (Swagger UI)                                              |
| Logging           | Log4j2 + SLF4J MDC for per-request correlation IDs; `@AuditLog` aspect       |
| Metrics           | Micrometer + Prometheus registry                                            |
| Build             | Maven (Spring Boot plugin), Dockerfile on `eclipse-temurin:21-jre-alpine`   |
| Tests             | JUnit Jupiter, Testcontainers (MySQL, LocalStack, MockServer for `lynq-iam`), Awaitility, JaCoCo |

---

## Request lifecycle

Every request passes through an ordered filter chain before reaching a controller:

| Order | Filter                       | Scope                        | Purpose                                                                                          |
| :---: | ---------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------ |
| 0     | `RequestUuidFilter`          | `/*`                         | Require the `lynq-request-uuid` header; bind it to SLF4J MDC (`requestId`) and echo it back on the response. `403` if missing. |
| 1     | `AuthHeaderExistenceFilter`  | `/*` (Swagger paths exempt)  | `401` if the `Authorization` header is missing or blank.                                         |
| 2     | `IamAuthenticationFilter`    | `/*` (Swagger paths exempt)  | Call `lynq-iam` for the token's user info, then load a `LynqUserPrincipal` — with the roles it reports as authorities — into the `SecurityContext`. `401` if IAM will not resolve the token, `503` if IAM is unreachable. |

> This service does **not** verify the access token's signature. Every request reaches it through
> [`lynq-bff`](../lynq-bff), which validates the signature before proxying, which is also why the
> API will sit behind the `/dmz/analytics` prefix.

Spring Security is configured **stateless** and `permitAll` (`SecurityConfig`); the filter chain above is what enforces authentication. Authorization is method security: `@HasRole(Role.CANDIDATE)` / `@HasRole(Role.COMPANY)` over `@PreAuthorize("hasRole('{value}')")`, with `GrantedAuthorityDefaults("R_")` mapping `hasRole('CANDIDATE')` onto the `R_CANDIDATE` authority. A caller without the role gets `403` with the usual `ErrorRestResponse`.

Every response uses the platform envelope: `GlobalRestResponse` (`success`, `data`) on success and `ErrorRestResponse` (adds `reason`) on failure, mapped from exceptions in `ControllerExceptionHandler`.

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
  "payload": { "jobId": "77777777-7777-7777-7777-777777777777", "lynqScore": 72.5 }
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

---

## Running locally

**Prerequisites**

- JDK 21
- Maven 3.9+
- A reachable MySQL 9 with the `lynq_analytics_db` database, a running `lynq-iam`, and LocalStack with the domain events queue (`docker compose up localstack`)

The default `application.yaml` targets `localhost:3306` (MySQL, `root` / `federico`), `lynq-iam` at `http://localhost:8080/lynq-iam` and SQS at `http://localhost:4566`.

```bash
mvn clean package
java -jar target/lynq-analytics.jar
```

Liquibase applies the changesets under `changelog/ddl` on startup.

Service URLs (default profile):
- API: `http://localhost:8091/lynq-analytics`
- Swagger UI: `http://localhost:8091/lynq-analytics/swagger-ui.html`
- Actuator / Prometheus: `http://localhost:8092/actuator`

**Tests** (Testcontainers spins up MySQL, LocalStack with SNS and SQS, and a MockServer for `lynq-iam`; Docker must be running):

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
| `LYNQ_ANALYTICS_EVENTS_QUEUE` | `lynq.analytics.events.queue` | default `lynq-analytics-events` |
| `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | AWS SDK default chains | unset in EKS when the pod role provides them |
| `SPRING_CLOUD_AWS_SQS_ENDPOINT` | SQS endpoint override | only against LocalStack, e.g. `http://localstack:4566` |

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
    │   │   ├── client/          # LynqIamClient (Feign) and its response
    │   │   ├── config/          # AppConfig, FilterConfig, OpenApiConfig, SecurityConfig
    │   │   ├── controller/      # ControllerExceptionHandler, response envelope
    │   │   ├── exceptions/      # BadRequest, Forbidden, NotFound, InvalidDomainEvent
    │   │   ├── filter/          # RequestUuid, AuthHeaderExistence, IamAuthentication, PublicPaths
    │   │   ├── listener/        # DomainEventListener (SQS) and the envelope it receives
    │   │   ├── model/           # JPA entities
    │   │   ├── repository/      # Spring Data repositories
    │   │   ├── security/        # LynqUserPrincipal, Role, @HasRole
    │   │   └── service/         # DomainEventService
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-production.yaml
    │       ├── changelog/       # Liquibase, lynq_analytics_db
    │       └── log4j2-spring.xml
    └── test/
```
