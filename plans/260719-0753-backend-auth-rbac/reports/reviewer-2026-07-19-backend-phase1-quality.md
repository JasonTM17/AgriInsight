# Backend Phase 1 Code Quality Review

> Historical Stage 2 review. Findings were remediated or explicitly deferred; see the current spec and final adversarial review for adjudication and acceptance status.

## Scope

- Focus: CK Stage 2 final bounded review; current source only
- Reviewed: 23 Java/YAML/SQL source and test files plus `backend/pom.xml`, Docker delivery files, Maven wrapper properties, and `scripts/run-backend-tests.ps1`
- Physical LOC: 1,454 across reviewed implementation/build files
- Specification: `phase-01-backend-foundation-and-contracts.md`
- Scout input: plan-local `reports/scout-2026-07-19-backend-phase1-edge-cases.md`; the root-relative scout path supplied to the task does not exist
- Constraints: no Maven, Docker, network, process, deletion, or Git operations

## Overall Assessment

**BLOCKED.** No Critical finding, but two High findings prevent production-readiness approval. The readiness contract can lose its schema contributor because bean creation relies on unsupported auto-configuration ordering, and the only real PostgreSQL/Flyway test can silently skip while the build remains green. Three Medium contract gaps remain around method-validation errors, database-level nonblank enforcement, and the required post-transaction serialization test.

## Critical Issues

None found in the bounded source review.

## High Priority

### 1. Schema readiness bean relies on ordering-unsafe `@ConditionalOnBean`

- Location: `backend/src/main/java/com/agriinsight/backend/shared/config/DatabaseSchemaReadinessIndicator.java:11-13`; consumer at `backend/src/main/resources/application.yml:45-49`
- Trigger: normal application bootstrap processes the component-scanned indicator before `JdbcTemplateAutoConfiguration` has registered its `JdbcTemplate` bean definition. Spring Boot documents `@ConditionalOnBean` as ordering-sensitive and intended for ordered auto-configuration when the required bean comes from another auto-configuration.
- Impact: `schemaHistory` can be omitted even with a configured datasource. The configured readiness group then either fails membership validation or cannot prove the Flyway schema version, defeating the core Phase 1 readiness contract. Unit construction in `DatabaseSchemaReadinessIndicatorTest` does not exercise conditional registration; no test asserts that the production-shaped context contains the actual contributor.
- Fix: register the indicator from an auto-configuration ordered after JDBC template auto-configuration, or replace the bean-order condition with an explicit schema-readiness enablement contract that does not depend on registration timing. Add a production-shaped context assertion for the real `schemaHistory` bean and verify the readiness group invokes it.

### 2. Required PostgreSQL/Flyway gate silently turns into a passing skip

- Location: `backend/src/test/java/com/agriinsight/backend/persistence/FlywayMigrationIntegrationTest.java:17-24`
- Trigger: Docker is absent, unavailable, or misconfigured on a developer or CI runner.
- Impact: `@Testcontainers(disabledWithoutDocker = true)` skips the only test that applies Flyway V1 and checks PostgreSQL constraints, while Maven can still exit successfully. This violates the phase rule that unavailable PostgreSQL must be an explicit blocked gate and permits a false-green `test`/`verify` result with no migration validation.
- Fix: make the canonical PostgreSQL integration gate fail when its container prerequisite is unavailable. If fast local tests must remain Docker-optional, split the test into an explicit integration profile/job and require that profile for Phase 1/CI acceptance; report missing Docker as a failed prerequisite, not a skipped success.

## Medium Priority

### 3. Spring MVC method-validation failures fall through to generic 500 handling

- Location: `backend/src/main/java/com/agriinsight/backend/shared/api/ApiExceptionHandler.java:52-60`, `:88-94`
- Trigger: a controller uses method validation on request parameters, path variables, headers, or return values and Spring MVC raises `HandlerMethodValidationException` rather than `ConstraintViolationException`/`MethodArgumentNotValidException`.
- Impact: invalid client input is logged and returned as a 500 `Request failed` response instead of the required 400 validation ProblemDetail. This breaks the shared error contract as soon as Phase 2 adds validated controller methods.
- Fix: add an explicit safe 400 handler for Spring MVC's method-validation exception shape and a focused MockMvc test using a constrained request parameter/path variable. Keep returned violations generic and free of rejected values.

### 4. Database nonblank display-name constraint accepts whitespace-only values

- Location: `backend/src/main/resources/db/migration/V1__create_tenant_anchor.sql:11`; Java invariant at `backend/src/main/java/com/agriinsight/backend/shared/persistence/TenantAnchor.java:78-108`
- Trigger: a migration, administrative import, or other database client inserts a display name containing only tab/newline or Unicode spacing, for example PostgreSQL `E'\t'`.
- Impact: PostgreSQL `btrim(text)` removes ordinary spaces only, so the row passes `tenants_display_name_nonblank` although the Java constructor rejects it as blank. The database trust boundary therefore does not enforce the phase's nonblank-name invariant, and invalid tenant anchors can enter through non-JPA paths.
- Fix: define one allowed whitespace contract and encode it in both Java and the database check. Add integration cases for tabs/newlines and the Unicode spacing cases the Java normalizer handles.

### 5. Required no-SQL-after-transaction serialization proof is absent

- Location: requirement at `plans/260719-0753-backend-auth-rbac/phase-01-backend-foundation-and-contracts.md:61`, `:73`, `:92`; current property-only assertion at `backend/src/test/java/com/agriinsight/backend/BackendContextTest.java:35-45`
- Trigger: the first API response includes an entity association or DTO mapping accidentally moves outside its service transaction.
- Impact: `open-in-view=false` prevents hidden post-service queries but does not prove the required mapping boundary; a regression can instead cause `LazyInitializationException`, partial response failure, or reintroduce N+1 behavior if OSIV is later changed. Phase success criterion 92 is not met by checking the configuration property alone.
- Fix: add a focused persistence/service/web test that maps a response DTO inside the transaction and asserts serialization performs zero SQL after transaction completion. Keep the assertion independent of the OSIV property check.

## Edge Cases Rechecked From Scout

- Resolved in current source: loopback default binding; dev/local-only anonymous API docs; ProblemDetail security entry point/access-denied handler; correlation filter ordering and input validation; repeatable Flyway rows excluded from latest-version query; wrapper distribution checksum; D-drive output validation; `.dockerignore`; ASCII business-code grammar with Python-compatible edge whitespace stripping.
- Still relevant: production-shaped readiness registration/lifecycle, explicit PostgreSQL migration gate, and post-transaction serialization proof.
- No current N+1 loop, shared mutable state, unauthenticated business operation, secret literal, or sensitive health detail exposure found in reviewed source.

## Contract / Plan Status Recommendation

- Keep Phase 1 `in-progress`.
- Success criterion 88 (Java 21 and local JDK matrix): unverified.
- Success criterion 89 (fresh PostgreSQL/Flyway): blocked by High finding 2 and unexecuted Docker gate.
- Success criterion 90 (Modulith boundary test): implementation present; execution unverified.
- Success criterion 91 (DB/schema readiness and liveness): blocked by High finding 1; executions unverified.
- Success criterion 92 (no SQL during response serialization): not implemented as a focused test.
- Success criteria 93-94 (existing stacks and generated-output placement): executions unverified; wrapper/Docker source is statically present.

## Recommended Actions

1. Make schema readiness registration deterministic, then assert the real contributor exists in a production-shaped context.
2. Make PostgreSQL/Flyway validation an explicit required gate that cannot pass by skipping.
3. Cover `HandlerMethodValidationException` with a safe 400 ProblemDetail.
4. Align the SQL display-name constraint with the Java blankness rule.
5. Add the transaction/serialization SQL-count regression test.
6. After disk status is PASS, run the full gates listed below before changing phase status.

## Unverified Gates

These are missing executions, not source findings:

- Maven compile/test/verify, including Spring Boot 4.1.0, Spring Modulith 2.1.0, Springdoc 3.0.3, Testcontainers 2.0.5, and Jackson 3 API compatibility
- Java runtime matrix: canonical Temurin 21 and local JDK 24/26 with `--release 21`
- Actual application bootstrap with unavailable PostgreSQL and requests to liveness/readiness
- Fresh PostgreSQL 18 Flyway migration, checksum validation, and SQL constraint behavior
- Docker image build and non-root runtime smoke test
- Modulith verification execution
- Existing Python tests/compileall, Node check, and Compose validation
- Plan validation and `git diff --check`

Disk is WARN and the task prohibited Maven, Docker, process, network, and Git operations, so no passing gate is claimed.

## Metrics

- Type coverage: not measured; Java compiler not executed
- Test coverage: not measured; Maven tests not executed
- Lint/compiler warnings: not measured
- Static findings: 0 Critical, 2 High, 3 Medium

## Unresolved Questions

None.
