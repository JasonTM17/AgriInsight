# Codebase Summary

Verified snapshot: 2026-07-20

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
| `shared` | API version, correlation IDs, Problem Details, health/readiness, persistence/audit primitives, foundation security |
| `identity.api` | Minimum `GET /api/v1/me` contract |
| `identity.application` | Exact external identity claims, bootstrap service, internal principal mapping |
| `identity.domain` | Profile/external identity entities and fixed role/permission enums |
| `identity.infrastructure` | OIDC config/decoder/validators, JWT conversion, bootstrap query, exact route registry |
| `db/migration` | V1 tenant anchor, V2 identity/RBAC tables + hardened bootstrap function, V3 deterministic role/permission grants |

Phase 2 validates external JWT signature/algorithm, issuer, API audience, expiration/not-before, subject, and access-token discriminator. It resolves exact `(issuer, subject)` to an active internal profile/tenant, then discards the raw JWT from the application principal. JWT roles and tenant claims are ignored for authorization.

## Current public contracts

- Public: `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- Authenticated when identity enabled: `GET /api/v1/me`
- Development-only when explicitly enabled: OpenAPI/Swagger metadata
- All unregistered routes: denied

`/api/v1/me` returns only `profileId`, `tenantId`, and optional bounded `displayName`, `email`, and `assurance`. It does not expose issuer, subject, raw claims/token, roles, or database diagnostics.

## Verification snapshot

- Backend: 57 unit/security/module tests plus 1 PostgreSQL 18/Flyway integration test PASS.
- Migrations: V1-V3 fresh apply/validate; 19 permissions, 7 roles, supplier has zero grants.
- Local image: non-root UID/GID `10001`, liveness/readiness/fail-closed smoke PASS.
- Analytics: 65 tests PASS, 3 expected optional-PDF skips; compileall, Node syntax, Compose config, and wheel PASS.
- Disk policy: C warns/fails below 10/8 GB; D warns/fails below 25/20 GB; heavy work requires both PASS.

## Next boundary

Phase 3 owns restricted database roles/grants, operator provisioning, transaction-local tenant context, DB-backed permissions, PostgreSQL RLS, direct-SQL cross-tenant tests, and pooled-connection reset proof. No business API or production authentication claim should precede that gate.

## Unresolved Questions

- Production IdP/token fixtures and MFA policy
- Production audit retention and backup objectives
- Docker Hub namespace/release credentials
