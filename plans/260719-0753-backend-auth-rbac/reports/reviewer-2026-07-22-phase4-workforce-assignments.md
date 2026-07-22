# Phase 4 workforce and farm-assignment production review — 2026-07-22

## Code Review Summary

### Scope

- Diff: `97cd652..edbfe77`.
- Focus: Employee master lifecycle, redacted picker, farm-assignment grant/revoke, tenant scope, durable idempotency, optimistic versions, V9/V10 migration safety, RLS, and two-way concurrency serialization.
- Review mode: CK two-pass critical + informational checklist, edge-case scouting, adversarial request replay review, and executable PostgreSQL checks.

### Verification evidence

- Combined current-slice Maven gate: 53 unit/HTTP cases and 20 integration cases, failures/errors/skips 0, `BUILD SUCCESS`, 01:35 min.
- V10 migration gate also ran the broad unit suite: 265 unit cases plus 11 migration/ownership cases, failures/errors/skips 0.
- Fresh Flyway applies 11 entries and reaches schema version 10; inconsistent V9 upgrade data fails closed and keeps FORCE RLS.
- Concurrency tests prove both transaction orders: assignment-first rejects profile deactivation after waiting; profile-deactivation-first rejects the waiting assignment.
- Endpoint inventory confirms every Employee and Farm Assignment controller mapping has an exact deny-by-default registry entry.
- `.env` is ignored. The tracked-source secret scan matched only the deliberate private-key sentinel strings inside `ConfigurationSafetyTest`; no credential-bearing source file was found.
- `git diff --check` clean. Testcontainers terminated its PostgreSQL/Ryuk containers after the run.

## Critical issues

None found after correction.

The review traced tenant identity, permission, parent visibility, idempotency claim ordering, SQL tenant predicates, RLS, optimistic versions, audit publication, and parent lifecycle locks.

## High priority findings corrected

### Assignment-grant replay was blocked before idempotency resolution

The initial grant pre-claim validation rejected an already-active assignment. A successful request retried with the same key would therefore receive 409 before `CommandExecutionService` could replay the committed representation.

The fix separates safe pre-claim checks (tenant permission, active target profile/farm, version-zero command shape) from mutation-only duplicate detection. Regression coverage proves pre-claim validation does not query active assignment state, while a genuinely new duplicate command still conflicts inside the mutation.

## Medium priority

None found in the reviewed boundary.

## Low priority / informational

- A farm-assignment row does not grant a role or permission. `FarmDomainScopeResolver` requires the `FARM_MANAGER` role and its permission in addition to an active assignment, so assigning another active profile creates an inert row rather than elevating access. The current normative route contract does not require the target role to exist first; if product workflow later requires strict ordering, add an explicit role invariant and serialize role revoke with assignment lifecycle.
- The public matrix intentionally exposes grant/revoke commands but no assignment list route. Administration UI discovery will need either a future bounded read route added to the normative matrix or state obtained from another approved user-management projection.

## Edge cases checked

- Missing permission stops before command-service interaction.
- Hidden/cross-tenant or inactive target profile/farm fails before idempotency claim.
- Same key replay can reach the executor after the original assignment exists.
- Different-key duplicate active grant conflicts without rewriting history.
- Revocation is one-way, versioned, and preserves the old row; re-grant creates a new row.
- Weak `If-Match`, missing command headers, and unknown JSON fields fail before command execution.
- Profile/employee/farm deactivation cannot race past active responsibilities or assignments.
- Cross-tenant UUID guessing remains invisible under tenant predicates and FORCE RLS.

## Overall assessment

Employee master data and tenant-admin farm assignments are production-shaped for their declared Phase 4 boundary. Employee full-read and redacted-picker contracts are separated; lifecycle blockers are enforced in Java and PostgreSQL; assignment history is append-preserved; and the relevant concurrency/idempotency races are executable tests.

Phase 4 remains **in progress**. Activity tasks, activity assignees, immutable logs/corrections, and harvest/correction APIs are not implemented or accepted yet.

## Recommended actions

1. Implement Activity domain/status transitions and farm/worker scope resolver next.
2. Reuse the V9 active-employee lock for activity assignment creation and test assignment/revoke races.
3. Add immutable activity-log author/assignee guardrails before any worker UI is connected.
4. Keep Docker Hub/GitHub package publication behind Phase 7 image scan, SBOM/provenance, digest smoke, and release-credential gates.

## Metrics

- Current slice unit/HTTP: 53 passed, 0 failed/error/skipped.
- Current slice integration: 20 passed, 0 failed/error/skipped.
- Critical findings: 0.
- High findings: 1 fixed and regression-tested.
- Medium findings: 0.

## Unresolved questions

- Should a future farm-assignment read/list route be added for the tenant-admin UI, or should assignment state be embedded in an approved user projection?
- Will product workflow require `FARM_MANAGER` role to exist before assignment grant, or keep role and resource assignment independently orderable?
- Which Docker Hub namespace and protected release credentials will be used in Phase 7?
