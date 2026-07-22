# Phase 4 Operations Production Review

Date: 2026-07-22
Scope: activity lifecycle, assignments, worker scope, immutable logs, harvest ledger
Result: PASS — no release-blocking finding for Phase 4 boundary

## Review scope

- Delta reviewed: `f43da67..1bc5ec4`
- 106 changed backend files, 7,460 insertions, 6 deletions before final test-only commit
- Exact route registry, authorization-before-idempotency ordering, input validation, tenant/farm/activity scope, persistence, RLS grants, concurrency, error propagation, and response redaction inspected
- Spring Modulith `ModuleBoundaryTests`: 1 pass

## Critical and high findings

None open.

## Verified risks

- Manager reads/writes require a live assigned-farm grant; revocation immediately removes activity/harvest visibility and writes.
- Worker reads require active role/profile/employee/activity assignment. Worker cannot create tasks, reassign workers, spoof another employee, or correct another author's log.
- Activity and harvest facts are append-only at runtime. Corrections create linked rows; no UPDATE/DELETE capability is granted.
- Command services authorize hidden targets before claiming an idempotency key. Replay resolves the current authorized representation.
- Activity-season transitions contend on the same season row through V11; both race orders are tested.
- Harvest API normalizes KG/TONNE to kg before canonical fingerprinting and persistence. Waste, precision, revenue, hierarchy, and VOID shape are validated.
- Evidence URI is metadata only, length/scheme allowlisted, and never fetched.
- List queries apply scope before stable bounded pagination; responses omit `tenantId`.

## Informational notes

- `PostgresActivityStore` and `PostgresHarvestStore` are 209 and 208 lines. Both already delegate mapping/scope concerns; no functional split required now.
- Harvest list returns immutable ledger rows, including corrections. Consumers must reconcile correction lineage rather than sum raw rows; this is explicit in response fields.
- Production release still waits for Phases 5-7, IdP operations, CI/scans, and immutable registry publication.

## Verification

- Guarded backend verify: 353 unit/HTTP/security/module + 77 PostgreSQL integration = 430 pass, 0 failure/error/skip.
- Analytics regression: 65 pass, 3 expected optional-PDF skips.
- Disk guard after verification: C 11.279 GB, D 28.251 GB, both PASS.
- Testcontainers cleanup: no AgriInsight/PostgreSQL test container remains.

## Unresolved questions

None for Phase 4 acceptance.
