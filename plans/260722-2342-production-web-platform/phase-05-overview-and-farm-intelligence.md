---
phase: 5
title: "overview-and-farm-intelligence"
status: pending
priority: P1
effort: "3d"
dependencies: [2, 3, 4]
---

# Phase 5: overview-and-farm-intelligence

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

- Focused:
  - `npm --prefix web run test -- overview-route farm-intelligence`
  - `npm --prefix web run test:e2e -- --grep "overview|farm intelligence"`
  - `npm --prefix web exec playwright test --project=chromium --grep "@overview"`
- Broad:
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [ ] `/overview` becomes the post-login default and renders without browser-side KPI aggregation.
- [ ] Farm/field/crop/season UUID filters are scope-checked and resolved server-side to reconciled canonical codes before analytics access.
- [ ] `/farms` and `/farms/[farmId]` share canonical URL filters and keep deep links stable.
- [ ] Every analytic panel exposes scope, freshness, and safe lineage metadata in visible UI.
- [ ] Charts have equivalent tables or textual summaries; contextual images have real alt text and do not carry KPI meaning.
- [ ] The view model never assumes Gold has UUIDs or `tenantId`.
- [ ] Partial Spring or Gold failure renders explicit degraded UI instead of fake combined numbers.
- [ ] No code outside the overview/farms route tree and phase-local view-model adapters is required for rollout.

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
  - Do not edit shared shell/layout or navigation registration files in this phase.
- Owned artifacts:
  - overview/farms pages
  - overview/farms loaders, UUID/code resolvers, and mappers
  - overview/farms tests
  - route registration handoff notes for controller integration

## Commit Plan

1. `feat(web): add overview and farm intelligence route loaders`
2. `feat(web): render overview and farm intelligence views`
3. `test(web): cover overview filters, degraded states, and accessibility`
