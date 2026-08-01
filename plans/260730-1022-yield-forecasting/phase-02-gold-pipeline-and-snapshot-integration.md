---
phase: 2
title: "Gold pipeline and snapshot integration"
status: in-progress
priority: P1
effort: "8h"
dependencies:
  - 1
---

# Phase 2: Gold pipeline and snapshot integration

## Overview

Materialize the accepted baseline as a checksummed `gold/yield_forecast.csv`
contract. The warehouse builder owns season/event aggregation and active-season
coverage; the API snapshot cache loads only a reconciled, exact, current
artifact. Existing farm and cost Gold files remain unchanged.

## Requirements

- One output row for every eligible active season in the warehouse.
- Exact grain: unique `season_code` with matching farm, field, and crop codes.
- Include as-of/model/status/history/backtest evidence, forecast origin,
  immutable season area, nullable target and expected-harvest context, point
  kg/ha and total kg, plus observed historical min/max kg/ha and scaled kg.
- Backtest evidence carries both distinct origin count and pooled evaluated
  season count; MAE/WAPE use the exact Phase 1 kg/ha formulas.
- Pipeline manifest records row count and SHA-256; rerun with identical input
  and as-of date is byte-stable.
- Reject duplicate grain, missing active-season coverage, stale or future
  dates, status/nullability drift, non-finite values, range inversion, derived
  quantity mismatch, and canonical relationship mismatch.
- The FastAPI snapshot verifies the new file checksum, exact columns, row cap,
  completion/origin/status/numeric invariants, and reconciliation before
  serving any request. Public responses remain unchanged until Phase 3.

## Related code files

- Create: `src/agriinsight/metrics_yield_forecast.py`
- Create: `src/agriinsight/metrics_yield_forecast_contract.py`
- Split temporal/status validators into descriptive modules only if the
  contract file would exceed the 200-line modularity threshold
- Modify: `src/agriinsight/metrics.py`
- Modify: `src/agriinsight/analytics_api/snapshot_cache.py`
- Modify only if required by discovered generic loading:
  `src/agriinsight/analytics_snapshot.py`
- Modify: `tests/test_pipeline.py`
- Create: `tests/test_yield_forecast_gold.py`
- Create: `tests/test_yield_forecast_gold_contract.py`
- Modify: `tests/analytics_api/test_snapshot_consistency.py`
- Modify: `docs/data-contracts.md` after behavior passes
- Create after acceptance:
  `reports/phase-02-gold-integration-evidence.md`

## Implementation Steps

1. Add RED warehouse tests for season/event aggregation, active coverage,
   exact schema, relationship consistency, checksum, byte-stable rerun,
   as-of cutoff, and failed numeric/status/temporal contracts.
2. Query bounded read-only harvest events and active candidates with explicit
   deterministic ordering; call the Phase 1 pure forecaster.
3. Validate every row against canonical warehouse season relationships and the
   expected active-season set before adding `yield_forecast` to the Gold
   dataset map.
4. Add exact Gold validators for grain, model, status/nullability, as-of,
   completion/origin boundaries, historical-span ordering, and the area-scaled
   point/min/max quantities.
5. Add snapshot loading/reconciliation with a conservative row cap. Keep
   existing public Farm Performance and Overview response projections intact.
6. Update the data contract only after implementation tests pass; run focused,
   pipeline, snapshot, inventory-forecast, and full Python hosted gates.

## Success Criteria

- [ ] `gold/yield_forecast.csv` covers every active warehouse season exactly
  once and contains no completed season.
- [ ] Manifest row count/checksum and identical-rerun bytes are verified.
- [ ] Future facts, duplicate events, multiple harvests, or mismatched
  season/farm/field/crop relationships cannot corrupt published evidence.
- [ ] Every history label used by a row was complete strictly before that
  row's season-start forecast origin.
- [ ] Corrupt but checksum-valid forecast files fail snapshot startup/reload
  before request handling.
- [ ] Existing farm/cost/inventory Gold and API contracts remain unchanged.
- [ ] Focused and hosted full Python gates pass.

## Risks and rollback

- Adding a required snapshot file means an old artifact set cannot serve new
  code; deployment guidance must require regenerating Gold before rollout.
- Multiple partial harvests are legal, so validators must aggregate season
  quantity without multiplying area.
- Rollback deploys the prior analytics image and prior artifact set together;
  never mix code and manifest generations.

## Security

The new Gold file contains business codes and aggregate agronomic values only.
It inherits existing artifact permissions and exposes nothing until Phase 3
authorization and scope shaping are accepted.
