# Yield forecasting scout report

Date: 2026-07-30

## Verified context

- Analytical warehouse already holds canonical farm, field, crop, season,
  field area, completed harvest events, and active season context.
- Gross harvest kg is the existing realized-yield numerator. Field area is the
  existing seasonal denominator; waste-adjusted net yield is a different
  business measure and remains out of this contract.
- The pre-plan standard data had five completed historical seasons for each
  active crop but lacked earlier outcomes for season-start backtesting. Phase 1
  therefore adds a deterministic 2024 cohort; this remains synthetic and is
  not evidence of real agronomic accuracy.
- Inventory forecasting provides reusable strict cutoff, deterministic
  numeric, rolling-origin, Gold checksum, snapshot, scoped API, browser, media,
  and release patterns. Daily-demand cadence is not reused.
- Existing `/farms` and Overview models represent realized filtered KPIs.
  Forecast evidence belongs in a separate endpoint and the current farm-detail
  experience.
- No unfinished plan blocks analytical source or hosted-CI acceptance.
  External hosting/OIDC/recovery and DeepSeek production SLO remain separate.

## Selected boundary

- Source: Python analytical warehouse, not direct PostgreSQL operational facts.
- Grain: one active season.
- Baseline: prior same-crop median realized gross kg/ha times current field
  area.
- Current target: display-only comparison context, never a feature.
- Evidence: observed historical min/max span and grouped season-start
  rolling-origin MAE/WAPE.
- UI: `/farms/[farmId]`; no tenth top-level product area.
- Delivery: three accepted phases, TDD, focused commits, hosted heavy gates,
  then protected four-image publication.

## Rejected shortcuts

- Target-calibration baseline: valid as planning calibration but artificially
  favored by the synthetic generator's target-to-actual construction.
- Per-field time-series model: only one or two completed seasons per field.
- Soil/irrigation/weather ML: history too sparse for a credible initial model
  and would add complexity without honest validation.
- Direct operational PostgreSQL training: no accepted versioned
  operational-to-analytics ingestion contract yet.

## Risks

- Sparse synthetic history can prove determinism and leakage controls only.
- Multiple harvest events per season must sum quantity without multiplying
  area.
- Historical labels require immutable season area and explicit completion time;
  current field area and harvest date alone are insufficient.
- Old artifacts cannot load after the snapshot contract requires the new Gold
  file; code and regenerated artifacts must deploy together.
- Forecast range must not be described as confidence or production accuracy.

## Unresolved questions

None blocking this baseline. Advanced features and external production
operations remain later milestones.
