---
phase: 1
title: Leakage-safe forecast contract and backtested baseline
status: completed
priority: P1
effort: 1.5d
dependencies: []
---

# Phase 1: Leakage-safe forecast contract and backtested baseline

## Overview

First preserve the two facts required for an honest historical label:
season-specific operated area and the timestamp at which a completed season
became available for training. Then build a pure, deterministic season-grain
forecaster over completed harvest events and active candidates. The
implementation must prove serving-origin isolation, multi-harvest aggregation,
finite arithmetic, descriptive historical spans, and temporal rolling-origin
error before Gold or API integration.

## Requirements

- Exact model version: `crop-median-yield-per-ha-v1`.
- Point estimate: median historical gross `harvest_quantity_kg / area_ha` for
  the same crop, multiplied by the active season field area.
- Descriptive span: same-crop observed historical minimum/maximum yield per
  hectare, scaled by current area. It is not a forecast, confidence, or
  prediction interval.
- Serving and backtest origin: season start at timezone-free midnight. Every
  training label must have `completed_at < origin_timestamp`; all seasons with
  the same origin timestamp are evaluated as one group before any of their
  labels can enter later origins.
- Backtest metrics pool season-level kg/ha errors across every eligible
  evaluated season for the candidate crop:
  `MAE = mean(abs(predicted_kg_per_ha - actual_kg_per_ha))` and
  `WAPE_pct = 100 * sum(abs(error_kg_per_ha)) / sum(actual_kg_per_ha)`.
  Report both distinct origin count and evaluated season count. A zero WAPE
  denominator is null and prevents `ready`; round only final aggregates to six
  decimal places.
- Completed label: sum all harvest events for a completed season exactly once;
  require every event timestamp to be at or before the immutable season
  `completed_at`. Never train on a partial season label.
- Season denominator: immutable positive `season_area_ha` carried by the season
  source/warehouse record, not the mutable current field dimension.
- Minimums: three training seasons per origin, two backtest origins, and five
  completed same-crop seasons for `ready`.
- Candidate contract: unique active season, finite positive season area,
  `start_date <= as_of_date < expected_harvest_date`, canonical nonblank codes.
  Target yield is absent from the pure model input.
- Historical contract: unique harvest event IDs, consistent season context,
  finite non-negative gross quantity, positive season area, completed season,
  valid completion timestamp, and event/completion timestamps no later than
  the evaluated as-of cutoff.
- Output order and sorting are stable. Empty candidates produce the exact empty
  schema. Invalid in-window facts fail closed; non-date fields on future facts
  cannot alter an earlier forecast.
- The standard deterministic fixture adds the 2024 season cohort so 2025
  completed seasons can supply valid season-start backtest origins for 2026
  active crops. This is demo evidence, not real accuracy evidence.

## Architecture

```text
season-area/completion snapshot + bounded completed harvest events
  → aggregate all events to one complete season label
  → actual gross yield kg/ha by crop
  → median point + observed historical min/max
  → grouped season-start rolling-origin MAE/WAPE
  → versioned active-season forecast record
```

Phase 2 may attach `target_yield_kg` from the trusted season record as nullable
display context after the pure forecast is complete. It never enters point,
span, status, or backtest calculations.

## Related code files

- Create: `src/agriinsight/yield_forecast.py`
- Create: `src/agriinsight/yield_forecast_contract.py`
- Create: `src/agriinsight/yield_forecast_numeric.py` if finite/ULP helpers
  would otherwise make the main module exceed the project modularity boundary
- Modify: `src/agriinsight/synthetic.py`
- Modify: `src/agriinsight/transform.py`
- Modify: `src/agriinsight/quality.py`
- Modify: `src/agriinsight/sqlite_schema.sql`
- Modify: `src/agriinsight/warehouse.py`
- Modify: `src/agriinsight/metrics.py`
- Modify: `src/agriinsight/metrics_cost_analysis.py`
- Modify: `src/agriinsight/metrics_crop_health.py` only where area is a
  season-level denominator
- Modify: `src/agriinsight/demo_tenant_master_sql.py`
- Create: `tests/test_season_snapshot_contract.py`
- Modify: `tests/test_pipeline.py`
- Modify: `tests/test_cost_metrics.py`
- Modify: `tests/test_demo_tenant_bootstrap.py`
- Create: `tests/test_yield_forecast.py`
- Create: `tests/test_yield_forecast_contract.py`
- Read only: `src/agriinsight/inventory_demand_forecast*.py`
- Create after acceptance:
  `reports/phase-01-forecast-contract-evidence.md`

## Implementation Steps

1. Add RED source/transform/warehouse tests for `season_area_ha`,
   status-consistent `completed_at`, chronology, and the deterministic 2024
   history cohort. Reject missing/mutable denominators and impossible
   completion/event timing.
2. Implement the season snapshot evolution. Make realized farm/cost/crop-health
   season denominators and demo bootstrap use `season_area_ha`, make completed
   backend season dates derive from `completed_at`, and prove pipeline/bootstrap
   reconciliation without changing their public meaning.
3. Write RED forecast tests for known median/span/backtest results, future
   exclusion, equal-origin isolation, multiple harvest events per season,
   shuffled row stability, exact empty schema, sparse history, and non-finite
   overflow.
4. Add strict column/timestamp/identifier/numeric validation. Parse event and
   completion timestamps before cutoff filtering; validate other future-fact
   fields only after eligibility is known.
5. Aggregate complete labels by season with stable finite summation. Reject
   conflicting crop/field/area/completion context or duplicate event IDs.
6. Implement crop median, observed min/max, area scaling, and grouped
   season-start rolling-origin error without randomness, current targets,
   future labels, external packages, or network access.
7. Emit an exact season-grain schema with `ready` or
   `insufficient_history`, nullable evidence only where the status permits, and
   deterministic row order.
8. Run focused tests, adjacent inventory-forecast regressions, Ruff, and Python
   compile. Record the initial RED and final GREEN evidence.

## Success Criteria

- [x] Tests demonstrably fail before implementation and pass afterward.
- [x] Target yield is not accepted by the pure model input and therefore cannot
  affect point, span, status, or backtest evidence.
- [x] A label with `completed_at >= origin_timestamp`, a future event, or an
  equal-origin outcome cannot enter training for that origin.
- [x] Multiple harvest events for a completed season are summed once while its
  immutable season-area denominator is counted once.
- [x] Ready rows have five or more history seasons, two or more backtest
  origins, explicit evaluated-season count, finite ordered historical spans,
  defined pooled season-level WAPE, and exact model version.
- [x] Sparse history returns count/date evidence but null point/span/error
  metrics; zero actual denominator makes WAPE undefined and prevents `ready`.
- [x] Existing realized KPI, inventory forecast, API, and web meanings remain
  unchanged after the season source/warehouse evolution.

## Risks and rollback

- Synthetic history has three calendar years and target-correlated outcomes;
  tests prove software behavior, not real agronomic accuracy.
- Same-crop median ignores soil, irrigation, weather, and growth-stage signals;
  this is intentionally the simplest auditable baseline.
- Rollback must restore the prior source/schema/warehouse code and matching
  artifacts together; mixed generations are unsupported.

## Security

No tenant-selected model, dynamic SQL, provider, secret, network call, or
operational mutation is introduced.
