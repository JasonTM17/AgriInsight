---
phase: 8
title: "cost-analysis"
status: implementation-complete
priority: P1
effort: "4d"
dependencies: [2, 3, 4]
---

## Delivery status (2026-07-27)

Implementation and static verification are complete. The guarded browser gate
is checked in as `web/tests/e2e/cost-analysis.spec.ts` but is intentionally not
claimed locally while `scripts/check-workspace-disk.ps1` reports C: below its
non-negotiable 8 GiB floor. See
`reports/phase-08-cost-analysis-evidence-2026-07-27.md` for exact evidence.

# Phase 8: cost-analysis

## Overview

Deliver cost analysis with exactly two lenses: `operating` and `procurement`. Operating cost entries and summaries stay on the existing Spring `/api/v1/cost-entries` and `/api/v1/cost-summaries` routes, while procurement analytics and normalized export flow through FastAPI with real CSV/PDF/XLSX output, 25k-row or 10 MiB limits, and `_tmp`-only staging.

### Verified contract amendment (2026-07-27)

The initial read-only scout found a real implementation gap: the existing
`GET /internal/v1/costs` response is operating-only (`CostsPayload` contains
`cost_breakdown`, `cost_farm`, `cost_monthly`, and an operating summary), has no
query parameters, and does not expose `procurement_detail`. Passing mapped farm
codes to that route would therefore be misleading. Phase 8 adds a separate,
explicit read-only route:

- `GET /internal/v1/costs/procurement`
- query: `farm_code`, `month_from`, `month_to`, `limit`, `offset`
- payload: procurement-only summary, monthly trend, supplier drivers, and a
  bounded detail page

The existing `/internal/v1/costs` route remains backwards compatible for the
operating analytics contract. The generated analytics OpenAPI artifact,
allowlist, runtime schemas, and endpoint tests must be updated together.

## Context

- Verified Spring operating-cost reads and mutations are rooted at `/api/v1/cost-entries` and `/api/v1/cost-summaries` in `backend/src/main/java/com/agriinsight/backend/cost/api/OperatingCostReadController.java:25`, `OperatingCostMutationController.java:34`, and `CostSummaryController.java:19`.
- Verified Spring mutations are `POST /api/v1/cost-entries` and `POST /api/v1/cost-entries/{id}/corrections` in `backend/src/test/java/com/agriinsight/backend/cost/OperatingCostHttpContractTest.java:93` and `:120`.
- Existing export builders already exist in Python: CSV/PDF/XLSX assembly in `src/agriinsight/cost_report_service.py:30`, XLSX temp-root enforcement in `src/agriinsight/cost_report_xlsx.py:285`, and `_tmp` exclusion from artifact checksums in `src/agriinsight/pipeline.py:56`.
- Inventory value remains part of the Inventory area; this phase must not add a third cost lens.
- Phase 3 owns BFF auth/session and upstream error mapping.
- Phase 4 owns shell/nav and shared formatting primitives.
- This phase owns cost route trees, cost-specific BFF wrappers, FastAPI export-endpoint planning, and cost-domain tests.

## Requirements

- Functional:
  - Render `/costs` with exactly two lenses: `operating` and `procurement`.
  - Keep each lens on its own filters, summary cards, tables, and trend panels.
  - Support operating-cost append/correction only through the existing Spring routes.
  - Support one normalized FastAPI export endpoint that reuses the existing CSV/PDF/XLSX builders.
  - Render only the requested format. Do not call the existing all-format `build_cost_report_bundle()` on an HTTP request because it eagerly creates CSV, PDF, and optional XLSX together.
  - Reject exports estimated above 25k rows or 10 MiB and instruct the user to narrow filters.
  - Map operational UUID filters to canonical analytics codes before procurement analytics calls.
- Non-functional:
  - No third `inventory` lens exists here; inventory value stays in Inventory.
  - No `PATCH` cost route or new Spring controller is introduced.
  - Browser cannot assemble export files, estimate totals from hidden pages, or bypass normalized export contracts.
  - Export metadata must stay safe: no filesystem paths, only normalized lineage and size metadata.
  - Procurement remains read-only analytics.

## Data Flow

1. Browser requests `/costs?lens=...` with lens-specific URL filters.
2. BFF routes `operating` requests to Spring `cost-entries` and `cost-summaries` routes.
3. BFF resolves operational UUID filters to canonical farm/season/activity codes before procurement analytics requests.
4. BFF routes `procurement` analytics and export requests to FastAPI.
5. FastAPI normalizes the request, enforces row/byte caps, stages temporary files only under `_tmp`, and dispatches exactly one requested CSV/PDF/XLSX renderer.
6. FastAPI returns a stream plus safe metadata; BFF forwards the response to the browser without exposing filesystem paths.

## File Matrix

These are the fixed Phase 8 ownership targets; the FastAPI paths extend the Phase 2 package in one serialized integration commit.

| Action | Path | Purpose |
| --- | --- | --- |
| CREATE | `web/src/app/(platform)/costs/page.tsx` | costs route entry |
| CREATE | `web/src/features/costs/load-cost-view-model.ts` | two-lens loader |
| CREATE | `web/src/features/costs/cost-filter-schema.ts` | lens-safe URL parsing |
| CREATE | `web/src/features/costs/map-operational-ids-to-codes.ts` | UUID -> canonical code mapping |
| CREATE | `web/src/features/costs/export-cost-view.ts` | normalized export request/stream helper |
| CREATE | `web/src/features/costs/components/*.tsx` | lens tabs, tables, trend panels |
| CREATE | `web/tests/contracts/cost-analysis.contract.test.ts` | two-lens and export rules |
| CREATE | `web/tests/e2e/cost-analysis.spec.ts` | lens and export journey |
| CREATE | `web/src/features/costs/cost-generated-contract-schemas.ts` | runtime-safe operating/procurement response schemas |
| CREATE | `web/src/features/costs/cost-generated-client-adapter.ts` | Spring and FastAPI cost readers |
| CREATE | `src/agriinsight/analytics_api/routers/cost_exports.py` | normalized FastAPI export endpoint |
| MODIFY | `src/agriinsight/analytics_api/routers/costs.py` | procurement-only read route |
| MODIFY | `src/agriinsight/analytics_api/models.py` | procurement payload contract |
| MODIFY | `src/agriinsight/analytics_api/record_models.py` | procurement record models |
| MODIFY | `src/agriinsight/analytics_api/snapshot_cache.py` | procurement detail column/row validation |
| CREATE | `src/agriinsight/analytics_api/procurement_cost_read_models.py` | scoped procurement aggregation/read model |
| CREATE | `tests/test_internal_cost_exports.py` | export endpoint row/byte/path guards |
| CREATE | `tests/analytics_api/test_procurement_costs.py` | scoped procurement read contract |
| MODIFY | `src/agriinsight/cost_report_service.py` | normalized response metadata reuse |
| CREATE | `src/agriinsight/cost_report_single_export.py` | prepare once and render exactly the requested format without breaking the dashboard bundle API |
| MODIFY | `src/agriinsight/cost_report_xlsx.py` | `_tmp` staging integration if needed |
| MODIFY | `src/agriinsight/analytics_api/app.py` | sequential router registration handoff |

## Interfaces And Contracts

- Lens URL contract:
  - `lens`: required enum `operating | procurement`
  - `from`, `to`, `farmId`, `seasonId`, `activityId`, `category`: optional by lens, rejected if lens-incompatible
  - `category` is an enum string, not `categoryId`
- Verified Spring operating contracts consumed via BFF:
  - `GET /api/v1/cost-entries`
  - `GET /api/v1/cost-entries/{id}`
  - `GET /api/v1/cost-summaries`
  - `POST /api/v1/cost-entries`
  - `POST /api/v1/cost-entries/{id}/corrections`
- Procurement analytic contracts consumed via BFF:
  - `GET /internal/v1/costs/procurement`
  - normalized export endpoint reusing existing CSV/PDF/XLSX builders
- Export contract:
  - success includes a streamed file plus safe metadata such as `runId`, `contractVersion`, `asOf`, checksum fingerprint, row count, byte size, and format
  - over-limit returns typed error carrying estimated rows/bytes and current applied filters
  - export staging may use `_tmp` only and must not surface filesystem paths
  - `format` is an allowlisted `csv|pdf|xlsx`; one request invokes one renderer, sets a safe deterministic `Content-Disposition`, and preserves existing formula-injection protections
  - sequential router registration is required when the FastAPI export route is added

## TDD Track

### RED

- Add contract tests for exactly two lenses, rejected `inventory` lens, rejected `categoryId`, and read/write separation.
- Add tests for UUID -> canonical code mapping before procurement analytics.
- Add export tests for one-renderer-only dispatch, under-limit success, over-limit rejection, `_tmp`-only staging, safe `Content-Disposition`/metadata, formula safety, disconnect cleanup, and XLSX-unavailable behavior.
- Add E2E covering lens switching, export success, export rejection, and operating-cost correction.

### GREEN

- Implement two-lens filter parsing and source-aware loaders.
- Implement operating lens read/write panels from Spring.
- Implement procurement analytic lens from FastAPI with read-only UI.
- Implement normalized FastAPI export flow with builder reuse and limit enforcement.
- Leave shared navigation registration to a serialized controller-only integration step.

### REFACTOR

- Extract lens-dispatch helpers only after both lenses prove stable.
- Consolidate export error handling and toast/banner mapping.
- Keep abstractions cost-local; do not create a generic multi-lens framework for other domains.

## Implementation Steps

1. Freeze the two-lens URL contract and reject any `inventory` lens or `categoryId` filter shape.
2. Write contract tests for lens separation, UUID/code mapping, export metadata, and over-limit behavior.
3. Implement BFF loaders that dispatch by lens and normalize freshness/lineage metadata per source.
4. Implement operating lens UI against the existing Spring `cost-entries` and `cost-summaries` routes.
5. Implement procurement analytic lens against FastAPI using canonical codes derived from operational UUID filters.
6. Add a selected-format export service and normalized FastAPI endpoint, reusing the existing individual renderers and `_tmp` staging without invoking the eager all-format bundle path.
7. Register the FastAPI export router sequentially and document the serialization requirement in the integration handoff.
8. Finish with E2E for lens switching, export paths, and auth/error handling.

## Validation

- Focused:
  - `.\backend\mvnw.cmd -f .\backend\pom.xml -Dtest=OperatingCostHttpContractTest,CostRoutesTest test`
  - `python -m pytest tests/test_cost_report_exports.py tests/test_internal_cost_exports.py`
  - `npm --prefix web run test -- cost-analysis`
  - `npm --prefix web exec playwright test --grep "@costs"`
- Broad:
  - `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
  - `python -m pytest`
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [x] `/costs` exposes exactly two lenses: `operating` and `procurement`.
- [x] Operating lens uses the existing Spring `cost-entries` and `cost-summaries` routes for append/correction and summaries.
- [x] Procurement remains read-only analytics; inventory value stays in the Inventory area.
- [x] Export is a real server-mediated FastAPI contract reusing existing CSV/PDF/XLSX builders, not browser-built assembly.
- [x] Each export request invokes exactly one selected renderer; CSV/PDF requests never initialize the XLSX runtime or build the other formats.
- [x] Exports above 25k rows or 10 MiB are rejected with typed over-limit feedback.
- [x] Successful exports carry safe metadata and never expose filesystem paths.
- [x] No `PATCH` cost route, no `operating-costs` route, and no new Spring controller is introduced.

## Risks And Rollback

- High: a third `inventory` lens could creep back in and duplicate the Inventory area.
  - Mitigation: hard-fail unknown lenses in tests and schema parsing.
- High: export metadata or errors can leak filesystem paths.
  - Mitigation: safe metadata allowlist and `_tmp`-only staging tests.
- Medium: procurement analytics can drift if UUID filters are passed straight through instead of canonical codes.
  - Mitigation: explicit UUID -> code mapping tests before analytics/export calls.
- Medium: router registration conflicts can break FastAPI startup if done in parallel.
  - Mitigation: acknowledge sequential router registration as a serialized integration step.
- Rollback:
  - Disable export entrypoints first if manifest or sizing guarantees fail.
  - Hide `/costs` route if two-lens separation proves unreliable; no persisted cost data requires rollback from analytics reads.

## Dependencies And Ownership

- Hard blockers: Phases 2, 3, and 4 complete.
- Parallel safety:
  - Do not edit overview, work, inventory, crop-health, or admin route trees.
  - Do not add or change Spring controllers in this phase.
  - Consume procurement analytics contracts without reopening Phase 2 internals unless controller serializes that work separately.
  - Do not edit shared shell/navigation registration files in this phase.
- Owned artifacts:
  - costs route tree and lens loaders
  - export helper, FastAPI export endpoint, and cost tests
  - sequential router registration handoff notes

## Commit Plan

1. `feat(web): add cost lens loaders and route filters`
2. `feat(web): render operating and procurement cost views`
3. `feat(api): add normalized cost export endpoint`
4. `test(costs): cover two-lens export limits and command paths`
