# Backend Phase 6 Acceptance

Date: 2026-07-22  
Status: ACCEPTED

## Accepted boundary

- Append-oriented `operating_cost_entries` ledger with six migrated categories: `LABOR`, `MATERIAL`, `MACHINERY`, `TRANSPORT`, `UTILITY`, and `OTHER`.
- Exactly one canonical target per entry: `TENANT`, `FARM`, `FIELD`, `SEASON`, or `ACTIVITY`; nullable one-hot tenant-scoped foreign keys are validated by database constraints and service validation.
- Positive VND `NUMERIC(19,2)` values, UTC `Instant` timestamps, immutable `POSTING`/`REVERSAL` rows, and correction as one exact reversal plus one replacement.
- Bounded list/detail/summary reads with stable ordering, explicit date/page caps, tenant-leading indexes, and hierarchy-derived farm/season dimensions.
- Secured routes: `GET /api/v1/cost-entries`, `GET /api/v1/cost-entries/{id}`, `GET /api/v1/cost-summaries`, `POST /api/v1/cost-entries`, and `POST /api/v1/cost-entries/{id}/corrections`.
- Tenant Admin manages; Executive/Data Analyst read tenant-wide; assigned Farm Manager reads assigned farms; Inventory Manager and Supplier receive no operating-cost permission.
- Operating cost, procurement spend, and inventory value remain separate lenses. No Python adapter, Gold writer, SQLite access, or outbox was added; Phase 7 remains the machine handoff boundary.

## Acceptance evidence

| Gate | Result |
|---|---|
| Disk guard | PASS before/after heavy work; C stayed above 10 GB and D above 25 GB (final observed C 10.477 GB, D 25.300 GB) |
| Guarded backend `verify` | PASS — 442 Surefire + 96 Failsafe tests; zero failures, errors, and skips; build SUCCESS in 6:02 |
| Cost focused suite | PASS — 26/26 tests (18 unit/HTTP/OpenAPI + 8 PostgreSQL integration/concurrency/query-plan) |
| Flyway/PostgreSQL | PASS — fresh PostgreSQL 18 applies 18 migrations, latest version `17`, repeatable grants converge; V3 adoption upgrades with 15 migrations |
| Ledger invariants | PASS — one-hot target, positive precision, reversal lineage, no double reversal, command binding, cross-tenant FK rejection |
| Correction concurrency | PASS — exactly one concurrent correction wins; one reversal and one replacement remain committed; replay is idempotent |
| Scope/RLS | PASS — forced RLS, role-specific cost policies, assigned-farm visibility, tenant target hidden from farm manager, inventory/supplier denial |
| Query plans | PASS — tenant-leading list/summary indexes selected; limits/date windows are bounded |
| HTTP/OpenAPI | PASS — unknown fields rejected, Problem Details/error contracts, idempotency, route registry, operation descriptions/examples, separate summary lens |
| Analytics regression | PASS — Python `75 passed, 3 skipped`; existing Cost Analysis/export contracts unchanged |
| Secret/git hygiene | PASS — `git diff --check`; `.env` ignored; no token/private key staged; `tmp/` remains untracked working residue |

## Security and data guarantees

- V17 uses `ENABLE/FORCE ROW LEVEL SECURITY` and a security-definer access function that resolves the canonical target's farm through field/season/activity parents. Read and insert policies are distinct.
- Application scope is checked before store access, and targeted farm detail reads apply the resolved-farm predicate in SQL as defense in depth.
- Correction commands lock the tenant/original entry with a transaction advisory lock, then rely on unique reversal/command constraints. A retry returns the committed representation without appending a second correction.
- The API response does not expose tenant internals on entry detail/list DTOs; summary responses label tenant, lens, source, period, and optional season variance explicitly.
- No delete route exists. Forward correction is the only supported mutation of a posted financial fact.

## Deferred by design

- Python reporting read adapter, Gold cost frame refresh, SQLite synchronization, transactional outbox, and external event delivery: Phase 7.
- COGS/allocation semantics, inventory-to-operating-cost attribution, procurement-to-cost joins, realtime alerts, ML, AI/Text-to-SQL, and production web frontend remain out of scope.
- Production OIDC/MFA, audit retention, backup/restore RPO/RTO, protected CI, SBOM/provenance, and Docker Hub/GitHub Packages publication remain release inputs.

## Review and rollback

The lead applied CK cook/backend/test/code-review checklists: boundary validation, idempotency actor binding, RLS role separation, hierarchy joins, error propagation, concurrency locking order, data exposure, and query-plan evidence. A full gate initially exposed one stale policy-count assertion (`59` versus the new `62` policies); the assertion was corrected to reflect the three V17 cost policies, and the complete rerun passed.

Rollback is forward-only: disable cost write routes if necessary, preserve immutable rows, and ship a repair migration. Do not edit V16/V17 or delete posted facts. Python Gold and local reports continue independently while Phase 7 is pending.

## Unresolved questions

- Production OIDC provider/MFA and privileged-operation policy.
- Audit retention and backup/restore owner/RPO/RTO.
- Docker Hub namespace, repository visibility, and least-privilege release token.
