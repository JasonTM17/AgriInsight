# Code Review Summary — Yield Forecast Phase 3

## Scope

- Focus: current Phase 3 API, BFF, generated contract, detail loader, runtime schema, and panel changes.
- Read-only review. No Docker, build, lint, typecheck, or test command ran.
- Scout coverage: import/caller search traced the API router, BFF allowlist, generated `AnalyticsResponse`, detail route, farm-list links, filter resolver, and timestamp pipeline.

## Overall Assessment

Do not land the current frontend integration unchanged. The API authorization and bounded pagination path fail closed, but two user-visible evidence-integrity defects remain.

## High Priority

### [H1] Timezone-free history evidence is shifted before display

- Evidence: the data contract only accepts timezone-free timestamps in [`src/agriinsight/season_snapshot_validation.py`](../../../src/agriinsight/season_snapshot_validation.py) lines 10-12 and serializes them without an offset at lines 32-37. The yield producer returns those values unchanged at [`src/agriinsight/yield_forecast.py`](../../../src/agriinsight/yield_forecast.py) lines 173-182.
- Defect: [`web/src/features/farms/yield-forecast-formatters.ts`](../../../web/src/features/farms/yield-forecast-formatters.ts) lines 51-54 passes a no-offset ISO timestamp to `new Date(value)`, which JavaScript interprets in the runtime's local timezone, then formats it with `timeZone: "UTC"`. A `2025-06-01T12:00:00` completion can therefore render as a different clock time depending on where Next/browser runs.
- Impact: the panel presents a changed training-history boundary while claiming server evidence is displayed verbatim. The existing test at [`web/tests/contracts/yield-forecast.test.ts`](../../../web/tests/contracts/yield-forecast.test.ts) lines 139-142 only uses a `Z` timestamp and cannot detect the production path.
- Required fix: keep the existing timezone-free semantics and format the calendar/time components without constructing a timezone-bearing `Date`, or change the API/data contract end-to-end to emit an explicit offset. Add tests for a no-offset timestamp under a non-UTC runtime timezone.

## Medium Priority

### [M1] Forecast scope ignores active detail filters

- Evidence: farm list links carry the current filters into a farm detail URL at [`web/src/features/farms/components/farm-list.tsx`](../../../web/src/features/farms/components/farm-list.tsx) lines 99-102. The detail loader uses resolved `field`, `crop`, and `season` filters for observed Farm Performance at [`web/src/features/farms/load-farm-detail-view-model.ts`](../../../web/src/features/farms/load-farm-detail-view-model.ts) lines 49-55, but sends the forecast endpoint only `farm_code`, `limit`, and `offset` at lines 58-62.
- Impact: after navigating from a field/crop/season-filtered farm list, the realized-performance section follows the selected relationship while forecast rows and `forecastHealth` aggregate every active season for the farm. The page thus shows incompatible scopes even though the new endpoint and BFF expressly support canonical `field_code`, `crop_code`, and `season_code` filters.
- Required fix: derive one forecast query from `toAnalyticsFilterQuery(filters, resolved)`, retain the canonical farm code, and add a loader test proving all supported filters reach `analyticsYieldForecast` and constrain its health/rows consistently.

## Checked Without Finding a Blocker

- The route authorizes `FARMS` before snapshot access, rejects explicitly foreign farm filters, validates the snapshot/reconciliation state, fixes ordering to expected-harvest then season code, checks snapshot currency, and applies the serialized-response cap: [`src/agriinsight/analytics_api/routers/yield_forecast.py`](../../../src/agriinsight/analytics_api/routers/yield_forecast.py) lines 64-92.
- The read model scopes before filtering, calculates health from the scoped relation, uses stable fixed ordering, and constrains each page to the API limit: [`src/agriinsight/analytics_api/yield_forecast_read_models.py`](../../../src/agriinsight/analytics_api/yield_forecast_read_models.py) lines 49-66.
- `Promise.allSettled` keeps the Spring-resolved farm identity and realized analytics available if only forecast retrieval/validation fails: [`web/src/features/farms/load-farm-detail-view-model.ts`](../../../web/src/features/farms/load-farm-detail-view-model.ts) lines 47-91.
- BFF adds only the intended read operation and has no caller-selected tenant/model/sort parameter: [`web/src/server/bff/allowed-operation.ts`](../../../web/src/server/bff/allowed-operation.ts) lines 128-140.
- The runtime schema binds to generated TypeScript, rejects unexpected properties, non-finite numerics, counter/page inconsistencies, foreign farm rows, and duplicate season grain: [`web/src/features/farms/yield-forecast-contract-schema.ts`](../../../web/src/features/farms/yield-forecast-contract-schema.ts) lines 143-216.

## Plan Follow-up

Phase 3 remains in progress. Its hosted CI, browser/a11y, candidate-image, media, and documentation evidence requirements are not proven by this source-only review; do not mark the phase accepted before those gates and the two findings are resolved.

## Unresolved Questions

- None for the two findings; both are reproducible from the current data and UI contracts.

## Re-review — H1/M1 remediation

- **H1 resolved.** [`web/src/features/farms/yield-forecast-formatters.ts`](../../../web/src/features/farms/yield-forecast-formatters.ts) lines 18 and 52-60 recognize the timezone-free contract form and render its clock/calendar components without `Date` conversion. Offset-bearing values retain the UTC formatter path. [`web/tests/contracts/yield-forecast.test.ts`](../../../web/tests/contracts/yield-forecast.test.ts) lines 139-143 now proves the no-offset value renders as `01:00 30/07/2026`.
- **M1 resolved.** [`web/src/features/farms/load-farm-detail-view-model.ts`](../../../web/src/features/farms/load-farm-detail-view-model.ts) lines 50-66 derives canonical resolved filters once and forwards field, crop, and season codes to `analyticsYieldForecast` while retaining the resolved farm code and bounded paging. [`web/tests/contracts/farm-intelligence.test.ts`](../../../web/tests/contracts/farm-intelligence.test.ts) lines 308-372 exercises resolved UUID masters and asserts the exact scoped forecast query.
- Re-review result: both previously accepted findings are resolved in source. `git diff --check` for the touched files passes. No test/build command was run in this read-only re-review.
