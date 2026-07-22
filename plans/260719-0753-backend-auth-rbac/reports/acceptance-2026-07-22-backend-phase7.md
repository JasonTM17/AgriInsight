---
phase: 7
title: "Backend Phase 7 acceptance — outbox, images, and recovery boundary"
date: 2026-07-22
status: in-progress
---

# Backend Phase 7 acceptance

This report records the evidence collected before the protected registry
publication and the clean-cluster backup/restore drill. The phase remains
open until those two release-boundary checks are evidenced.

## Evidence collected

| Gate | Result | Evidence |
|---|---|---|
| Workspace disk guard | PASS | C: 11.08 GB free; D: 25.20 GB free at report creation. Thresholds remain C 10/8 GB and D 25/20 GB. |
| Outbox atomicity | PASS | `OutboxAtomicityIntegrationTest`; committed event persisted with the domain command and rollback persisted neither. |
| Outbox lease/retry | PASS | `OutboxLeaseIntegrationTest`; aggregate ordering, lease fencing, stale acknowledgement rejection, bounded retry, and terminal dead-letter. |
| Outbox application unit tests | PASS | `CommandExecutionServiceTest` 5 tests and `OutboxDrainServiceTest` 1 test. |
| Tenant/RLS regression | PASS | `TenantRlsIntegrationTest` 9 tests. |
| Guarded backend verification | PASS | `scripts/run-backend-tests.ps1 verify`: 255 suites, 622 tests, 0 failures, 0 errors, 0 skipped; Flyway V1–V19 and PostgreSQL 18 integration completed successfully. |
| Python regression | PASS | `python -m pytest`: 76 passed, 3 expected optional-PDF skips in 38.44s. |
| Python/package checks | PASS | `compileall`, Node syntax check, wheel build/smoke, and SHA-256 artifact check. |
| Compose contract | PASS | Root Compose and backend profile both pass `docker compose ... config --quiet`; backend data binds to ignored D-local storage and artifacts are not mounted writable. |
| Image boundary | PASS | Root Python and backend Java images build locally with pinned base digests, OCI labels, non-root users, and deterministic smoke checks. |
| Documentation validation | PASS | `node .claude/scripts/validate-docs.cjs docs/` exits 0; 12 internal links and 10 configuration references verified. Existing validator warnings are false positives for code/class names. |

## Phase 7 implementation covered

- Flyway V18/V19 `outbox_events` schema, FORCE RLS, order/claim indexes, and
  least-privilege integration grants.
- Typed committed-command event contract and JSON Schema v1.
- Transactional outbox writer, fenced `FOR UPDATE SKIP LOCKED` drain, retry
  backoff, stale lease rejection, and dead-letter terminal state.
- Loopback-only backend Compose overlay with role bootstrap/migration/runtime
  separation and D-local PostgreSQL data.
- Pinned, non-root first-party Python/backend images plus pull-request CI and
  protected Docker Hub/GHCR publication workflow.
- Disk-guarded custom-format PostgreSQL backup/restore wrappers and recovery
  documentation.

## Remaining acceptance gates

1. Push non-production phase tags to Docker Hub and GHCR, then record the
   returned immutable digests and exact-digest smoke checks. Production release
   still requires the protected workflow, reviewer approval, and configured
   `DOCKERHUB_NAMESPACE`/environment secrets.
2. Run the D-local backup -> clean target database -> role bootstrap -> restore
   -> Flyway/RLS/runtime smoke drill and record measured restore time.
3. After both gates pass, mark this phase complete and update the roadmap and
   plan status. No `latest` tag or PostgreSQL republish is allowed.

## Unresolved questions

- Production owner must approve RPO, RTO, retention, encrypted off-host backup
  storage, and the restore operator before any production deployment claim.
