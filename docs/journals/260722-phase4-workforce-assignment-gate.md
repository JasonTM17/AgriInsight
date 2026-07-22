# Phase 4 workforce and assignment gate — 2026-07-22

## What changed

Employee was not treated as a thin picker table. It now has tenant-wide master CRUD/lifecycle, a separate redacted picker contract, optimistic versions, audit events, and database guards preventing inactive employees from owning live fields or activity assignments.

Farm assignment now has explicit tenant-admin grant/revoke commands, append-preserved history, exact route registration, idempotency, ETag/If-Match, tenant-safe FK access, and V10 profile lifecycle serialization.

## Failure that mattered

The first assignment implementation passed happy-path tests but violated idempotent replay. `requireGrantTargets()` checked whether the assignment was already active before the command executor claimed or replayed the key. After the first successful request, the exact retry looked like a duplicate and returned 409.

This was not cosmetic. A client retry after response loss is one of the main reasons durable command records exist. Blocking it before replay would make the API unreliable under normal network failure.

## Decision

- Before command claim: verify permission, active/visible parent targets, and immutable command shape only.
- Inside the mutation: reject a different command that attempts a duplicate active grant.
- On replay: reconstruct the current tenant-authorized assignment representation.

The split is now covered by a regression test. Do not move mutable-state checks back into pre-claim validation.

## Database lesson

Application checks alone could not close assignment/profile races. V10 makes both transaction orders lock the same profile row:

- assignment first: deactivation waits, then sees the active assignment and fails;
- deactivation first: grant waits, then sees the inactive profile and fails.

The migration also rejects inconsistent V9 data before installing triggers and proves rollback preserves FORCE RLS.

## Verification

- Combined workforce/assignment gate: 53 unit/HTTP + 20 integration, all pass.
- V10 broad gate: 265 unit + 11 migration/ownership, all pass.
- C/D after gate: about 16.79 GB / 29.10 GB free.
- Testcontainers cleaned its PostgreSQL/Ryuk containers.
- `.env` ignored; tracked secret scan found only the deliberate private-key sentinel test.

## Next

Activity/task status, activity assignments, immutable log corrections, and harvest facts remain the real Phase 4 blockers. Frontend work can use the stable employee/farm-assignment contracts, but work-management screens should wait until the activity contracts pass the same gate.

## Unresolved questions

- Farm-assignment admin read/list contract for UI discovery.
- Strict role-before-assignment ordering versus independently orderable admin commands.
- Docker Hub namespace and protected release credentials for Phase 7.
