---
phase: 1
title: Forecast contract and backtested baseline
status: in-progress
priority: P1
effort: 6h
dependencies: []
---

# Phase 1: Forecast contract and backtested baseline

## Overview

Create the first real forecasting contract over warehouse/material OUT
movements. The pure implementation uses only historical base-unit facts on or
before `as_of_date`, emits a deterministic 30-day baseline, and proves its
error with rolling-origin backtests.

## Requirements

- Functional: one row per warehouse/material; 30-day point forecast; bounded
  lower/upper empirical range; history coverage; model version; data status;
  rolling-origin MAE and WAPE.
- Non-functional: deterministic; no new dependency; no random seed; no future
  fact access; finite non-negative outputs; stable column order; clear empty,
  no-demand, and insufficient-history behavior.
- Contract: model name `mean-daily-usage-90d-v1`; horizon 30 days; maximum
  training window 180 days; forecast lookback 90 days; weekly-spaced backtest
  origins; minimum two complete backtest windows for `ready` status.

## Architecture

```text
bounded OUT movements → dense daily base-unit demand
                      → 90-day mean × 30-day horizon
                      → historical rolling 30-day totals (p10/p90 range)
                      → weekly rolling-origin predictions vs actual totals
                      → versioned forecast/backtest record
```

Forecast range is an empirical planning range, not a probabilistic confidence
claim. Point forecast is clamped inside the range. `no_demand` returns zeros;
`insufficient_history` returns a bounded descriptive record and never invents
accuracy metrics.

## Related Code Files

- Create: `src/agriinsight/inventory_demand_forecast.py`
- Create: `tests/test_inventory_demand_forecast.py`
- Read: `src/agriinsight/metrics_inventory.py`
- Read: `src/agriinsight/sqlite_schema.sql`
- Create: `reports/phase-01-forecast-contract-evidence.md` after acceptance

## Implementation Steps

1. Write tests first for ready/no-demand/insufficient-history, dense zero days,
   exact as-of cutoff, future-row exclusion, stable schema, interval ordering,
   finite outputs, deterministic rerun, and rolling-origin metrics.
2. Implement strict input validation and dense daily-series construction over
   canonical warehouse/material/base-unit groups.
3. Implement the 90-day baseline, empirical 30-day p10/p90 range, and
   weekly-spaced rolling-origin backtest without external ML libraries.
4. Return stable typed records/DataFrame with explicit status and nullable
   backtest metrics; never silently coerce invalid quantities or dates.
5. Run focused tests, then inventory/pipeline regressions and Python compile.

## Success Criteria

- [ ] Tests fail before implementation and pass afterward.
- [ ] A future transaction cannot change a forecast for an earlier as-of date.
- [ ] Ready records have at least two complete backtest windows, finite MAE/WAPE,
  ordered non-negative range, and stable model version.
- [ ] Sparse/no-demand/insufficient inputs return documented states, not
  exceptions or fabricated accuracy.
- [ ] No existing Gold/API/UI contract changes in this phase.

## Risk Assessment

- Overstated sophistication: name and UI language remain “baseline”; no ML/SLA
  claim.
- Intermittent demand bias: expose nonzero-day/history/backtest evidence; a
  later model may add Croston/SBA behind a new version.
- Leakage: cutoff and rolling origins are asserted in tests.

## Security Considerations

No tenant identifiers leave existing authorized datasets. No provider, secret,
network, dynamic code, or user-controlled SQL is introduced.
