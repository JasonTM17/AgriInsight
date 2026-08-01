# Statistical contract review

Date: 2026-07-30

## Initial verdict

The same-crop median kg/ha baseline is the simplest explainable control, but
the first draft scored 5/10 because it backtested near harvest instead of at
the serving origin, lacked immutable label-completion and season-area facts,
and published sample p10/p90 at only five observations.

## Accepted corrections

- Forecast and backtest origin is season start.
- A complete label is available only at explicit `completed_at`; training
  requires `completed_at < origin_timestamp`.
- Season area is snapshotted on the season record.
- Equal-origin seasons are evaluated as a group before any outcome can train a
  later origin.
- Current target is removed from pure model inputs.
- P10/p90 is removed. V1 exposes only the observed historical min/max span.
- `ready` requires five historical labels, two valid backtest origins, three
  prior labels at each origin, finite MAE, and defined WAPE.
- Backtest errors pool evaluated seasons in kg/ha:
  `MAE = mean(abs(error))` and
  `WAPE_pct = 100 * sum(abs(error)) / sum(actual)`. The contract exposes both
  origin and evaluated-season counts, treats a zero denominator as undefined,
  and rounds only final aggregates.
- `insufficient_history` keeps count/date evidence but withholds point/span and
  error values.
- A deterministic 2024 fixture cohort enables honest 2025 season-start origins
  while remaining explicitly synthetic demo evidence.

## Rejected alternatives

- Recent-N crop median: arbitrary window without drift evidence.
- Farm-crop hierarchy: current per-farm history is too sparse.
- Target-ratio calibration: structurally advantaged by the synthetic generator.

## Remaining boundary

This milestone proves data/contract/software behavior. It does not prove
production agronomic accuracy, drift performance, external ingestion, or SLA.
