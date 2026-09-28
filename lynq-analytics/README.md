# lynq-analytics

Analytics service for the Lynq platform. It will serve the numbers that sit next to a job post: the estimated **time to fill** a company can expect, a candidate's **standing** among the applicants of a post, and **salary medians** for a position and for similar candidates.

It does not read `lynq_backend_db`. Its data will arrive as domain events published by `lynq-app-backend` (SNS topic `lynq-domain-events` → SQS queue `lynq-analytics-events`) and be projected into its own schema, `lynq_analytics_db`.

This is the skeleton: the service boots, authenticates every request against [`lynq-iam`](../lynq-iam) and answers with the platform's response envelope. Queue, read model and endpoints come next.

---

## Technologies

| Area              | Stack                                                                        |
| ----------------- | ---------------------------------------------------------------------------- |
| Language / JDK    | Java 21                                                                      |
| Framework         | Spring Boot 4.0.6 (Web, Data JPA, Actuator, AOP, Security, Validation)       |
| Web server        | Jetty (Tomcat excluded)                                                      |
| Persistence       | MySQL 9 (`lynq_analytics_db`), Hibernate / Spring Data JPA, Liquibase migrations |
| Inter-service     | Spring Cloud OpenFeign — client for `lynq-iam`                               |
| Docs              | springdoc-openapi (Swagger UI)                                              |
| Logging           | Log4j2 + SLF4J MDC for per-request correlation IDs; `@AuditLog` aspect       |
| Metrics           | Micrometer + Prometheus registry                                            |
| Build             | Maven (Spring Boot plugin), Dockerfile on `eclipse-temurin:21-jre-alpine`   |
| Tests             | JUnit Jupiter, Testcontainers (MockServer for `lynq-iam`), H2, JaCoCo        |

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

## Running locally

**Prerequisites**

- JDK 21
- Maven 3.9+
- A reachable MySQL 9 with the `lynq_analytics_db` database, and a running `lynq-iam`

The default `application.yaml` targets `localhost:3306` (MySQL, `root` / `federico`) and `lynq-iam` at `http://localhost:8080/lynq-iam`.

```bash
mvn clean package
java -jar target/lynq-analytics.jar
```

Liquibase applies the changesets under `changelog/ddl` on startup.

Service URLs (default profile):
- API: `http://localhost:8091/lynq-analytics`
- Swagger UI: `http://localhost:8091/lynq-analytics/swagger-ui.html`
- Actuator / Prometheus: `http://localhost:8092/actuator`

**Tests** (Testcontainers spins up a MockServer for `lynq-iam`; Docker must be running):

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
    │   │   ├── exceptions/      # BadRequest, Forbidden, NotFound
    │   │   ├── filter/          # RequestUuid, AuthHeaderExistence, IamAuthentication, PublicPaths
    │   │   └── security/        # LynqUserPrincipal, Role, @HasRole
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-production.yaml
    │       ├── changelog/       # Liquibase, lynq_analytics_db
    │       └── log4j2-spring.xml
    └── test/
```
