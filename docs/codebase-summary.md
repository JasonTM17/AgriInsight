# Codebase Summary

Verified snapshot: 2026-07-26

## Repository shape

| Path | Responsibility |
|---|---|
| `src/agriinsight/` | Deterministic Bronze/Silver/Gold pipeline, quality, warehouse, KPI, insight, and report services |
| `dashboard/` | Streamlit analytics dashboard composition and contextual visual catalog |
| `web/` | Next 16 App Router/BFF, opaque session auth, and the `/overview`, `/farms`, `/farms/[farmId]`, `/work` browser surface |
| `tests/` | Python pipeline, KPI, dashboard, export, visual-asset, security-boundary, and disk-guard tests |
| `backend/` | Java 21 Spring Boot operational backend, PostgreSQL migrations, and transactional outbox |
| `scripts/` | C/D disk guard, guarded backend verification, and big-data demo runner |
| `plans/` | CK phase plans, design contracts, reports, and acceptance evidence |
| `docs/` | Evergreen architecture, operations, standards, contracts, and roadmap |

## Analytics plane

The validated MVP generates synthetic operational sources, preserves Bronze,
normalizes/quarantines into Silver, atomically loads a SQLite star schema,
materializes Gold KPI/alert contracts, and renders Executive, Farm Performance,
Inventory, Cost Analysis, Crop Health, and Data Quality views. Controlled
CSV/PDF and capability-gated XLSX exports use validated Gold data and
deterministic lineage.

The analytics plane owns `artifacts/`, its manifest, Gold CSVs, and the SQLite
warehouse. It does not write PostgreSQL operational state. The CLI keeps a fast
`standard` profile and a guarded `big-data` profile (10 farms, 120 fields,
365 days, 24 readings/day); the manifest stores resolved dimensions and a
configuration-fingerprinted run identity.

The dashboard uses eight generated WebP visuals in `dashboard/assets/generated/`.
They are contextual UI assets rather than source facts; Crop Health marks its
image as AI-generated demo evidence and never assigns it an observation ID.
The local Streamlit theme follows the Field Ledger palette from the CK FE
design system.

## Web surface

The web app owns the Next 16 App Router, the opaque session/BFF layer, and the
Phase 5-7 browser surface. Product routes are `/overview`, `/farms`,
`/farms/[farmId]`, `/work`, and `/inventory`; auth/support routes such as
`/login`, `/protected`, and `/api/auth/*` exist as shell plumbing.

Server loaders resolve scoped Spring UUID masters to canonical codes before
calling the typed FastAPI Gold read layer. The browser receives aggregated
view models through a tokenless BFF/opaque PostgreSQL session boundary and
never reconstructs KPI joins. Partial-source failure renders explicit degraded
states; safe lineage and reviewed contextual WebPs remain visible and bounded.
Shared rollout hardening covers exact BFF query allowlists, request-scoped nonce
CSP/custom 404 rendering, and mutex-owned E2E startup/cleanup.

`/work` consumes only frozen Spring activity, assignment, log, and correction
history contracts. Reads use bounded 50-row pages with exact offsets up to the
Spring limit of 10,000. Append and correction commands cross two exact BFF POST
routes with same-origin/CSRF/session checks, a streamed 64 KiB JSON limit,
strict UUID/body validation, and caller-stable `Idempotency-Key` forwarding.
Corrections remain linked append-only rows; the browser never sends a fabricated
`If-Match`, patch request, offline queue, or client-synthesized history.

`/inventory` pairs the Spring operational ledger with the Gold inventory
envelope under one warehouse-scoped route. `src/features/inventory/` holds the
route-state parser, the generated-client adapter, the strict mutation contract,
the view-model loader that isolates per-source failure, and the idempotent
mutation hook. Reads keep server order for balances, lots, and ledger rows and
disclose both `hasMore` and the 10,000 offset ceiling rather than implying a
total. Commands are a receipt/issue transaction and a linked reversal; only the
reversal carries a strong `If-Match`, whose value the BFF re-reads server-side
before forwarding. An unconfirmed command keeps its `Idempotency-Key` so retries
dedupe upstream. No ABC class, FEFO order, alert, or low-stock threshold is
recomputed in the browser.

## Operational backend

The backend is a Spring modular monolith under `com.agriinsight.backend`.

| Module | Current responsibility |
|---|---|
| `shared` | API/error contracts, correlation, canonical command hashing, durable idempotency, tenant/profile context, health/readiness |
| `identity` | OIDC validation, exact identity bootstrap, tenant-user lifecycle, external identities, route registry |
| `authorization` | Fixed roles/permissions, scope evaluation, tenant transaction aspect, role lifecycle, audit publishers |
| `farm` | Scoped farm/field/crop/season reads/commands and assignment history |
| `operations` | Employee master/picker, scoped activities/assignments, immutable logs/corrections, harvest facts |
| `inventory/api` | Warehouse, material, supplier, assignment, balance, lot, movement, and reversal HTTP contracts |
| `inventory/application` | Canonical commands, services, pages/queries, stores, reconciliation report |
| `inventory/domain` | Base units, quantity/money precision, transaction and projection records |
| `inventory/infrastructure` | PostgreSQL ledger/projections, deterministic locks/FEFO, reconciliation, warehouse scope SQL |
| `cost` | Append-only operating-cost ledger, correction/reversal commands, bounded hierarchy-derived reads, summaries, and cost route contracts |
| `integration` | Transactional outbox event model, writer port, drain service, and PostgreSQL outbox store |
| `db/migration` | V1-V4 foundation/identity; V5-V11 farm/workforce/activity lifecycle; V12-V15 inventory/warehouse scope; V16-V17 cost ledger/RLS; V18-V19 outbox and release boundary |
| `backend/ops/postgres` | Idempotent role gate, allowlisted ownership adoption, operator first-admin provisioning |

The backend resolves exact `(issuer, subject)`, loads the active internal
profile and database permissions, then binds `app.tenant_id` and
`app.profile_id` transaction-locally. JWT roles and tenant claims are not
trusted for authorization. Runtime roles are restricted, non-owner, and
subject to PostgreSQL ENABLE/FORCE RLS.

Phase 1 contract freeze adds eight bounded GET reads that stay additive and
non-enumerating:

- `GET /api/v1/activities/{id}/assignments`
- `GET /api/v1/activities/{id}/logs`
- `GET /api/v1/activities/{id}/logs/{logId}/history`
- `GET /api/v1/users/{id}/roles`
- `GET /api/v1/users/{id}/external-identities`
- `GET /api/v1/farm-assignments`
- `GET /api/v1/warehouse-assignments`
- `GET /api/v1/audit-events`

The deterministic backend OpenAPI artifact is frozen at 67 paths and 94
operations. Every operation carries `X-Correlation-Id`; 13 versioned detail
GETs also expose `ETag`.

## Inventory contract summary

- `RECEIPT` requires active warehouse/material/supplier, base quantity, VND unit
  cost, batch, and expiry; the server derives finance fields.
- `ISSUE` requires a reason and uses an explicit lot or deterministic FEFO over
  eligible, non-expired lots. It cannot make a lot or balance negative.
- Reversals are immutable linked rows, bounded by the original remaining
  quantity and allocation/lot provenance. Receipt reversal money uses cumulative
  two-decimal rounding so the final total exactly cancels the source.
- `inventory_transactions` is the source ledger; allocations, lots, and
  balances are projections reconciled in a read-only drift report.
- V15 RLS is role-aware: Tenant Admin writes tenant inventory; assigned
  Inventory Manager reads/writes; Executive/Data Analyst read tenant-wide;
  assigned Farm Manager reads; Supplier has no inventory permission.
- All list routes are bounded and stable; mutation routes require idempotency,
  and versioned mutation/reversal routes require strong `If-Match`.

## Current public contracts

- Public health: `GET /actuator/health`, `/actuator/health/liveness`,
  `/actuator/health/readiness`.
- Authenticated `GET /api/v1/me` when identity is enabled.
- Tenant/user/identity/role routes under `/api/v1/users`.
- Farm, field, crop, season, employee, assignment, activity, log, and harvest
  routes under `/api/v1`.
- Inventory masters: `/api/v1/warehouses`, `/api/v1/materials`,
  `/api/v1/suppliers`, `/api/v1/warehouse-assignments`.
- Inventory reads: `/api/v1/inventory/balances`, `/api/v1/inventory/lots`,
  `/api/v1/inventory/transactions` and `/{id}`.
- Inventory writes: `POST /api/v1/inventory/transactions` and
  `POST /api/v1/inventory/transactions/{id}/reversals`.
- Operating-cost writes: `POST /api/v1/cost-entries` and
  `POST /api/v1/cost-entries/{id}/corrections`.
- Operating-cost reads: `GET /api/v1/cost-entries`,
  `GET /api/v1/cost-entries/{id}`, and `GET /api/v1/cost-summaries`.
- Cost summaries identify the `OPERATING_COST` lens and never merge
  procurement spend or inventory value.
- OpenAPI/Swagger is disabled by default and only exposed in an explicit
  development profile or authenticated non-development configuration.
- All unregistered business mappings are denied.

## Verification snapshot

- Web Phase 6 local acceptance (2026-07-26): generated-contract drift,
  TypeScript, zero-warning ESLint, Next 16 production build, 127 passed web
  tests with 9 intentional DB-only skips, 31 focused Work contract/security
  tests, 13 demo bootstrap/reconciliation tests, 5 Spring activity HTTP
  contract tests, production dependency audit with 0 vulnerabilities, and 6/6
  real Keycloak/PostgreSQL/Spring/Chrome scenarios. The E2E project and owned
  runtime paths cleaned before `WEB_PLATFORM_E2E=PASS`. A tenant-scoped real
  PostgreSQL seed → revoke → reseed probe also passed with
  `preserved=1 active=0 history=1`, proving demo bootstrap never restores a
  revoked assignment.
- Web Phase 5 local acceptance (2026-07-26): clean `npm ci`, checked-in Spring
  and analytics contract drift, TypeScript, zero-warning ESLint, Next 16
  production build, Maven package with tests skipped by that gate, 82 passed
  web tests with 9 intentional skips, 9/9 PostgreSQL privilege tests, 3/3
  installed-Chrome scenarios, and production dependency audit with 0
  vulnerabilities at the configured threshold.
- Web E2E cleanup audit: zero listeners on 3100, 55443, and 58080-58082; zero
  `agriinsight-web-e2e` Compose containers; `artifacts/_tmp/web-e2e` and
  `_tmp/web-e2e` absent.
- Backend phase-1 contract gate (2026-07-23): 459 surefire tests + 100
  Failsafe/PostgreSQL integration tests; zero failures, errors, and skips.
- Backend guarded `mvn verify` (2026-07-22): 622 tests, including 98 Failsafe
  integration tests; zero failures, errors, and skips.
- Hosted GitHub Actions run `29932250984` passed 5/5 jobs for commit
  `8d8463f9fe576aa98498125ae3dc845d9b432d82`. That run covered Java, Python,
  dependency/configuration/secret scan, and image scan/smoke gates.
- Phase 7 manual registry digests were published for evidence only: backend
  `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`
  and Python `sha256:ee4090812a36c48f180ee74aaa16995c79eabfedb6821d9764319643d06ba2f6`.
- Cost focused suite: 26/26; fresh PostgreSQL 18 containers validate V1-V17,
  RLS, correction concurrency, query plans, and bounded projections. The
  inventory focused suite remains 32/32.
- OpenAPI contract: `/v3/api-docs` operation summaries and request examples
  verified by the inventory OpenAPI contract checks.
- Phase 1 contract export:
  `backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json`
  regenerated deterministically with SHA-256
  `673b2dabb8853d75fff5b719fd1ecfaef350b0b076170e78a63b05fedbb7dfa8`.
- Analytics: Python 76 passed, 3 expected optional-PDF skips; compileall and
  visual/export/dashboard checks pass.
- Big-data: 1,050,003 Bronze sensor rows, 1,050,000 Silver/warehouse facts,
  quality passed, 74 checksum entries with zero mismatch, 388.2 MB on D.
- Disk policy: C warns/fails below 10/8 GB and D below 25/20 GB. The final
  Phase 6 gate recorded C 9.37 GiB and D 20.93 GiB before cleanup; both
  remained above fail thresholds. Archived Codex sessions, Azure Functions
  cache, and JetBrains local cache were moved recoverably to D through
  junctions; a 0.42 GiB inactive Maven-cache archive was also moved from C to
  D to preserve C-drive headroom.
- Backup/restore drill: D-local custom dump SHA-256 `934ddd9db020d5a2e4f6860ce977663ec5a28bd68d4dcd7a16cc88a4c9c4162c`,
  Flyway `19`, clean target restore elapsed 11.045s, and role/RLS/runtime
  smoke passed.
- Disposable web-auth spike: `openid-client` 6.8.4 won; Better Auth 1.6.24
  failed the executable refresh-fence harness. Real issuer gate remains proven
  against Keycloak 26.7.0, PostgreSQL 18, Next 16.2.11, and installed Chrome.
  Final auth gate: 16 unit, 7 PostgreSQL integration, 1 installed-Chrome E2E.

## Next boundary

Production-web Phase 6 is completed locally. The next implementation boundary
is Phase 7 Inventory Control over the frozen Spring inventory contracts.
Phase 11 browser quality and Phase 12 protected release gates still block
registry publication and any production-release claim.

## Unresolved questions

- Production IdP/token fixtures and MFA policy.
- Production audit retention and backup/restore objectives.
- Protected release environment secrets/reviewers and release-token rotation owner.
