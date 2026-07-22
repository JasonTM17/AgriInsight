# Code Standards

This repository contains a Python analytics plane and a Java operational backend. Standards differ slightly by stack, but the same rules apply everywhere: keep behavior real, keep scope tight, and fail closed.

## Repository layout

| Path | Purpose |
|---|---|
| `src/` | Python analytics package |
| `dashboard/` | Streamlit app and report presentation code |
| `backend/` | Java Spring Boot operational backend |
| `scripts/` | Operational and verification scripts |
| `docs/` | Evergreen project documentation |
| `plans/` | Implementation plans and phase reports |
| `tests/` | Python tests |

## General standards

- Verify behavior in code before documenting it.
- Keep docs and contracts in sync with implementation.
- Do not commit secrets, tokens, private keys, or machine-specific runtime paths.
- Prefer explicit failure over silent fallback.
- Use descriptive names and small files when a module starts to accumulate too many concerns.

## Python analytics standards

- Keep pipeline runs deterministic where possible.
- Preserve Gold contracts and manifest integrity.
- Do not let reporting recompute canonical KPI logic in the UI layer.
- Keep export logic fail-closed on invalid filters, empty results, or size limits.

## Java backend standards

- Target Java 21.
- Use Spring Boot with problem-detail responses for client-facing errors.
- Keep security deny-by-default until a route is explicitly allowed.
- Validate external JWT signature/algorithm, issuer, API audience, time claims, subject, and the configured access-token discriminator before identity lookup.
- Resolve external identities by exact `(issuer, subject)` and retain only the internal principal in the security context; never log/store raw bearer tokens or trust JWT role/tenant claims for row scope.
- Register every business mapping as an exact HTTP method + Spring `PathPattern`; endpoint inventory and `anyRequest().denyAll()` must catch omissions.
- Authorize at both boundaries: the route registry checks the minimum permission, and a scoped application service rechecks permission/scope before repository or idempotency work.
- Every tenant-owned service entry point uses `@TenantScoped`; the outer transaction binds the database-verified tenant and, for warehouse-scoped work, profile with transaction-local `set_config(..., true)` before data access. Never use session-level tenant settings or inherit request scope into async work.
- Let a rejected business transaction roll back and release its connection before opening an independent authorization-denial audit transaction. Never nest that audit while the outer transaction owns the only pooled connection.
- Pass `ScopeContext` into tenant repositories or keep repositories package-private behind a scoped service. Unknown domain scope resolvers fail closed until their FK-backed module is installed.
- Run the application as the restricted non-owner runtime role. Migration owner, operator, and identity-definer privileges are separate; runtime must never be superuser, table owner, `CREATEROLE`, or `BYPASSRLS`.
- Keep transactional outbox persistence and drain fencing in the `integration` module. Do not add a broker, public drain route, or scheduler in code that still runs inside the Phase 7 handoff boundary.
- Tenant tables require `tenant_id`, composite tenant-aware relationships where applicable, `ENABLE/FORCE ROW LEVEL SECURITY`, one reviewed permissive tenant policy per command, matching `USING`/`WITH CHECK`, and direct SQL isolation tests.
- State-changing routes require a bounded idempotency key and canonical validated fingerprint bound to tenant, principal, method, route, path/query/body, and semantics-bearing headers. Store only the key digest and fixed-size replay metadata, never raw keys, credentials, request bodies, or response snapshots.
- Authorization must run before an idempotency key is claimed. Replay may return only a currently authorized representation.
- Keep identity disabled when the complete provider contract is absent. Provider URLs use HTTPS outside loopback development; CORS origins are exact allowlist values.
- Use UUIDs for operational identifiers and canonical ASCII business codes where needed.
- Persist timestamps in UTC.
- Keep Open Session in View disabled.
- Use Flyway for schema changes and treat applied migrations as immutable history.
- Keep backend runtime data out of `artifacts/`.

## Inventory and migration standards

- Treat V12-V19 as immutable applied history: V12 inventory tables, V13 tenant
  RLS, V14 active profile/warehouse assignment lifecycle, V15 role-aware
  inventory read/write policies plus indexes, V16 operating-cost ledger, V17
  cost RLS and indexes, V18 outbox tables, and V19 outbox RLS/index policies.
- Inventory tables use tenant-aware composite foreign keys, active warehouse and
  material/supplier checks, and PostgreSQL ENABLE/FORCE RLS. Warehouse-scoped
  queries must carry both tenant and profile context and evaluate active
  assignments in the database, not only in the UI.
- `inventory_transactions` and lot allocations are append-only for runtime.
  Posted facts change only through linked service-generated reversals; never
  update/delete a ledger row or silently repair a projection in a request.
- `RECEIPT` and `ISSUE` request shapes are mutually exclusive. Receipt finance
  and supplier data are server-derived/validated; issue cannot accept receipt
  finance fields. Reversals derive direction, allocation, supplier, and money
  from the original.
- Use `BigDecimal`/`NUMERIC` for quantities and VND, explicit scale boundaries,
  deterministic `(expiry_date, received_at, id)` locks, and FEFO selection. A
  reconciliation query detects ledger, allocation, lot, and balance drift
  without mutating source data.
- Canonical material API quantities are base units (`KG`, `LITER`, `PIECE`).
  Tonne conversion belongs to an import boundary and must convert quantity and
  unit price together; do not add implicit API conversion.
- Every controller mapping is registered in the exact route inventory. Springdoc
  remains disabled by default; any inventory OpenAPI example must be backed by a
  contract test.

## Operational standards

- Use `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify` for backend verification.
- Use `scripts/run-backend-migrations.ps1` for role bootstrap, optional allowlisted ownership adoption, Flyway migrate, and Flyway validate; never enable application-driven production migrations.
- Keep Maven repo, temp, and user-home output on `D:`.
- Run the disk guard before expensive backend or Docker work.
- Treat blocked gates as evidence, not as a reason to rewrite the status.

## Naming

- Markdown docs use kebab-case filenames.
- Java packages remain under `com.agriinsight.backend`.
- Backend source files follow Java naming conventions.
- Generated build output stays ignored.

## Testing expectations

- Add focused tests for new behavior.
- Cover both happy path and failure path where the contract can fail.
- Do not weaken tests to hide a blocked integration gate.
- Keep backend verification separate from analytics verification when the stacks diverge.

## Farm lifecycle standards

- Farm create and lifecycle operations require tenant-wide scope; update may use assigned-farm or tenant-wide scope.
- Farm list/read/update/lifecycle mappings stay exact, permission-bound, and hidden as `404` when outside the caller's authorized visibility.
- State-changing routes require canonical `Idempotency-Key`; update/deactivate/reactivate also require strong `If-Match` version checks.
- Deactivation fails closed while a field, planned/active season, planned/started activity, or unrevoked assignment remains live.
- Application lifecycle transactions explicitly use READ_COMMITTED. Parent deactivation and live-child writes must lock the same farm row so both race orderings serialize.
- Upgrade preflight and rollback must preserve ENABLE/FORCE ROW LEVEL SECURITY; migration failures must not weaken tenant isolation.
