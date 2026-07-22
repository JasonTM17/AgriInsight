# Backend Phase 5 Acceptance

Date: 2026-07-22
Status: ACCEPTED

## Accepted boundary

- Versioned warehouse, material, supplier, warehouse-assignment, stock-balance, stock-lot, movement, and linked-reversal APIs under `/api/v1`.
- Immutable PostgreSQL inventory ledger with lot allocations, FEFO issue selection, stock-lot projection, and warehouse/material balance projection updated in one transaction.
- Receipt, issue, and reversal commands enforce canonical field shapes, positive quantities, VND monetary precision, strong `If-Match`, and tenant-scoped idempotency.
- Warehouse assignment scope is enforced in the application authorization layer and PostgreSQL FORCE RLS. Tenant Admin can write tenant inventory; assigned Inventory Manager can read/write assigned warehouses; Executive/Data Analyst can read tenant-wide; assigned Farm Manager can read; Supplier has no inventory permission.
- Reconciliation detects ledger, allocation, lot, and balance drift without silently repairing source records. Backend PostgreSQL inventory facts remain separate from Python Gold/SQLite contracts.
- OpenAPI examples and operation descriptions are contract-tested; API docs are disabled by default and only exposed through an explicit development profile.

## Acceptance evidence

| Gate | Result |
|---|---|
| Disk pre/post guard | PASS — C 10.925 GB, D 25.823 GB after guarded verify; thresholds 10/8 GB for C and 25/20 GB for D |
| Backend guarded `mvn verify` | PASS — 487 Surefire + 92 Failsafe tests; zero failures, errors, and skips |
| Inventory focused suite | PASS — 32/32 `*Inventory*Test` tests |
| PostgreSQL/Flyway | PASS — PostgreSQL 18, V1-V15 plus repeatable RLS/grants migration on fresh containers |
| RLS policy catalog | PASS — 59 permissive policies, FORCE RLS, role-aware inventory read/write policies, profile-bound warehouse scope |
| Ledger/projections | PASS — FEFO, explicit lot, no-negative balance, first-receipt race, issue/reversal race, rounding, and reconciliation drift scenarios |
| Warehouse lifecycle | PASS — active assignment/history prevents unsafe deactivation; inactive warehouses cannot accept or reverse inventory |
| HTTP/OpenAPI | PASS — route registry, MockMvc contract tests, `/v3/api-docs` summaries/examples, idempotency and ETag headers |
| Analytics regression | PASS — Python `65 passed, 3 skipped`; `python -m compileall -q src dashboard tests` exit 0 |
| Git/secret hygiene | PASS — `git diff --check`; `.env` remains ignored and no secret/token is staged |

## Security and data guarantees

- V15 binds `app.tenant_id` and `app.profile_id` transaction-locally; warehouse RLS evaluates active role plus active warehouse assignment.
- Inventory read and write policy paths are separate. A profile with a read-only role cannot insert/update projections or ledger rows even when assigned.
- Supplier and finance fields are derived from active server-side masters for receipts; clients cannot submit inverse finance data on reversals.
- Ledger rows are append-only for runtime; corrections are service-generated linked reversals with cumulative quantity and monetary bounds.
- Missing tenant/profile context fails closed; cross-tenant UUID guessing returns safe not-found/denied behavior.

## Deferred by design

- Operating-cost ledger and reporting boundary: Phase 6 (planned V16+ migrations; V15 is reserved for inventory hardening).
- Outbox, protected CI, dependency/image scans, SBOM/provenance, GitHub Packages, Docker Hub publication, and GitHub repository metadata: Phase 7/release gate.
- Production OIDC issuer/MFA, audit retention, backup/restore objectives, and Docker Hub namespace/token remain deployment inputs.
- Full Spring Boot + real PostgreSQL vertical HTTP mutation test is not a Phase 5 blocker; store/RLS integration and MockMvc HTTP contracts are independently covered and a vertical smoke is planned for release hardening.

## Review note

The automated reviewer subagent was unavailable because of an external usage quota. The lead applied the CK base/API and adversarial review checklists manually: trust boundaries, error propagation, idempotency replay, locking order, RLS role separation, data exposure, query/index behavior, and backward compatibility were inspected with focused tests and source evidence. No unresolved Phase 5 blocker remains.

## Unresolved questions

- Production identity provider, MFA and privileged-role operating policy.
- Docker Hub namespace and least-privilege CI token ownership.
- Phase 6 migration naming after V15 and final cost-to-inventory reporting contract.
