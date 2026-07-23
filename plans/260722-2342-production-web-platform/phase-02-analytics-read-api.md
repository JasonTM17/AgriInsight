---
phase: 2
title: "Analytics read API"
status: pending
priority: P1
effort: "7d"
dependencies: [1]
---

# Phase 2: Analytics read API

## Overview

Establish one explicit demo-tenant integration boundary, then add an internal FastAPI read surface over verified Gold artifacts. The demo bootstrap reconciles canonical master codes and role personas into the operational database; the service itself stays read-only, snapshot-backed, and demo-tenant-gated by Spring `/api/v1/me`.

## Context links

- `plans/260722-2342-production-web-platform/plan.md`
- `docs/codebase-summary.md`
- `docs/project-overview-pdr.md`
- `dashboard/cost_analysis_snapshot.py`
- `src/agriinsight/pipeline.py`

## Verified baseline

- The pipeline already writes deterministic `manifest.json` with run metadata, row counts, and SHA-256 checksums for every artifact except manifest/tmp/log files (`src/agriinsight/pipeline.py:48-119`).
- A stable checksum-verified snapshot reader already exists, but only for cost datasets and only under `dashboard/` today (`dashboard/cost_analysis_snapshot.py:35-99`).
- The current Streamlit app reads Gold files directly from disk and has no HTTP analytics layer (`dashboard/app.py:537-638`, `docs/codebase-summary.md:17-30`).
- `/api/v1/me` is the current principal bootstrap surface and includes tenant and permission context (`backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserController.java:11-19`, `backend/src/main/java/com/agriinsight/backend/identity/api/CurrentUserResponse.java:8-36`).
- Farm and warehouse scope catalogs are already bounded, permission-guarded, and omit `tenantId` from response DTOs (`backend/src/main/java/com/agriinsight/backend/farm/api/FarmReadController.java:29-49`, `backend/src/main/java/com/agriinsight/backend/farm/api/FarmResponse.java:7-29`, `backend/src/test/java/com/agriinsight/backend/farm/FarmReadHttpContractTest.java:45-100`, `backend/src/main/java/com/agriinsight/backend/inventory/api/WarehouseReadController.java:29-49`, `backend/src/main/java/com/agriinsight/backend/inventory/api/WarehouseResponse.java:8-33`, `backend/src/test/java/com/agriinsight/backend/inventory/WarehouseReadHttpContractTest.java:44-94`).
- The product boundary is explicit: Python owns artifacts and manifest lineage; Java owns authenticated operational facts; browser BFF/session handling is still deferred in the PDR (`docs/project-overview-pdr.md:27-37`, `117-122`).

## Requirements

- Functional: expose bounded JSON read endpoints for overview, farms, inventory, crop health, data quality, and cost-analysis reads over one checksum-verified snapshot.
- Functional: derive access from the caller's Spring-backed identity and configured demo tenant only; deny non-demo tenants even if a bearer is valid.
- Functional: compare `/me.tenantId` to one deployment-configured demo tenant UUID. Tenant code is display metadata and must never be the isolation key.
- Functional: provide an explicit, idempotent, local-demo-only bootstrap that imports canonical farms, fields, crops, seasons, warehouses, materials, bounded operational samples, seven persona identities/roles, and their assignment scopes from the verified demo artifacts/subject map into PostgreSQL. It must never run implicitly or target a non-demo database.
- Functional: produce a cross-store reconciliation report proving every farm/field/crop/season/warehouse/material code used by a web analytic filter maps to exactly one active Spring master for the configured demo tenant before FastAPI becomes ready.
- Functional: reuse existing manifest/checksum/export code paths; do not fork a second artifact-trust model.
- Security: forward the caller bearer only server-to-server to Spring; never trust client-supplied farm or warehouse scope without Spring confirmation.
- Security: use roles and permissions as an additive grant union for multi-role principals, but intersect every resource-scoped grant with Spring-returned canonical codes. A tenant-wide role widens only the domains it actually grants; Supplier never contributes a grant.
- Reliability: bound Spring calls with connect/read timeouts, propagate a validated correlation ID, retry only safe idempotent GETs with a small jittered budget, reject redirects, and fail closed on malformed/oversized upstream responses.
- Non-functional: keep the service internal-only, OpenAPI-documented, and read-only. Actual cost export publication remains Phase 8 work.
- Non-functional: cache only checksum-verified, already-aggregated Gold snapshots keyed by manifest fingerprint/run; invalidate and fail closed when the manifest changes mid-read. Never load raw million-row Silver data in a request.

## Data flow

1. Explicit demo bootstrap -> verified artifact masters + credential-free OIDC subject map -> transactional demo-only PostgreSQL seed -> cross-store code reconciliation report. This never runs in a normal production start path.
2. Caller bearer -> FastAPI dependency -> Spring `/api/v1/me` -> principal envelope -> configured demo-tenant UUID and endpoint-policy check.
3. When the endpoint is farm-scoped or inventory-scoped, the same bearer pages through only the required bounded Spring farm or warehouse catalog; a request-local cache prevents duplicate scope calls.
4. FastAPI request -> one checksum-verified cached Gold snapshot keyed by manifest fingerprint -> page-specific read model -> bounded JSON response.
5. Snapshot failure, reconciliation drift, scope mismatch, or non-demo tenant -> stable 4xx/5xx body with correlation ID; no filesystem path or raw upstream payload leaks.

## File matrix

- Modify: `pyproject.toml` - add internal analytics API optional dependencies and entrypoint.
- Modify: `dashboard/cost_analysis_snapshot.py` - delegate to a shared artifact snapshot loader or become a thin compatibility wrapper.
- Modify: `dashboard/app.py` - import the shared snapshot module instead of owning a dashboard-local version.
- Modify: `tests/test_cost_analysis_snapshot.py`
- Modify: `tests/test_dashboard.py` - keep the current dashboard green after the shared-loader extraction.
- Create: `src/agriinsight/analytics_snapshot.py` - generalized manifest/checksum snapshot loader for arbitrary dataset sets.
- Create: `src/agriinsight/analytics_api/__main__.py`
- Create: `src/agriinsight/analytics_api/app.py`
- Create: `src/agriinsight/analytics_api/settings.py`
- Create: `src/agriinsight/analytics_api/auth_scope.py`
- Create: `src/agriinsight/analytics_api/read_models.py`
- Create: `src/agriinsight/analytics_api/errors.py`
- Create: `src/agriinsight/demo_tenant_bootstrap.py` - deterministic demo SQL/data bundle generation under ignored D-local temp only.
- Create: `src/agriinsight/demo_tenant_reconciliation.py` - canonical code-set and count checks across artifacts and Spring demo masters.
- Create: `src/agriinsight/analytics_api/routers/catalog.py`
- Create: `src/agriinsight/analytics_api/routers/overview.py`
- Create: `src/agriinsight/analytics_api/routers/farms.py`
- Create: `src/agriinsight/analytics_api/routers/inventory.py`
- Create: `src/agriinsight/analytics_api/routers/crop_health.py`
- Create: `src/agriinsight/analytics_api/routers/data_quality.py`
- Create: `src/agriinsight/analytics_api/routers/costs.py`
- Create: `tests/analytics_api/test_auth_scope.py`
- Create: `tests/analytics_api/test_snapshot_consistency.py`
- Create: `tests/analytics_api/test_endpoints.py`
- Create: `tests/analytics_api/test_openapi_contract.py`
- Create: `tests/test_demo_tenant_bootstrap.py`
- Create: `tests/test_demo_tenant_reconciliation.py`
- Create: `scripts/bootstrap-demo-environment.ps1` - explicit guard, transaction, seed, reconciliation, and report wrapper.
- Create: `deploy/demo/demo-tenant.json` - non-secret tenant/profile/role/canonical subject IDs; credentials remain environment-only.
- Create: `docs/contracts/agriinsight-analytics-v1.openapi.json` - checked-in deterministic internal API contract.
- Delete: none.

## Endpoint and data contracts

- `GET /internal/v1/catalog` -> safe run metadata (`runId`, `contractVersion`, `asOf`, `generatedAt`, checksum fingerprint), allowed farms, allowed warehouses, and locale metadata for the BFF.
- `GET /internal/v1/overview` -> executive summary, monthly trend, top risks, and insight queue from one snapshot.
- `GET /internal/v1/farms` -> paged farm-performance rows plus farm-filtered crop profitability drill data; `limit <= 100`.
- `GET /internal/v1/inventory` -> summary, paged status rows, ABC class labels, and alerts filtered to Spring-approved warehouses only.
- `GET /internal/v1/crop-health` -> summary, paged field-risk rows, weekly pest counts, and alerts filtered to Spring-approved farms only.
- `GET /internal/v1/data-quality` -> quality scores, check failures, remediation counts, run lineage, and no write affordance.
- `GET /internal/v1/costs` -> read-only cost summary data plus explicit capability flags; actual file download/generation routes stay deferred to Phase 8 so this phase stays read-only.
- Every endpoint is GET-only in this phase. Sorting and filters are allowlisted; unbounded scans, arbitrary SQL-like filters, and raw dataset passthrough are forbidden.
- Every success payload is a typed Pydantic envelope with safe `scope`, `freshness`, `lineage`, and page payload fields. Every failure is a typed correlation-aware error; neither shape may contain an artifact root, manifest path, stack trace, bearer, or raw Spring body.
- Crop Health and Data Quality lock camelCase fields in the checked-in spec: `runId`, `asOf`, `generatedAt`, `dataStatus=current|stale|partial|missing`, `assessmentMethod=rule-based-heuristic`, `severity=none|low|medium|high`, and descriptive `evidenceSignals`. Severity is sourced from verified rule-based Gold/quality results, not converted to probability or causal language. `dataStatus` follows one tested server rule: missing filtered evidence -> `missing`; verified but incomplete optional sections -> `partial`; otherwise artifact age over configured 48 hours -> `stale`; else `current`.
- `/health/ready` fails when the demo UUID is absent, manifest verification fails, or required cross-store canonical codes drift; it never reports ready on an empty/unreconciled operational database.

## Authorization matrix

- `TENANT_ADMIN`, `EXECUTIVE`, and `DATA_ANALYST`: tenant-wide overview/farm/inventory/crop/cost reads when the corresponding Spring permission is also present.
- `FARM_MANAGER`: farm-scoped overview/farm/crop/cost reads only for canonical codes returned by Spring; never tenant-wide analytics.
- `INVENTORY_MANAGER`: inventory reads only for canonical warehouse codes returned by Spring; no farm/crop/data-quality analytics.
- Data Quality: `TENANT_ADMIN` and `DATA_ANALYST` only because it exposes tenant-wide pipeline quality.
- `FIELD_WORKER` and `SUPPLIER`: denied all analytics endpoints. A valid token or client-supplied code never widens these rules.
- Multi-role evaluation is a domain-specific union of valid grants followed by resource-scope intersection. Example: `DATA_ANALYST + INVENTORY_MANAGER` may use tenant-wide inventory because Data Analyst grants it, but an unrelated role never promotes farm-scoped access to tenant-wide.

## Tests before

- Generalize the snapshot tests first so any dataset set must pass manifest-before/after and checksum verification, not just cost CSVs.
- Add bootstrap safety tests first: explicit demo flag/marker required, non-demo host/database refused, no secrets written, transaction rollback on partial failure, idempotent rerun, and no mutation of verified artifacts.
- Add reconciliation tests proving duplicate, missing, inactive, or mismatched canonical codes block readiness; include the actual big-data dimension sets.
- Add auth-scope tests for every matrix row: non-demo tenant UUID denied, role/permission mismatch denied, farm/warehouse managers constrained to Spring-returned codes, Field Worker/Supplier denied, and caller-supplied foreign filters rejected before artifact access.
- Add multi-role permutation tests and upstream-client tests for timeouts, bounded retries, redirect rejection, correlation propagation, payload caps, and sanitized failure bodies.
- Add endpoint tests that prove page caps, allowlisted sort/filter behavior, and no path leakage on missing or corrupt artifacts.
- Add cache tests that prove one verified load per manifest fingerprint, request-local scope reuse, invalidation on manifest change, and fail-closed behavior during concurrent replacement.
- Add OpenAPI tests that lock internal endpoint names, typed envelopes/query caps/read-only methods, canonical serialization, and checked-in drift.

## Green steps

1. Implement the explicit demo bootstrap and seven-persona subject map without credentials; generate temporary seed material only under ignored D-local `_tmp`, apply transactionally to the compose-marked demo DB, and write a safe reconciliation report.
2. Prove all canonical analytic dimensions resolve one-to-one to active demo operational masters; keep the million sensor facts artifact-side and import only bounded operational records needed for real workflows.
3. Extract a shared artifact snapshot loader from `dashboard/cost_analysis_snapshot.py` into `src/agriinsight/analytics_snapshot.py` without changing current dashboard behavior.
4. Add a minimal FastAPI application with stable router registration order and one internal config surface for artifact root, fixed Spring base URL, and demo tenant UUID.
5. Implement a hardened upstream client and auth dependency that always calls `/api/v1/me`, applies the endpoint-specific multi-role matrix, and fetches only needed bounded farm/warehouse pages into request-local scope.
6. Build typed read models from already-aggregated Gold CSVs plus `quality/data_quality_report.json`; never read raw Silver or write SQLite/artifacts in request flow.
7. Add a process-local immutable snapshot cache keyed by verified manifest fingerprint/run. Revalidate before response, atomically replace complete snapshots, and fail closed on checksum/manifest/reconciliation change.
8. Publish only bounded GET endpoints, then canonically generate/check in `docs/contracts/agriinsight-analytics-v1.openapi.json`; drift fails before Phase 3.

## Refactor

- Keep scope filtering and dataframe shaping in pure helpers so later route phases can reuse them without reopening auth logic.
- Keep artifact verification centralized in one shared snapshot module; the dashboard and FastAPI must not diverge.
- Keep the cache immutable and bounded to aggregate snapshots; no module-level mutable dataframe may be edited across requests.
- Keep any future export adapter behind a private module boundary so Phase 8 can add file routes without rewriting core snapshot logic.

## Focused commands

- `python -m pytest tests/test_cost_analysis_snapshot.py tests/analytics_api -q`
- `python -m pytest tests/test_demo_tenant_bootstrap.py tests/test_demo_tenant_reconciliation.py -q`
- `python -m pytest tests/test_dashboard.py -q`

## Broad commands

- `python -m pytest`
- `python -m compileall -q src dashboard tests`
- `git diff --check`

## Acceptance

- [ ] One shared snapshot loader verifies arbitrary dataset sets against `manifest.json` checksums.
- [ ] Explicit demo bootstrap creates seven real personas and operational masters/samples aligned to verified artifact codes; it is idempotent, transactional, credential-free in source, D-temp-only, and refuses non-demo targets.
- [ ] Big-data canonical master reconciliation passes one-to-one before analytics readiness; missing/duplicate/inactive codes fail closed.
- [ ] Internal FastAPI endpoints are GET-only, bounded, documented, and demo-tenant-gated through Spring `/me` plus scoped farm/warehouse catalog calls.
- [ ] `/me.tenantId` is compared to the configured demo UUID; code/display names never authorize access.
- [ ] Endpoint-specific role/permission policy and Spring-approved canonical farm/warehouse scope are enforced exactly as the matrix states.
- [ ] Multi-role grants are unioned per domain then intersected with resource scope; unrelated roles never widen access.
- [ ] Non-demo tenants and foreign farm/warehouse filters fail closed.
- [ ] Deterministic `docs/contracts/agriinsight-analytics-v1.openapi.json` and drift tests cover typed success/error envelopes.
- [ ] Verified aggregate snapshots are cached by manifest fingerprint and invalidated safely; raw million-row Silver data is never request-loaded.
- [ ] The current Streamlit dashboard remains green after the shared-loader extraction.
- [ ] No endpoint writes artifacts, SQLite, or PostgreSQL business tables.

## Risks and rollback

| Risk | L x I | Mitigation |
|---|---|---|
| Snapshot helper forks from dashboard logic | Medium x High | one shared loader module and dashboard regression tests |
| Demo tenant leaks wider scope | Medium x High | derive farm/warehouse filters only from Spring-approved catalogs |
| Demo artifacts and operational DB use unrelated codes, leaving real pages empty | High x High | explicit bootstrap, one-to-one reconciliation, readiness gate, and big-data E2E |
| Demo seed accidentally targets a non-demo DB | Low x Critical | explicit flag + compose marker + host/database allowlist + transaction + fail closed |
| Spring dependency stalls every analytics request | Medium x High | fixed base URL, timeouts, safe bounded retries, redirect rejection, payload caps |
| Stale process cache survives artifact replacement | Medium x High | manifest-fingerprint key, atomic immutable replacement, pre-response revalidation |
| Endpoint payloads become unbounded | Medium x Medium | page caps, allowlisted sort keys, and explicit dataset shaping |
| Export scope grows too early | Medium x Medium | keep file generation deferred to Phase 8 |

Rollback: disable the FastAPI service and keep Streamlit as the only analytics UI; the shared snapshot extraction is backward-compatible and can remain even if HTTP is rolled back.

## Dependencies, parallelization, and ownership

- Depends on: Phase 1.
- Parallelization: none across the analytics contract boundary; its checked-in OpenAPI must be stable before Phase 3 client generation.
- Blocks: Phase 3 and all analytics-backed route work.
- File ownership: this phase owns `src/agriinsight/analytics_api/**`, the shared snapshot extraction, deterministic analytics OpenAPI, and tests. Phase 3 consumes the artifact but never edits these routers.

## Commit groups

1. `feat(demo): add guarded tenant bootstrap and reconciliation`
2. `refactor(analytics): share verified artifact snapshots`
3. `feat(analytics): add scoped cached read endpoints`
4. `test(analytics): lock openapi cache and scope gates`

## Success Criteria

- [ ] Shared artifact verification is reused instead of duplicated.
- [ ] Analytics reads are internal, bounded, and read-only.
- [ ] Demo-tenant gating is enforced through Spring-authenticated scope, not caller claims.
- [ ] Dashboard regression and new FastAPI tests both pass.

## Validation log

- Tier: Standard
- Claims checked: 9
- Verified: 9
- Failed: 0
- Unverified: 0
