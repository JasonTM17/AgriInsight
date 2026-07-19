---
phase: 1
title: "Backend foundation and contracts"
status: pending
priority: P1
effort: "2-3d"
dependencies: []
---

# Phase 1: Backend foundation and contracts

## Overview

Create the isolated Java 21 Spring Boot application, reproducible Maven build, PostgreSQL/Flyway foundation, module boundary checks, safe configuration, and API/error conventions. This phase proves that the new backend can be built and tested without changing the Python pipeline or requiring Docker at developer startup.

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

Phase 1 owns only shared/foundation code and the `tenants` anchor table. It must not add business CRUD or a second artifact reader. `application.yml` uses environment variables for JDBC URL, separate Flyway/runtime credentials, OIDC values, CORS origins, and profile; checked-in defaults are safe local placeholders only. Production must never run JPA with the Flyway owner credential.

## Related Code Files

- Create: `D:\AgriInsight\backend\pom.xml`
- Create: `D:\AgriInsight\backend\mvnw`, `D:\AgriInsight\backend\mvnw.cmd`, `D:\AgriInsight\backend\.mvn\wrapper\*`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\AgriInsightBackendApplication.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\api\ApiExceptionHandler.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\api\ApiVersion.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\persistence\AuditableEntity.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\persistence\DatabaseSchemaReadinessIndicator.java`
- Create: `D:\AgriInsight\backend\src\main\resources\application.yml`
- Create: `D:\AgriInsight\backend\src\main\resources\application-test.yml`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V1__create_tenant_anchor.sql`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\architecture\ModuleBoundaryTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\BackendContextTest.java`
- Create: `D:\AgriInsight\backend\Dockerfile`
- Create: `D:\AgriInsight\backend\.gitignore` (`target/`, `.runtime/`, local IDE/temp only)
- Create: `D:\AgriInsight\scripts\run-backend-tests.ps1`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — build and boundary tests:** add a context test that expects the application class and a Modulith verification test that rejects cycles/internal-package access. Add a test that confirms no checked-in config contains a password/token/private key and that only `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` are public. Test PostgreSQL down or schema version behind => readiness 503 while liveness remains 200; unauthenticated health responses expose no JDBC/schema details.
2. **Green — scaffold:** generate the Maven wrapper; configure Java 21 release, compiler warnings, dependency locking/reporting policy, Spring Boot 4.1.0, Spring Modulith 2.1.0 BOM, MVC, validation, JPA, PostgreSQL driver, Flyway, Actuator, OpenAPI starter, security test dependencies, and Testcontainers test dependencies. Resolve the exact Boot-4-compatible OpenAPI/Testcontainers versions in the POM and record them in the phase report; do not use dynamic `latest` versions.
3. **Green — foundation runtime:** implement the application class, profile-safe YAML with `spring.jpa.open-in-view=false`, explicit liveness/readiness groups, `ApiVersion`, correlation-id filter, and RFC 9457/RFC 7807-compatible `ProblemDetail` mapping for validation, malformed JSON, conflicts, and unexpected errors. Readiness explicitly includes `db` plus a custom schema-history/version indicator; liveness excludes external dependencies. Map DTOs inside the scoped service transaction; serialization cannot trigger lazy SQL afterward. Diagnostics returned to clients are generic; detailed causes go to structured logs with a correlation id.
4. **Green — database:** add `V1__create_tenant_anchor.sql` with UUID tenant id, canonical code, display name, active status, UTC audit timestamps, and optimistic-lock version. Add constraints for nonblank code/name and a unique tenant code. Keep database role creation out of application startup; phase 3 supplies least-privilege role SQL.
5. **Green — delivery:** add a multi-stage non-root Dockerfile and a PowerShell wrapper that runs Maven with `-Dmaven.repo.local=D:\AgriInsight\artifacts\_tmp\m2-repository` (or an explicit `MAVEN_REPO_LOCAL` override), after invoking the disk guard. The wrapper must fail before Maven if C/D status is not PASS.
6. **Refactor:** split Java classes below ~200 LOC, keep controllers thin, keep shared primitives dependency-light, run Modulith verification, and remove generated `target/`, Maven caches, reports, and Docker output from the commit.
7. **CI contract:** document the Java 21 compile/unit command for phase 7's lead-owned workflow change; do not modify the shared workflow in this foundation phase, alter the existing Python/Node job, or make the backend a dependency of pipeline/dashboard.

## Contracts and invariants

- Route prefix is `/api/v1`; foundation has no unauthenticated business route.
- Client error content type is `application/problem+json`; no stack traces, SQL, file paths, JWTs, or secrets.
- `tenant.code` uses the same trim + uppercase canonicalization rule as Python business codes. Java UUIDs are canonical operational/API identities; canonical business codes are the stable cross-system mapping keys carried beside UUIDs in events.
- All timestamps persisted by Java are timezone-aware UTC; money/quantities are not introduced until domain phases.
- Open Session in View is disabled in every profile. Repository/entity access after the tenant transaction is a test failure, not an implicit lazy query.
- Readiness proves the runtime database is reachable and at the expected Flyway schema version; liveness proves only the process can run. Health details are never public, and IdP/JWKS network reachability is not a readiness dependency.
- Backend runtime code and Java tests never use `artifacts/` as application storage. Only build tooling may use the ignored `artifacts/_tmp` Maven/temp area selected by the wrapper.
- The existing `agriinsight-bronze-silver-gold-v1` manifest and Gold contract are read-only external contracts.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -DskipTests=false test`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository verify`
- `ck plan validate plans/260719-0753-backend-auth-rbac/plan.md`
- `git diff --check`

## Success Criteria

- [ ] `backend/` builds on CI with Temurin 21 and local JDK 24/26 using `--release 21`.
- [ ] Fresh PostgreSQL applies Flyway V1; checksum validation is enabled and applied SQL is never edited in place.
- [ ] Spring Modulith/ArchUnit boundary test is green and proves the foundation does not depend on domain internals.
- [ ] DB/schema-aware readiness and process-only liveness have focused 200/503 tests with hidden details; no business route is accidentally public.
- [ ] Open Session in View is disabled and a focused test proves response serialization performs no SQL after the scoped transaction closes.
- [ ] Existing Python test suite, compileall, Node check, and Compose config remain green.
- [ ] Maven/Docker/temp outputs are outside tracked files and under D when generated locally.

## Risk Assessment

- Boot 4/Spring Modulith/OpenAPI version mismatch: resolve from official compatibility metadata before implementation; pin versions and fail the build on unresolved dependencies.
- Local JDK differs from CI: enforce compiler release 21 and make CI Temurin 21 the canonical gate; do not claim local runtime equivalence.
- PostgreSQL is unavailable: unit/context tests must not silently switch to H2; defer container integration with an explicit blocked gate.
- C-drive pressure: the wrapper must invoke the disk guard and use D-local Maven/temp paths; no cleanup command is permitted.

## Rollback

Delete only the unmerged `backend/` foundation files and the additive Java CI job if this phase is rejected. Do not touch Python files, artifacts, existing Compose services, or prior commits. Database rollback is a forward migration/backup concern once a shared environment exists.
