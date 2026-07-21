# Codebase Summary

Verified snapshot: 2026-07-21

## Repository shape

| Path | Responsibility |
|---|---|
| `src/agriinsight/` | Deterministic Bronze/Silver/Gold pipeline, quality, warehouse, KPI, insight, and report services |
| `dashboard/` | Streamlit composition and six analytics dashboard domains |
| `tests/` | Python pipeline, KPI, dashboard, export, security-boundary, and disk-guard tests |
| `backend/` | Java 21 Spring Boot operational backend |
| `scripts/` | C/D disk guard and guarded backend verification |
| `plans/` | CK phase plans, design contracts, reports, and acceptance evidence |
| `docs/` | Evergreen architecture, operations, standards, and roadmap |

## Analytics plane

The validated MVP generates synthetic operational sources, preserves Bronze, normalizes/quarantines into Silver, atomically loads a SQLite star schema, materializes Gold KPI/alert contracts, and renders Executive, Farm Performance, Inventory, Cost Analysis, Crop Health, and Data Quality views. Controlled CSV/PDF and capability-gated XLSX exports use validated Gold data and deterministic lineage.

The analytics plane owns `artifacts/`, its manifest, Gold CSVs, and SQLite warehouse. It does not write PostgreSQL operational state.

## Operational backend

The backend is a Spring modular monolith under `com.agriinsight.backend`.

| Module | Current responsibility |
|---|---|
| `shared` | API/error contracts, correlation, canonical command hashing, durable idempotency, tenant context, health/readiness |
| `identity.api` | Current user plus exact tenant-user and external-identity HTTP contracts |
| `identity.application` | Exact identity bootstrap, DB-enriched principal, tenant-user lifecycle and command orchestration |
| `identity.infrastructure` | OIDC validation, exact route registry, bounded PostgreSQL user/identity/principal stores |
| `authorization` | Fixed roles/permissions, scope evaluation, tenant transaction aspect, role lifecycle, audit publishers |
| `farm` | Scoped farm reads/commands, exact HTTP routes, lifecycle invariants, PostgreSQL persistence |
| `db/migration` | V1-V4 foundation/identity/authorization; V5-V7 farm schema/RLS/lifecycle; repeatable least-privilege helpers/grants |
| `backend/ops/postgres` | Idempotent role gate, allowlisted legacy ownership adoption, operator first-admin provisioning |

Phase 2 validates external JWT signature/algorithm, issuer, API audience, expiration/not-before, subject, and access-token discriminator. Phase 3 resolves exact `(issuer, subject)`, loads the active internal profile plus database roles/permissions under tenant context, and discards the raw JWT. JWT roles and tenant claims remain ignored for authorization.

## Current public contracts

- Public: `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- Authenticated when identity enabled: `GET /api/v1/me`
- User management: `GET/POST /api/v1/users`, `GET /api/v1/users/{id}`
- User lifecycle: `POST /api/v1/users/{id}/deactivate|reactivate`
- External identities: link and exact identity unlink routes below `/api/v1/users/{id}/external-identities`
- Role lifecycle: grant and revoke routes below `/api/v1/users/{id}/roles`
- Farm routes: `GET/POST /api/v1/farms`, `GET/PATCH /api/v1/farms/{id}`, and POST deactivate/reactivate lifecycle routes
- Development-only when explicitly enabled: OpenAPI/Swagger metadata
- All unregistered routes: denied

`/api/v1/me` returns the enriched internal profile, tenant code, fixed roles, and effective permissions without issuer, subject, raw claims/token, or database diagnostics. Tenant-administration responses expose only bounded internal/public fields and optimistic versions.

## Verification snapshot

- Backend: 161 unit/security/module tests plus 41 PostgreSQL 18/Flyway integration tests PASS (202 total).
- Migrations: V1-V7 plus repeatable grants apply/validate on fresh and allowlisted upgrade databases.
- Isolation: missing/invalid tenant, cross-tenant read/write, `WITH CHECK`, pooled-connection reset, role attributes, function grants, and policy catalog PASS.
- Commands: same-key concurrency, rollback retry, response-loss replay, changed path/`If-Match` conflict, actor binding, and no sensitive snapshot PASS.
- Administration: user/identity/role lifecycle, exact routes, bounded query counts, last-admin invariant, durable success/conflict/denial audit PASS.
- Farm: assignment-aware reads/updates, tenant-wide create/lifecycle, HTTP/ETag/idempotency contracts, and READ_COMMITTED parent/child lifecycle serialization PASS.
- Local image: non-root UID/GID `10001`, liveness/readiness/fail-closed smoke PASS.
- Analytics: 65 tests PASS, 3 expected optional-PDF skips; compileall, Node syntax, Compose config, and wheel PASS.
- Disk policy: C warns/fails below 10/8 GB; D warns/fails below 25/20 GB; heavy work requires both PASS.

## Next boundary

Phase 3 is accepted. Phase 4 is in progress: the farm slice is verified, while fields, crops, seasons, employees, assignments, activities, logs, and harvest APIs plus their FK-backed scope resolvers remain open. Phase 5 is dependency-unblocked but remains after Phase 4 by default. Release CI, scans, SBOM/provenance, and Docker Hub publication remain Phase 7.

## Unresolved Questions

- Production IdP/token fixtures and MFA policy
- Production audit retention and backup objectives
- Docker Hub namespace/release credentials
