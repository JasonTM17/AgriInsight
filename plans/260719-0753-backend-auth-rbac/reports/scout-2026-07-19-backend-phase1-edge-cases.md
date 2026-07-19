# Backend Phase 1 Edge-Case Scout

> Historical pre-remediation scout. Findings below describe the source at scout time; see [Backend Phase 1 Acceptance](./acceptance-2026-07-19-backend-phase1.md) for the final gate evidence.

## Summary

Phase 1 has safe defaults in several places, but not review-ready against its own contracts. Five code/contract blockers remain: production-equivalent readiness is unproved and likely blocked by eager JPA validation; local loopback binding is not enforced; security-filter errors bypass the ProblemDetail/correlation contract; API docs become public in every profile when enabled; Java/database code canonicalization diverges from Python for Unicode whitespace. PostgreSQL migration and Java 21 gates also remain unverified in the available test evidence.

Evidence basis: repository inventory, source/tests/config, read-only `git status`/`git diff`, and pre-existing Surefire reports. No build, test, Docker, network, or process command executed during this scout. `backend/` and `scripts/run-backend-tests.ps1` are currently untracked; there is no tracked diff to compare.

## Blockers

### 1. Production DB-down/readiness behavior is not exercised

- Required behavior: PostgreSQL down or schema behind must yield readiness 503 while liveness stays 200 (`phase-01-backend-foundation-and-contracts.md:59`, `:74`, `:91`).
- Production config enables JPA schema validation (`backend/src/main/resources/application.yml:8-11`) and includes real `db` plus `schemaHistory` contributors (`backend/src/main/resources/application.yml:35-39`). A DB outage or missing schema can therefore fail application initialization before either probe is callable; no production-equivalent test disproves this failure mode.
- The context profile removes DataSource, JPA, and Flyway through test-only resources (`backend/src/test/resources/application-test.yml`). `BackendContextTest` further replaces readiness membership with only `readinessState` (`backend/src/test/java/com/agriinsight/backend/BackendContextTest.java:22`), while `ReadinessContractTest` injects a synthetic down indicator (`backend/src/test/java/com/agriinsight/backend/ReadinessContractTest.java:21-26`, `:46-55`). These tests prove Actuator status mapping, not actual DB-down startup/readiness; the test-only resource is deliberately not packaged into the runtime jar.
- Required review action: add a production-shaped startup/probe test with unavailable DB and with migrated-but-behind schema. If startup fails, separate process boot/liveness from eager JPA schema validation or revise the explicit phase contract before approval.

### 2. Loopback-only local exposure is not enforced

- Phase 1 requires loopback-safe health/readiness (`phase-01-backend-foundation-and-contracts.md:18`); the plan also says backend ports remain loopback-only (`plan.md:62`).
- `server` config sets only graceful shutdown (`backend/src/main/resources/application.yml:22-23`); no `server.address` or profile-specific bind exists. A normal local Spring Boot launch therefore has no repository-level loopback guard.
- Required review action: establish a safe local bind/default and an explicit container/deployment override, then test effective configuration.

### 3. Denied/security-filter responses do not implement the public error contract

- Phase contract requires `application/problem+json` for client errors (`phase-01-backend-foundation-and-contracts.md:69-70`); whole-plan contract includes authentication and authorization failures (`plan.md:142-146`).
- `ApiExceptionHandler` handles MVC/controller exceptions only (`backend/src/main/java/com/agriinsight/backend/shared/api/ApiExceptionHandler.java:25-95`). `FoundationSecurityConfig` installs no ProblemDetail `AuthenticationEntryPoint` or `AccessDeniedHandler` (`backend/src/main/java/com/agriinsight/backend/shared/config/FoundationSecurityConfig.java:13-34`). Security denials occur before controller advice.
- The only denied-route assertion accepts either 401 or 403 and checks neither media type, safe body, nor correlation header (`backend/src/test/java/com/agriinsight/backend/BackendContextTest.java:57-61`).
- Correlation filter is merely a component with no explicit order/security-chain insertion (`backend/src/main/java/com/agriinsight/backend/shared/web/CorrelationIdFilter.java:15-33`), so an early security rejection also lacks a proven generated/validated correlation ID.
- Required review action: make security failures produce the same safe ProblemDetail contract; put correlation handling before security rejection; add full-context assertions for status, media type, body, and header.

### 4. Enabling API docs makes them anonymous in every profile

- Plan permits Swagger UI only in development or authenticated outside development (`plan.md:147`).
- One environment boolean enables Springdoc (`backend/src/main/resources/application.yml:41-50`) and simultaneously `permitAll`s `/v3/api-docs/**` and Swagger UI (`backend/src/main/java/com/agriinsight/backend/shared/config/FoundationSecurityConfig.java:16`, `:28-31`). There is no profile/environment restriction.
- Default `false` is safe, but a production operator enabling documentation silently creates anonymous exposure, contrary to the stated contract.
- Required review action: bind anonymous docs to an explicit development profile; keep non-development docs disabled or authenticated. Test both effective profiles.

### 5. Business-code canonicalization is not the same as Python

- Contract requires Java tenant codes to use the same trim + uppercase rule as Python (`phase-01-backend-foundation-and-contracts.md:71`). Python uses Pandas `str.strip().str.upper()` (`src/agriinsight/transform.py:18-19`). Java uses `String.trim()` then `toUpperCase(Locale.ROOT)` (`backend/src/main/java/com/agriinsight/backend/shared/persistence/TenantAnchor.java:62-76`); the SQL constraint uses `btrim` (`backend/src/main/resources/db/migration/V1__create_tenant_anchor.sql:9`).
- `String.trim()` and default PostgreSQL `btrim` do not cover the same Unicode whitespace set as Python/Pandas stripping. Example: a code surrounded by non-breaking spaces can canonicalize in Python but remain distinct in Java/database, breaking the declared cross-system stable key.
- Tests cover ASCII spaces only (`backend/src/test/java/com/agriinsight/backend/shared/persistence/TenantAnchorTest.java:12-17`) and lowercase SQL only (`backend/src/test/java/com/agriinsight/backend/persistence/FlywayMigrationIntegrationTest.java:24-54`).
- Required review action: define the allowed code character/whitespace contract, implement one equivalent normalization rule at DTO/entity/database boundaries, and add cross-language fixtures.

### 6. Phase completion gates are not yet evidenced

- The pre-existing Surefire result shows the only PostgreSQL/Flyway integration test skipped (`backend/target/surefire-reports/com.agriinsight.backend.persistence.FlywayMigrationIntegrationTest.txt:4`). Thus fresh PostgreSQL migration, constraints, and Flyway compatibility are not proved (`phase-01-backend-foundation-and-contracts.md:89`).
- Existing Surefire metadata shows tests ran on Java 24, not canonical Temurin 21 (`backend/target/surefire-reports/TEST-com.agriinsight.backend.BackendContextTest.xml:4`); compiler release 21 is configured (`backend/pom.xml:20-29`) but the Java 21 runtime/CI gate remains open (`phase-01-backend-foundation-and-contracts.md:88`).
- No Docker build was run in this scout, per task constraint, so optional image build correctness is unverified.
- Classification: acceptance/verification blocker, not proof of an implementation defect.

## Follow-ups Before Later Phases

- **Repeatable Flyway migration compatibility:** readiness selects the last successful row by `installed_rank` without excluding repeatable rows (`backend/src/main/java/com/agriinsight/backend/shared/config/DatabaseSchemaReadinessIndicator.java:15-20`). A successful repeatable migration has no numeric version and can make a current schema report unavailable. The plan explicitly anticipates repeatable policy/grant definitions (`plan.md:159`). Test versioned + repeatable history before phase 3.
- **Runtime Flyway-history privilege:** readiness always queries `flyway_schema_history` (`DatabaseSchemaReadinessIndicator.java:15-20`). Phase 3's restricted runtime role must receive only the narrow read needed or readiness will stay DOWN; prove with distinct `current_user` roles.
- **Wrapper path enforcement:** defaults use `artifacts/_tmp` (`scripts/run-backend-tests.ps1:19-33`), but arbitrary `MAVEN_REPO_LOCAL`, `MAVEN_TEMP_DIR`, and `MAVEN_USER_HOME` values are accepted and later Maven arguments can supply another `-Dmaven.repo.local` (`scripts/run-backend-tests.ps1:35-48`). The local D-drive output claim needs validation or explicitly documented trusted overrides plus wrapper tests.
- **Wrapper supply chain:** wrapper distribution URL is pinned to Maven 3.9.12 but has no `distributionSha256Sum` (`backend/.mvn/wrapper/maven-wrapper.properties:1-3`). Add checksum verification to make first-use download match the reproducible-build claim.
- **Docker context:** no `backend/.dockerignore` exists. Current ignored `target/`, runtime overrides, logs, and IDE state can still be sent to a local/remote build context even though the Dockerfile copies only named paths (`backend/Dockerfile:5-11`). Add a narrow context ignore before image work.
- **Shared Modulith API:** the only module test verifies the current graph (`backend/src/test/java/com/agriinsight/backend/architecture/ModuleBoundaryTests.java:11-14`), which is effectively single-module. Shared types live in nested packages and no named interface is declared. Before phase 2 imports them, prove intended public shared interfaces rather than discovering an internal-package violation later.
- **Configuration secret test breadth:** current test scans only `src/main/resources` and accepts any value beginning `${` (`backend/src/test/java/com/agriinsight/backend/shared/config/ConfigurationSafetyTest.java:20-31`, `:36-52`). It does not protect Docker/build config, Java constants, `.properties` using `=`, or an environment placeholder with a non-empty secret fallback. Current inventory found no committed secret; broaden the regression gate separately.
- **Unknown JSON fields and method-validation variants:** no strict unknown-field setting exists, and tests cover malformed JSON, `@NotBlank`, and one integrity conflict only (`backend/src/test/java/com/agriinsight/backend/shared/api/ApiExceptionHandlerTest.java:35-64`). Add contract tests before real DTOs/controllers arrive.

## Verified Non-Issues

- Actuator web exposure is limited to `health`; exact public matchers are root health, liveness, readiness, with deny-all fallback (`backend/src/main/resources/application.yml:25-39`; `backend/src/main/java/com/agriinsight/backend/shared/config/FoundationSecurityConfig.java:22-33`).
- Public health details are globally hidden (`backend/src/main/resources/application.yml:30-34`). Existing tests also assert common JDBC/schema strings are absent (`backend/src/test/java/com/agriinsight/backend/BackendContextTest.java:39-47`; `backend/src/test/java/com/agriinsight/backend/ReadinessContractTest.java:32-37`).
- Flyway is disabled by default and runtime/migrator usernames use distinct defaults (`backend/src/main/resources/application.yml:4-20`). Same-role rejection belongs to the later role/RLS phase; phase 1 does not create database roles.
- Docker runtime declares and uses a non-root UID/GID (`backend/Dockerfile:13-19`). Immutable digest publication/base-image hardening is explicitly serialized to phase 7 (`plan.md:74-76`, `:121`), so mutable image tags are not treated as a phase-1 defect.
- Generated `backend/target/` exists locally but is ignored (`backend/.gitignore:1`); read-only status did not show its contents as candidates. No cleanup performed.

## Unresolved Questions

- Are business codes intentionally ASCII-only? If yes, where will that invariant be enforced across Java, SQL, and Python? If no, exact Unicode normalization/case/length semantics need specification.
- Must liveness remain callable when PostgreSQL is absent during initial boot, or only after a previously healthy process loses DB connectivity? Current phase text says PostgreSQL down without distinguishing these states.
- What trusted override policy applies to Maven repo/temp paths on developer Windows versus non-Windows CI?
