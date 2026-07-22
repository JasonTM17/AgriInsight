# System Architecture

AgriInsight is split into two planes.

## Contents

- [Analytics plane](#analytics-plane) - current Bronze/Silver/Gold pipeline, artifacts, and dashboard.
- [Operational backend](#operational-backend) - separate Java Spring boundary for operational state.
- [Boundaries](#boundaries) - what each plane owns and what it must not touch.
- [Current status](#current-status) - what is verified today and what is still blocked.

## Analytics plane

The analytics plane is the current validated MVP.

- Python pipeline generates Bronze, Silver, quarantine, warehouse, Gold, and manifest artifacts.
- Streamlit reads Gold contracts and renders operational views for the analytics MVP.
- Reporting is derived from normalized Gold inputs and stays local/internal.

```mermaid
flowchart LR
    A["Source data"] --> B["Bronze"]
    B --> C["Validation and quarantine"]
    C --> D["Silver"]
    D --> E["Warehouse and Gold"]
    E --> F["Dashboard and reports"]
```

## Operational backend

The backend is a separate Java 21 Spring Boot project under `backend/`.

Verified foundation, identity, and tenant-authorization boundary currently present in source:

- Java 21/Spring Boot application and Spring Modulith boundary
- deny-by-default stateless OAuth2 resource server
- issuer, audience, signature/algorithm, time, subject, and access-token discriminator validation
- exact `(issuer, subject)` bootstrap to active profile and tenant
- database-backed role/permission enrichment before route authorization
- exact route registry and tenant-administration APIs for users, external identities, role assignments, and farms
- one `@TenantScoped` business transaction that binds `app.tenant_id` before repository access
- restricted runtime/migration/identity-definer PostgreSQL roles and `ENABLE/FORCE ROW LEVEL SECURITY`
- fixed-size canonical command records for tenant/principal/route-bound idempotency
- durable role, user, identity, conflict, and authorization-denial audit events
- correlation IDs and redacted `application/problem+json` responses
- liveness/readiness split and Flyway V1-V11 migrations, including serialized Field/Crop/Season, Employee, farm-assignment, and activity-season lifecycle guards

```mermaid
flowchart LR
    T["Bearer JWT"] --> V["Signature and claim validators"]
    V --> I["Exact issuer + subject bootstrap"]
    I --> E["Tenant transaction: roles + permissions"]
    E --> R["Exact route registry"]
    R --> B["TenantScoped business transaction"]
    B --> C["set_config(app.tenant_id, true)"]
    C --> P["Application predicates + FORCE RLS"]
    R --> D["Unregistered route: deny + audit"]
```

The request never accepts tenant scope from a header, path, or JWT tenant claim. The exact identity bootstrap is the only pre-tenant database operation. `TenantPrincipalLoader` then opens a short transaction, binds the database-verified tenant, loads the active profile plus fixed roles/permissions, closes that transaction, and only then lets Spring evaluate the exact route registry.

Every operational service entry point owns a separate `@TenantScoped` transaction. Its outer aspect binds the same tenant with transaction-local `set_config` before any repository query. Missing or mismatched context fails closed, and the restricted runtime role remains subject to both application predicates and PostgreSQL FORCE RLS.

Authorization denial audit follows a deliberate ordering invariant: the rejected business transaction rolls back and releases its connection first; only then may an independent audit transaction bind the tenant and persist the denial. This prevents pool exhaustion/deadlock when the pool has one connection. Audit persistence failure keeps the client response at a generic 403 and emits only a redacted operational error type.

Mutation routes authorize before claiming an idempotency key. The command store binds a SHA-256 key digest and canonical request hash to tenant, principal, method, and route template. It stores no request body, raw key, token, or response snapshot; committed replay reconstructs a currently authorized representation.

The farm/field/crop/season master-data slice uses the same boundary for assignment-aware reads and mutations. FARM-scoped writes lock active assignments until commit; tenant-wide administrator writes remain available where the permission matrix allows them. Lifecycle transactions explicitly use READ_COMMITTED; parent deactivation and live-child inserts/updates lock the farm row in a consistent order. V7/V8 preflight fails closed on inconsistent upgrade data, and rollback preserves ENABLE/FORCE RLS.

Employee full-master access is tenant-wide, while `WORKFORCE_PICKER_READ` returns a redacted active-only projection. V9 locks active employee parents for live field/activity responsibility and blocks deactivation until dependencies close. Farm grants are append-preserved rows: revoke increments the version and never deletes/reactivates history; re-grant creates a new row. V10 locks the active profile during grant and rejects profile deactivation while an active farm assignment exists, covering both concurrency orders.

Activities use tenant, assigned-farm manager, or assigned-worker scope before paging. Task transitions and metadata updates are versioned; assignment revoke preserves history. Activity evidence is append-only, accepts bounded URI metadata without fetching it, and represents corrections as linked rows. V11 serializes live activity and season transitions. Harvest facts are also append-only, normalize KG/TONNE to kg at the API boundary, and keep correction lineage without introducing Phase 6 operating costs.

## Boundaries

| Plane | Owns | Does not own |
|---|---|---|
| Analytics | artifacts, Gold contracts, local reporting, dashboard views | PostgreSQL operational state, OIDC/RBAC, backend images |
| Backend | operational API boundary, OIDC identity, tenant RBAC/RLS, tenant administration, idempotency, health, PostgreSQL schema history | `artifacts/`, Gold CSVs, SQLite warehouse, report generation |

## Current status

| Area | Status |
|---|---|
| Analytics MVP | Verified by its existing regression suite |
| Backend phase 1 foundation | Accepted 2026-07-19 |
| Backend phase 2 OIDC identity | Accepted 2026-07-20 |
| Backend phase 3 tenant RBAC/RLS | Accepted 2026-07-20; current backend regression gate remains green |
| Tenant administration | Exact user/role/farm-assignment mutation routes verified |
| Backend phase 4 operations | Accepted 2026-07-22; farm/season/workforce/activity/log/harvest gates green |
| Docker Hub publication | Not yet claimed |
| Local backend image verification | Phase 2 non-root build/smoke verified; no registry provenance |

The right way to read the repo is: analytics and backend phases 1-4 are locally verified. Inventory, cost, outbox, production identity operations, and release publication remain sequential gates, so Phase 4 acceptance is not a full production-release claim.
