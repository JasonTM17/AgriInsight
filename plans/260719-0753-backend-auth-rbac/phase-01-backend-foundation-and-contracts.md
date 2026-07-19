---
phase: 1
title: "Backend foundation and contracts"
status: completed
priority: P1
effort: "2-3d"
dependencies: []
---

# Phase 1: Backend foundation and contracts

## Overview

Create the isolated Java 21 Spring Boot application, reproducible Maven build, PostgreSQL/Flyway foundation, module boundary checks, safe configuration, and API/error conventions. This phase proves that the new backend can be built and tested without changing the Python pipeline or requiring Docker at developer startup.

## Acceptance

Accepted on 2026-07-19. The guarded current-tree verification passed 24 unit tests plus 1 required PostgreSQL 18/Testcontainers integration test; Flyway V1 applied and validated from an empty schema. A Temurin 21 multi-stage image built successfully and passed a loopback-only, non-root runtime smoke test. Existing Python, compileall, Node, Compose-config, and wheel gates remained green. See the [acceptance report](./reports/acceptance-2026-07-19-backend-phase1.md) and [engineering journal](../../docs/journals/260719-2310-backend-phase1-acceptance.md).

## Requirements

- Functional: boot a minimal application, expose loopback-safe health/readiness, version all future routes under `/api/v1`, and load a PostgreSQL schema through Flyway.
- Non-functional: compile with Java release 21, run as non-root in the optional image, keep secrets out of Git, use ProblemDetail, keep the Maven repository/temp under `D:\AgriInsight\artifacts\_tmp`, and keep normal `target/` output ignored on D.
- Compatibility: existing `python -m pytest`, Python CLI, dashboard, `docker compose ... pipeline/dashboard`, and artifact manifest behavior remain unchanged.

## Architecture

`backend/` is a separate Maven project with package-root `com.agriinsight.backend`. Use Spring Modulith application modules rather than deployable services:

```text
com.agriinsight.backend
├── shared       # IDs, clock, API errors, persistence/audit primitives
├── identity     # added in phase 2
├── authorization/tenancy
├── farm/operations
├── inventory
├── cost
└── integration
```

Phase 1 owns only shared/foundation code and the `tenants` anchor table. It must not add business CRUD or a second artifact reader. `application.yml` uses environment variables for JDBC URL, separate Flyway/runtime credentials, health behavior, and the server bind; checked-in defaults are safe local placeholders only. Phase 2 owns OIDC and CORS configuration. Production must never run JPA with the Flyway owner credential.

## Related Code Files

- Create: `D:\AgriInsight\backend\pom.xml`
- Create: `D:\AgriInsight\backend\mvnw`, `D:\AgriInsight\backend\mvnw.cmd`, `D:\AgriInsight\backend\.mvn\wrapper\*`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\AgriInsightBackendApplication.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\api\ApiExceptionHandler.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\api\ApiVersion.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\config\FoundationSecurityConfig.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\config\DatabaseSchemaReadinessIndicator.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\persistence\AuditableEntity.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\persistence\TenantAnchor.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\web\CorrelationIdFilter.java`
- Create: `D:\AgriInsight\backend\src\main\resources\application.yml`
- Create: `D:\AgriInsight\backend\src\test\resources\application-test.yml` (test-only; never packaged into the runtime jar)
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V1__create_tenant_anchor.sql`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\architecture\ModuleBoundaryTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\BackendContextTest.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\DatabaseUnavailableLifecycleTest.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\ReadinessContractTest.java`
- Create: `D:\AgriInsight\backend\Dockerfile`
- Create: `D:\AgriInsight\backend\.dockerignore`
- Create: `D:\AgriInsight\backend\.gitignore` (`target/`, `.runtime/`, local IDE/temp only)
- Create: `D:\AgriInsight\scripts\run-backend-tests.ps1`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — build and boundary tests:** add a context test that expects the application class and a Modulith verification test that rejects cycles/internal-package access. Add a test that confirms no checked-in config contains a password/token/private key and that only `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` are public. Test PostgreSQL down or schema version behind => readiness 503 while liveness remains 200; unauthenticated health responses expose no JDBC/schema details.
2. **Green — scaffold:** generate the Maven wrapper; configure Java 21 release, compiler warnings, dependency locking/reporting policy, Spring Boot 4.1.0, Spring Modulith 2.1.0 BOM, MVC, validation, JPA, PostgreSQL driver, Flyway, Actuator, OpenAPI starter, security test dependencies, and Testcontainers test dependencies. Resolve the exact Boot-4-compatible OpenAPI/Testcontainers versions in the POM and record them in the phase report; do not use dynamic `latest` versions.
3. **Green — foundation runtime:** implement the application class, a loopback-safe default bind, profile-safe YAML with `spring.jpa.open-in-view=false`, explicit liveness/readiness groups, `ApiVersion`, a highest-precedence correlation-id filter, and RFC 9457/RFC 7807-compatible `ProblemDetail` mapping for MVC and security errors. Readiness explicitly includes `db` plus a custom schema-history/version indicator; liveness excludes external dependencies. JPA boot must not require database metadata or DDL validation so an unavailable database affects readiness rather than process liveness; Flyway and the schema indicator own migration validation. When a phase introduces a transactional response DTO, it must map the DTO inside the scoped service transaction and prove serialization cannot trigger lazy SQL afterward. Diagnostics returned to clients are generic; detailed causes go to structured logs with a correlation id.
4. **Green — database:** add `V1__create_tenant_anchor.sql` with UUID tenant id, canonical code, display name, active status, UTC audit timestamps, and optimistic-lock version. Add constraints for nonblank code/name and a unique tenant code. Keep database role creation out of application startup; phase 3 supplies least-privilege role SQL.
5. **Green — delivery:** add a multi-stage non-root Dockerfile, narrow `.dockerignore`, checksum-verified Maven wrapper, and a PowerShell wrapper that runs Maven with `-Dmaven.repo.local=D:\AgriInsight\artifacts\_tmp\m2-repository` (or an explicit override that still resolves to D), after invoking the disk guard. The wrapper must fail before Maven if C/D status is not PASS, reject hidden Maven environment/config arguments and CLI arguments that redirect repository/temp output or mask test failures, and preflight Docker for `verify`. Unit tests run under `test`; the required PostgreSQL/Testcontainers gate runs under `verify`, requires at least one matching integration test, and must fail, never skip, when Docker is unavailable.
6. **Refactor:** split Java classes below ~200 LOC, keep controllers thin, keep shared primitives dependency-light, run Modulith verification, and remove generated `target/`, Maven caches, reports, and Docker output from the commit.
7. **CI contract:** document the Java 21 compile/unit command for phase 7's lead-owned workflow change; do not modify the shared workflow in this foundation phase, alter the existing Python/Node job, or make the backend a dependency of pipeline/dashboard.

## Contracts and invariants

- Route prefix is `/api/v1`; foundation has no unauthenticated business route.
- Client error content type is `application/problem+json`; no stack traces, SQL, file paths, JWTs, or secrets.
- `tenant.code` strips Unicode edge whitespace, uppercases with `Locale.ROOT`, and must match portable ASCII grammar `[A-Z0-9][A-Z0-9._-]{0,63}`. This is a strict interoperable subset of the Python trim + uppercase rule. Java UUIDs are canonical operational/API identities; canonical business codes are the stable cross-system mapping keys carried beside UUIDs in events.
- All timestamps persisted by Java are timezone-aware UTC; money/quantities are not introduced until domain phases.
- Open Session in View is disabled in every profile. Phase 1 exposes no transactional business response DTO, so a serialization SQL-count test would be synthetic. The first phase that adds such a DTO must add the focused no-SQL-after-transaction regression before that API can be accepted.
- Readiness proves the runtime database is reachable and at the expected Flyway schema version; liveness proves only the process can run. Hikari and PostgreSQL driver connect/login/socket timeouts bound unavailable or black-holed database attempts. Health details are never public, and IdP/JWKS network reachability is not a readiness dependency.
- Backend runtime code and Java tests never use `artifacts/` as application storage. Only build tooling may use the ignored `artifacts/_tmp` Maven/temp area selected by the wrapper.
- The existing `agriinsight-bronze-silver-gold-v1` manifest and Gold contract are read-only external contracts.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 test`
- `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
- `ck plan validate plans/260719-0753-backend-auth-rbac/plan.md`
- `git diff --check`

## Success Criteria

- [x] `backend/` builds with Temurin 21 in the image/verification environment and with the local newer JDK using `--release 21`; the shared protected CI job remains a Phase 7 release gate.
- [x] Fresh PostgreSQL applies Flyway V1; checksum validation is enabled and applied SQL is never edited in place.
- [x] Spring Modulith/ArchUnit boundary test is green and proves the foundation does not depend on domain internals.
- [x] DB/schema-aware readiness and process-only liveness have focused 200/503 tests with hidden details; no business route is accidentally public.
- [x] Open Session in View is disabled in every profile. No transactional business response exists in Phase 1; the focused serialization/no-SQL proof is a mandatory acceptance gate for the first transactional API phase.
- [x] Existing Python test suite, compileall, Node check, and Compose config remain green.
- [x] Maven/Docker/temp outputs are outside tracked files and under D when generated locally.

## Risk Assessment

- Boot 4/Spring Modulith/OpenAPI version mismatch: resolve from official compatibility metadata before implementation; pin versions and fail the build on unresolved dependencies.
- Local JDK differs from CI: enforce compiler release 21 and make CI Temurin 21 the canonical gate; do not claim local runtime equivalence.
- PostgreSQL is unavailable: unit/context tests must not silently switch to H2; defer container integration with an explicit blocked gate.
- C-drive pressure: the wrapper must invoke the disk guard and use D-local Maven/temp paths; no cleanup command is permitted.

## Rollback

Delete only the unmerged `backend/` foundation files and the additive Java CI job if this phase is rejected. Do not touch Python files, artifacts, existing Compose services, or prior commits. Database rollback is a forward migration/backup concern once a shared environment exists.
