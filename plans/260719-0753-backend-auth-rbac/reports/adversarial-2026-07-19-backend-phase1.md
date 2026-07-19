# Backend Phase 1 Adversarial Review

> Historical pre-runtime-gate review. The final guarded test and image evidence is recorded in [Backend Phase 1 Acceptance](./acceptance-2026-07-19-backend-phase1.md).

## Scope

- Review target: current uncommitted `backend/`, guarded Maven runner, test resources, delivery files, and Phase 1 contract.
- Method: adversarial source/config review after Stage 2 remediations; no Maven, Docker, network, deletion, or Git mutation was performed.
- Runtime constraint: disk guard remains WARN on C (`~9.0 GB`) and Docker daemon is stopped.

## Assessment

No new Critical or High source defect was found in the current tree. Phase 1 remains **NOT ACCEPTED** because the required compile, test, PostgreSQL/Flyway, Java 21, and image gates have not run on the remediated source.

## Rechecked Remediations

- `schemaHistory` is now a deterministic production component under `@Profile("!test")`; the DB-down lifecycle test asserts the real bean exists.
- The Testcontainers migration test is excluded from Surefire, required by Failsafe `verify`, has no Docker auto-skip flag, and Failsafe uses `failIfNoTests=true`.
- `HandlerMethodValidationException` maps to generic 400 ProblemDetail and has a constrained-parameter test.
- PostgreSQL display-name blankness uses the explicit Java-aligned Unicode whitespace set, with tab/newline/NBSP/U+2007/U+202F integration cases.
- `application-test.yml` is test-only under `src/test/resources`; it is not a tracked runtime resource. A stale ignored local `backend/target/` from an earlier build still contains the old copy, so the next accepted run must be a clean build.
- The runner rejects direct output overrides, test masking/subset selectors, `fail-never`, inherited Maven argument/config channels, unsafe JVM option channels, and preflights Docker for `verify`.
- PostgreSQL driver connect/login/socket timeouts are explicit, bounding black-holed database attempts.

## Residual Non-blocking Question

The runner now rejects explicit Maven `-f/--file`, `-s/--settings`, global-settings, module, non-recursive, and resume selectors. A future release can still replace the defensive parser with a strict allow-list of supported goals if the wrapper is exposed to untrusted command input; this is not a Phase 1 acceptance blocker.

## Required Gates Before Acceptance

1. Free C to at least 10 GB (prefer 2 GB headroom); keep D above 25 GB.
2. Run the guarded unit command, then the guarded `verify` command after Docker is available.
3. Confirm a clean Temurin 21 build, fresh PostgreSQL 18 migration/checksum/constraint behavior, Modulith boundaries, non-root image smoke, and existing Python/Node/Compose regressions.
4. Add the no-SQL-after-transaction serialization regression in the first phase that introduces a transactional response DTO; Phase 1 has no such endpoint, so a synthetic test is intentionally deferred.

## Status

Status: DONE_WITH_CONCERNS

Summary: Current source remediates all Stage 2 findings and no additional blocking defect was demonstrated. Runtime acceptance remains blocked by the disk/Docker gates and the stale ignored target requires a clean verification run.

Concerns/Blockers: C below PASS threshold; Docker stopped; Java 21/Maven/PostgreSQL/image/analytics regression evidence missing; alternate Maven POM/settings flags remain a future hardening question.
