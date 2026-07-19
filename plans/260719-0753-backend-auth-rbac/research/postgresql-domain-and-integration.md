# Research: PostgreSQL domain, RLS, and integration

Date: 2026-07-19

## Evidence

- Current Python contracts have no tenant identifier and use regenerated SQLite surrogate keys. `farm_code`, `field_code`, `season_code`, `activity_id`, `warehouse_code`, `material_code`, `supplier_code`, and `transaction_id` are the stable business identifiers.
- The Python pipeline is the sole writer of Bronze/Silver/quarantine/Gold, SQLite, and `manifest.json`. Dashboard/reporting reads validated artifacts and uses manifest/checksum fencing. Java must therefore own a separate operational PostgreSQL database; backend runtime code must not publish or mutate analytics artifacts. Build tooling may use the existing ignored `artifacts/_tmp` scratch area.
- PostgreSQL RLS is default-deny when enabled without a policy; table owners and superusers can bypass it unless `FORCE ROW LEVEL SECURITY` and least-privilege roles are used: <https://www.postgresql.org/docs/current/ddl-rowsecurity.html>.
- PostgreSQL `set_config`/`current_setting` support transaction-local request context: <https://www.postgresql.org/docs/current/config-setting.html>.
- Flyway validates migration names/checksums and fails on drift: <https://documentation.red-gate.com/flyway/reference/commands/validate>.
- Spring Boot supports Testcontainers service connections and a PostgreSQL container: <https://docs.spring.io/spring-boot/reference/testing/testcontainers.html> and <https://java.testcontainers.org/modules/databases/postgres/>.
- Spring Data JPA auditing can supply created/modified metadata: <https://docs.spring.io/spring-data/jpa/reference/auditing.html>.

## Isolation options

| Option | Strength | Cost | Decision |
|---|---|---|---|
| Application predicates only | Simple ORM/query behavior | One missed predicate can leak another tenant | Not sufficient as the sole backstop. |
| RLS only | Database-enforced tenant isolation | Awkward assignment rules, admin/system jobs, and pooled context | Too opaque as the sole business policy. |
| Hybrid (selected) | App handles role/farm/warehouse/task policy; RLS blocks tenant leakage | Requires transaction-context and direct SQL tests | Best balance for this modular monolith. |

## Domain decisions

- Tenant equals enterprise for this milestone; hierarchy is `tenant -> farm -> field -> season -> activity/harvest` and `tenant -> warehouse -> inventory`.
- Every tenant-owned table carries `tenant_id`, even when derivable, and uses composite parent constraints/indexes where practical.
- UUIDs are internal/public IDs. Canonical business codes are trim+uppercase and unique per tenant. Python `*_key` values are not persisted as integration IDs.
- Mutable masters have audit/version columns and selective soft delete. Operational facts are append/correction/reversal records.
- Money is `NUMERIC`/`BigDecimal` in VND; timestamps are UTC `TIMESTAMPTZ`/`Instant`; business periods use `DATE`/`LocalDate`; units normalize explicitly.
- Inventory uses an immutable movement ledger plus a locked current-balance projection. Operating cost, procurement spend, and inventory value remain separate lenses; no COGS/allocation is invented.

## RLS implementation invariant

The request aspect starts/joins a transaction and executes `select set_config('app.tenant_id', :tenant, true)` before any user query. `app_current_tenant_id()` returns NULL if absent; policies then deny. The JDBC runtime role is not owner/superuser/BYPASSRLS, while migration ownership is separate. Direct Testcontainers tests use the restricted role and exercise connection-pool reuse, `USING`, `WITH CHECK`, and cross-tenant parent references.

Farm/warehouse/activity assignment is an application concern (`user_farm_assignments`, `user_warehouse_assignments`, activity assignees); it is not hidden inside a complex RLS subquery. This keeps authorization explainable and leaves RLS as a hard tenant backstop.

## Integration boundary

Each successful domain command writes its operational row and a versioned outbox event in one PostgreSQL transaction. The outbox is at-least-once and idempotency-keyed. A future Python consumer will translate events/business codes into a versioned Bronze contract; it will regenerate Gold through the existing quality gates. No phase in this plan mutates Gold, SQLite, `manifest.json`, or the existing v1 cost frames.

## Required tests

1. Fresh migration + Flyway checksum validation.
2. Tenant A/B direct SQL reads/writes with absent and wrong context.
3. Parent/child tenant mismatch, role assignment, optimistic locking, idempotent commands.
4. Inventory row-lock concurrency and ledger/balance reconciliation.
5. Domain rollback leaves no outbox event; retry/lease does not duplicate an event.
6. Python regression and artifact-boundary tests prove no backend write path exists.

## Unresolved deployment choices

- PostgreSQL minor image/digest can be selected during implementation. Production RPO, RTO, retention, encrypted off-host backup destination, and restore ownership must be approved and proven by a clean restore drill before production deployment.
- The schedule and shape of the future Python outbox consumer require a separate Stage 3 integration plan.
