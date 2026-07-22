# Phase 4 master-data production review — 2026-07-22

## Code Review Summary

### Scope

- Diff: `bb63cd4..HEAD`.
- Focus: Field, Crop, Season master-data HTTP/application/persistence contracts; V8 lifecycle migration; farm-scope authorization; idempotency/ETag/If-Match; PostgreSQL concurrency and RLS boundaries.
- Review mode: CK two-pass critical + informational checklist, executed sequentially in this session. Independent scout/reviewer agents were unavailable because the service quota was exhausted; no external review result is represented as completed.

### Verification evidence

- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository verify`: exit code 0, `BUILD SUCCESS`, 02:56 min.
- Surefire report aggregate: 261 unit cases, failures/errors/skips 0.
- Failsafe summary for the completed run: 53 integration cases, failures/errors/skips 0.
- `git diff --check`: clean.
- Concurrency coverage passed for live season/field/crop deactivation races, parent-first farm deactivation races, and assignment revocation during a scoped write.
- Fresh Flyway migration and V8 upgrade/inconsistent-data checks passed; database trigger failures are surfaced as expected conflicts rather than silent writes.
- Secret gate: `.env` is ignored; scan excluding dotenv, build output, and `tmp/` found zero secret-match files.

## Critical issues

None found in the reviewed slice.

The review specifically checked tenant-bound SQL predicates, hidden parent-resource behavior, idempotency claim ordering, direct-store bypasses, RLS policy coverage, and database-enforced parent/lifecycle invariants.

## High priority

None found in the reviewed slice.

The previously identified high-risk paths were corrected and regression-tested:

1. Tenant-wide Field writes now use tenant scope when the actor is authorized for the tenant, while farm-manager writes remain assignment-scoped.
2. Season variety validation is capped at the schema contract (`VARCHAR(160)`), preventing API/database drift.
3. Parent farm visibility is checked before idempotency claim/mutation for Field and Season commands, returning a hidden 404 instead of leaking a downstream conflict.
4. FARM-scoped writes lock the active assignment row until commit. Direct Field/Season creates now carry the same authorization predicate, closing the precheck/write TOCTOU window.

## Medium priority / deferred

### Active responsible-employee lifecycle race

Field create/lifecycle SQL checks that the responsible employee is active, but the employee row is not locked and the Field mutation update relies on the service precheck. The current schema also does not include a trigger that prevents a later employee deactivation from leaving an active field linked to an inactive employee.

This is a real cross-boundary gap, but Employee lifecycle/workforce APIs are not implemented in this slice. Fix in the workforce phase by choosing and testing one explicit invariant: lock the employee row for field writes plus serialize employee deactivation, or add a database trigger/transition contract that rejects the deactivation. Do not mark Phase 4 complete until that decision has an integration test.

## Low priority / informational

- The phase plan still names monolithic `FieldController`, `CropController`, and `SeasonController` files, while the implementation is intentionally split into create/read/update/lifecycle controllers. Plan documentation should be synchronized before the next slice.
- Docker MCP sandbox containers are external tooling state, not application artifacts. Do not use broad `docker system prune`; remove only exact stale IDs after confirming they are not active sessions.

## Edge cases checked

- Unassigned FARM_MANAGER guessing a parent farm: hidden 404 before command claim.
- Assignment revoked while a write is waiting: locked authorization returns false after revoke commits.
- Farm deactivation racing with child Field/Season/Crop insert/update: parent-first and child-first directions are serialized or rejected by V7/V8 database guards.
- Active season blocks Field/Crop deactivation; inactive parent blocks new Season creation.
- Repeated idempotency keys, stale `If-Match`, unknown JSON fields, bounded page size, duplicate canonical codes, and schema-length boundaries remain covered by unit/HTTP/integration tests.

## Overall assessment

The Field/Crop/Season master-data slice is production-shaped for its declared boundary: tenant and farm scope are explicit, HTTP commands are versioned/idempotent, lifecycle invariants are backed by PostgreSQL, and the race scenarios that matter for current parents are executable tests. Phase 4 remains **in progress** because workforce, activity, assignment, log, and harvest contracts are still open and the responsible-employee lifecycle invariant is deferred to that work.

## Recommended actions

1. Update Phase 4 plan and architecture summaries to reflect the verified Field/Crop/Season slice and split controller structure.
2. Implement Employee/Farm Assignment contracts next; add the active-employee/active-field serialization test before exposing workforce lifecycle routes.
3. Keep the Docker Hub push behind the Phase 7 release gate; publish only immutable semantic-version/SHA tags after image scan, SBOM/provenance, and digest smoke tests.

## Metrics

- Build/type check: pass (Java release 21 compiler configuration).
- Unit tests: 261 passed, 0 failed/error/skipped.
- Integration tests: 53 completed run, 0 failed/error/skipped.
- Critical findings: 0.
- High findings: 0.
- Medium findings: 1 deferred to workforce phase.

## Unresolved questions

- Should employee deactivation be rejected while any active field references the employee, or should field responsibility be cleared through an explicit audited command?
- Which production identity provider and Docker Hub release credentials will be used at Phase 7?
