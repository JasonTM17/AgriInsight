---
phase: 5
title: "overview-and-farm-intelligence"
status: completed
priority: P1
effort: "3d"
dependencies: [2, 3, 4]
---

# Phase 5: overview-and-farm-intelligence

## Progress Snapshot — 2026-07-26

- Accepted locally on `/overview`, `/farms`, and `/farms/[farmId]`; not
  publicly or production released.
- Final unified runner passed clean `npm ci`, Spring and analytics contract
  drift checks, TypeScript, zero-warning ESLint, Next 16 production build with
  all routes dynamic, Maven package with tests skipped by gate, 9/9 PostgreSQL
  privilege tests, 82 web tests with 9 intentional skips, and 3/3 installed-
  Chrome scenarios.
- Browser E2E covered nonce CSP landing/login/custom 404, real Keycloak/Spring
  `/me`/PostgreSQL auth, and the period-preserving Overview -> Farms -> detail
  path plus direct reviewed WebP rendering.
- Shared rollout fixes stayed bounded to BFF query allowlists, request-scoped
  nonce CSP/custom 404, provenance-safe direct WebP and CSP-safe trend
  rendering, and owned/mutex-guarded E2E lifecycle.
- Cleanup: no listeners on 3100, 55443, or 58080-58082; no
  `agriinsight-web-e2e` Compose containers; no runtime roots.
- Disk checkpoint: C 8.71 GB free WARN under the 10/8 thresholds; D 28.91 GB
  free PASS under the 25/20 thresholds; WSL swap configured at
  `D:\Docker\wsl-swap.vhdx`.
- UI/UX review:
  [`reports/ui-ux-phase5-review-2026-07-26.md`](./reports/ui-ux-phase5-review-2026-07-26.md).
- Final evidence:
  [`reports/phase-05-overview-farm-intelligence-evidence-2026-07-26.md`](./reports/phase-05-overview-farm-intelligence-evidence-2026-07-26.md).
- Phase 6 Work Operations is next.

## Contract Remediation Slice

The checked-in Phase 2 analytics contract now accepts canonical `farm_code`,
`field_code`, `crop_code`, `season_code`, and `date_preset` on `/overview` and
`/farms`. Phase 5 resolves operational UUIDs server-side before analytics calls
while preserving bearer verification, Spring-derived authorization scope,
checksum-backed snapshot reads, and the existing unfiltered responses.

### Exact semantics

- Spring resolves every supplied UUID to an active canonical master:
  `farmId -> farmCode`, `fieldId -> fieldCode + farmId`,
  `cropId -> cropCode`, and
  `seasonId -> seasonCode + farmId + fieldId + cropId`.
- Web rejects unknown, inactive, cross-parent, or incomplete selections before
  calling analytics. FastAPI independently rejects a farm outside the effective
  authorization scope and any filter combination absent from the verified
  artifact relationship.
- FastAPI accepts canonical `farm_code`, `field_code`, `crop_code`,
  `season_code`, and `date_preset` on overview/farms.
- `all` uses the full verified snapshot. `last-30-days` includes event facts
  from `asOf - 29 days` through `asOf`. `season-to-date` requires
  `season_code` and includes events from that season start through `asOf`.
- Costs come from verified activity facts by `occurred_at`; revenue and harvest
  quantity come from verified harvest facts by `harvested_at`. The API performs
  all KPI and monthly aggregation; browser components only format returned
  values.
- The envelope exposes safe applied filters and resolved date bounds. It never
  exposes UUIDs, tenant claims from the browser, raw fact rows, or filesystem
  paths.

### Files and contracts

- FastAPI:
  `analytics_api/{snapshot_cache,models,read_models,routers/overview,routers/farms,response_envelope}.py`
- Checked-in contract:
  `docs/contracts/agriinsight-analytics-v1.openapi.json`
- Web BFF and adapters:
  `web/src/server/bff/allowed-operation.ts`,
  `web/src/features/overview/{load-operational-analytics-masters,resolve-analytics-codes,load-overview-view-model}.ts`,
  `web/src/features/farms/load-farm-intelligence-view-model.ts`
- Generated consumer:
  `web/src/server/generated/analytics/schema.d.ts`
- Tests:
  `tests/analytics_api/*`, `web/tests/contracts/*`

### Validation and rollback

- TDD covers parent consistency, foreign/out-of-scope filters, all three date
  presets, exact server aggregates, unfiltered backwards compatibility, and
  OpenAPI drift.
- Rollback removes the new optional query parameters and applied-filter
  metadata; the original unfiltered overview/farms behavior remains unchanged.
- Custom date ranges, browser-side aggregation, new persistence, realtime
  facts, and public API exposure remain out of scope.

## Overview

Deliver the post-login overview and farm intelligence surfaces by combining reconciled Spring masters with FastAPI Gold envelopes keyed by canonical codes. UI routes may deep-link by operational UUID, but the server resolves every farm/field/crop/season identifier to the corresponding Spring code before analytics calls; the browser never computes KPIs.

## Context

- Verified Spring farm payload is minimal: `FarmResponse { id, code, displayName, active, version }` at `backend/src/main/java/com/agriinsight/backend/farm/api/FarmResponse.java:7`.
- Phase 2 owns the internal FastAPI Gold reads and artifact-backed lineage rules.
- Phase 3 owns session-safe BFF fetch, auth propagation, and upstream error mapping.
- Phase 4 owns shell, navigation, copy system, and shared layout primitives.
- FastAPI must verify bearer and effective scope independently; the web tier cannot pass trusted tenant or farm claims from the browser.
- This phase owns only overview/farm route trees, loaders, tests, and phase-local view-model adapters.

## Requirements

- Functional:
  - Render `/overview` as the default landing route after login.
  - Render `/farms` list and `/farms/[farmId]` drill-down with shared URL filter semantics.
  - Resolve UI `farmId`, `fieldId`, `cropId`, and `seasonId` UUID filters to canonical Spring codes server-side before calling FastAPI analytics.
  - Combine only verified Spring farm fields with Gold KPI/trend envelopes in one view model.
  - Support URL-driven filters for tenant-safe `farmId`, `seasonId`, `datePreset`, `cropId`, and operational status.
  - Show freshness, scope, and lineage beside each analytic panel, not hidden in a footer.
  - Render charts, tables, and contextual images with accessible text equivalents.
- Non-functional:
  - Never assume Gold rows contain UUIDs or `tenantId`.
  - No browser KPI math, percentile math, or aggregation over raw series.
  - No fake fallback data; partial upstream failure must render partial state explicitly.
  - No cross-tenant or out-of-scope filter expansion.
  - Lineage metadata must stay safe: `runId`, `contractVersion`, `asOf`, and checksum fingerprint only; no manifest filesystem paths.
  - First meaningful paint must tolerate one upstream being degraded without white-screening the page.

## Data Flow

1. Browser requests `/overview` or `/farms` with URL filters, optionally including a farm UUID.
2. Server route parses and canonicalizes filters before any upstream call.
3. BFF requests the necessary Spring farm/field/crop/season masters, rejects unknown/inactive/out-of-scope UUIDs, and resolves canonical codes.
4. BFF calls FastAPI Gold with server-held bearer plus those codes; FastAPI independently verifies bearer, permitted scope, and Phase 2 reconciliation before reading artifacts.
5. BFF merges Spring and Gold rows on canonical codes only, then projects the normalized view model to server components.
6. UI renders paired chart-plus-table sections, safe lineage metadata, and contextual images.
7. If one source fails, the unaffected source still renders with explicit degraded messaging.

## File Matrix

These are the fixed Phase 5 ownership targets under the Phase 3 `web/` layout; relocating them requires updating this plan and ownership checks first.

| Action | Path | Purpose |
| --- | --- | --- |
| CREATE | `web/src/app/(platform)/overview/page.tsx` | overview route entry |
| CREATE | `web/src/app/(platform)/overview/loading.tsx` | real loading state |
| CREATE | `web/src/app/(platform)/overview/error.tsx` | route-local failure state |
| CREATE | `web/src/app/(platform)/farms/page.tsx` | farm list route |
| CREATE | `web/src/app/(platform)/farms/[farmId]/page.tsx` | farm drill-down route |
| CREATE | `web/src/features/overview/load-overview-view-model.ts` | Spring + Gold merge loader |
| CREATE | `web/src/features/farms/load-farm-intelligence-view-model.ts` | farm-specific merge loader |
| CREATE | `web/src/features/overview/resolve-analytics-codes.ts` | farm/field/crop/season UUID -> canonical code mapping |
| CREATE | `web/src/features/overview/overview-filter-schema.ts` | canonical URL parsing |
| CREATE | `web/src/features/overview/components/*.tsx` | KPI, chart, table, image modules |
| CREATE | `web/src/features/farms/components/*.tsx` | list, detail, lineage modules |
| CREATE | `web/tests/contracts/overview-route.contract.test.ts` | UUID/code and lineage contract tests |
| CREATE | `web/tests/e2e/overview-farm-intelligence.spec.ts` | user-path validation |

## Interfaces And Contracts

- URL contract:
  - `farmId`: optional UUID, must be scope-checked then resolved server-side to canonical farm code before analytics access.
  - `fieldId`, `cropId`, `seasonId`: optional operational UUIDs; each is scope-checked and converted to its Spring master code before analytics use.
  - `datePreset`: enum only; reject arbitrary date math in browser state.
  - `cropId`, `status`: optional filters; must round-trip through links.
- Verified Spring contract:
  - `FarmResponse`: `id`, `code`, `displayName`, `active`, `version` only; do not invent area, manager, latest activity, tenant id, or image fields.
- Gold envelope contract consumed from FastAPI:
  - `scope`: canonical farm/season/date boundaries used to produce the panel.
  - `freshness`: `asOf` and refresh metadata.
  - `lineage`: `runId`, `contractVersion`, checksum fingerprint, and safe timestamps only.
  - `payload`: already-aggregated KPI/trend/table rows.
- Merge rules:
  - Spring is source of truth for farm UUID, canonical code, display name, active flag, and version.
  - Gold is source of truth for KPI values and analytic trend series.
  - Join on canonical codes only; never join on `tenantId` or assume analytics UUIDs exist.
  - If Gold references a code absent from current Spring scope, drop the analytic row and log a scope mismatch.

## TDD Track

### RED

- Add contract tests for farm/field/crop/season UUID -> code resolution, parent-child consistency, rejected unknown/inactive/out-of-scope UUIDs, and no-browser-math assertions.
- Add loader tests proving Spring-only success, Gold-only success, mixed success, and one-source failure.
- Add tests proving no UI path depends on `tenantId` or analytics UUIDs.
- Add accessibility tests for chart/table parity, contextual image alt text, and visible freshness/lineage labels.
- Add E2E covering login -> overview -> farms list -> farm drill-down with sharable URL filters.

### GREEN

- Implement filter schema, UUID -> code resolver, server loaders, and source-specific mappers.
- Implement overview/farm pages with paired visual and tabular renderers.
- Implement partial-failure presentation and safe lineage/freshness badges.
- Keep route exposure self-contained; hand shared shell navigation registration to a serialized controller-only integration step.

### REFACTOR

- Extract shared UUID/code resolution and scope badge primitives if they remain phase-local.
- Remove duplicate mapper branches once both overview and farm drill-down prove stable.
- Keep shared primitives inside this phase boundary; do not reopen Phase 4 shell files unless controller approves.

## Implementation Steps

1. Freeze the URL filter schema and deep-link semantics before any UI rendering work.
2. Write contract tests that prove every operational UUID filter resolves through its scoped Spring master/parent chain and no KPI aggregation happens in client components.
3. Implement overview loader that maps UUID filters to canonical codes, then fetches the authorized Spring/Gold views with explicit dependency ordering and partial failure states.
4. Implement overview route with KPI cards, trend section, exceptions table, and safe freshness/lineage metadata.
5. Implement farms list route with filter persistence and scope-safe drill links that carry UUIDs while analytics calls use canonical codes.
6. Implement farm detail route with verified Spring farm fields plus analytic envelope panels from Gold.
7. Add accessibility hardening: chart summaries, keyboard order, contextual image alt text, and visible degraded-state messaging.
8. Finish with E2E and visual smoke coverage under desktop and narrow tablet widths.

## Validation

- Integrated:
  - `powershell -ExecutionPolicy Bypass -File scripts/run-web-e2e-tests.ps1`
- Focused:
  - `npm --prefix web run test -- overview-route farm-intelligence`
  - `npm --prefix web run test:e2e -- --grep "overview|farm intelligence"`
  - `npm --prefix web exec playwright test --project=chromium --grep "@overview"`
- Broad:
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [x] `/overview` becomes the post-login default and renders without browser-side KPI aggregation.
- [x] Farm/field/crop/season UUID filters are scope-checked and resolved server-side to reconciled canonical codes before analytics access.
- [x] `/farms` and `/farms/[farmId]` share current supported URL filters and keep deep links stable.
- [x] Real Keycloak/Playwright navigation proves the selected period survives
  Overview -> Farms -> Farm detail without client-side reconstruction.
- [x] Every analytic panel exposes scope, cutoff, freshness, and safe lineage metadata in visible UI.
- [x] Charts have equivalent tables or textual summaries; contextual images have real alt text and do not carry KPI meaning.
- [x] The view model never assumes Gold has UUIDs or `tenantId`.
- [x] Partial Spring or Gold failure renders explicit degraded UI instead of fake combined numbers.
- [x] Shared rollout fixes stayed bounded to BFF query allowlists, request-scoped nonce CSP/custom 404, provenance-safe direct WebP and CSP-safe trend rendering, and owned/mutex-guarded E2E lifecycle.

## Risks And Rollback

- High: UUID/code drift can silently mis-join overview analytics.
  - Mitigation: explicit UUID -> code resolver tests and code-only analytics joins.
- High: leaking manifest paths or unsafe lineage fields exposes internal filesystem details.
  - Mitigation: allowlist lineage fields to `runId`, `contractVersion`, `asOf`, and checksum fingerprint only.
- Medium: URL filters grow incompatible with other phase routes.
  - Mitigation: freeze schema now and reuse exact key names in cross-links only.
- Medium: developers may reintroduce unverified Spring farm fields into the view model.
  - Mitigation: lock the adapter to the verified `FarmResponse` shape.
- Rollback:
  - Hide overview/farm routes from navigation.
  - Remove route exposure without touching Spring or FastAPI state.

## Dependencies And Ownership

- Hard blockers: Phases 2, 3, and 4 complete and stable.
- Parallel safety:
  - Do not edit work, inventory, cost, crop-health, or admin route trees.
  - Shared shell/navigation received no feature-scope change. Controller-owned
    rollout fixes were limited to root request rendering/CSP, bounded BFF query
    plumbing, reviewed visual rendering, and E2E runner lifecycle.
- Owned artifacts:
  - overview/farms pages
  - overview/farms loaders, UUID/code resolvers, and mappers
  - overview/farms tests
  - route registration handoff notes for controller integration

## Commit Plan

1. `feat(web): add overview and farm intelligence route loaders`
2. `feat(web): render overview and farm intelligence views`
3. `test(web): cover overview filters, degraded states, and accessibility`
