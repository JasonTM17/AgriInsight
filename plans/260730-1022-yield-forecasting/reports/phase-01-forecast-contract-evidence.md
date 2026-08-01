# Phase 1 forecast contract evidence

Date: 2026-08-01  
Status: accepted locally; Gold/API/release work remains in later phases.

## Delivered boundary

- Seasons retain immutable `season_area_ha` and an explicit, timezone-free
  `completed_at`; invalid source rows are quarantined and invalid warehouse
  inputs fail before loading.
- The pure forecast accepts completed harvest facts and active season context,
  never a current target yield. It aggregates harvest events at season grain,
  uses same-crop historical median kg/ha, and scales only by immutable season
  area.
- Serving and backtest training both require `season_completed_at < origin`.
  Backtests group equal season-start origins, use only earlier completed
  labels, and report pooled season-level MAE/WAPE only when coverage is ready.
- The deterministic synthetic fixture now supplies a 2024 history cohort. This
  supports software-contract backtests, not a production agronomic accuracy
  claim.

## RED to GREEN evidence

Initial contract tests were written before the snapshot fields, chronology
validation, 2024 cohort, and forecast modules existed. The source/warehouse
tests failed against the old contract; the pure forecast test collection also
failed because `agriinsight.yield_forecast` did not exist. Implementation was
then added until the focused suite passed.

The final focused recheck after the last correction returned exit code 0:

```powershell
$env:PYTHONPATH='src'
python -m ruff check src/agriinsight/season_snapshot_validation.py src/agriinsight/transform.py src/agriinsight/quality.py src/agriinsight/warehouse.py tests/test_season_snapshot_contract.py tests/test_season_snapshot_quality.py
python -m pytest tests/test_season_snapshot_contract.py tests/test_season_snapshot_quality.py tests/test_yield_forecast_contract.py tests/test_yield_forecast.py tests/test_yield_forecast_backtest.py -q
```

Result: `44 passed`; Ruff: `All checks passed!`.

The full affected regression gate returned exit code 0 for all test suites:

```powershell
$env:PYTHONPATH='src'
python -m pytest tests/test_season_snapshot_contract.py tests/test_season_snapshot_quality.py tests/test_pipeline.py tests/test_cost_metrics.py tests/test_demo_tenant_bootstrap.py tests/test_demo_tenant_reconciliation.py tests/test_yield_forecast.py tests/test_yield_forecast_backtest.py tests/test_yield_forecast_contract.py tests/test_inventory_demand_forecast.py tests/test_inventory_demand_forecast_contract.py tests/test_inventory_demand_forecast_gold.py tests/test_inventory_demand_forecast_gold_contract.py -q
```

Result: `116 passed`. Python Ruff on every changed Python source/test and
`python -m compileall -q src/agriinsight tests` also returned exit code 0. A
separate accidental attempt to pass `sqlite_schema.sql` to Python Ruff failed
because Ruff parses Python, not SQL; the corrected Python-only command passed
without changing code.

## Review trail

- Stage 1 controller review checked Phase 1 requirements against the final
  implementation and tests.
- Stage 2 final reviewer found no actionable defects and confirmed chronology,
  aggregation, origin isolation, finite arithmetic, and sparse-history
  semantics.
- Stage 3 adversarial review found one medium data-integrity defect: an active
  season with an impossible-but-regex-shaped completion timestamp could become
  null during transformation. The fix rejects any raw non-null active
  completion value and any non-null completion that parses to `NaT` in
  transform, quality reporting, and direct warehouse validation. The new
  regression proves all three paths reject `2025-02-31T00:00:00`.
- The Stage 3 recheck was time-boxed without a returned report; it is not used
  as acceptance evidence. Fresh focused/integrated tests and the final Stage 2
  review cover the corrected implementation.

## Scope and operational boundary

`metrics_crop_health.py` was inspected but not modified: it uses field area as
display context, not a season-level realized-yield denominator. Existing crop
health, inventory-demand forecasting, realized KPI, and bootstrap
reconciliation regressions are included above.

No secret, provider call, tenant-selected model, dynamic SQL, live PostgreSQL
ingestion, operational mutation, Docker action, hosted browser action, or
production deployment is introduced by this phase. Local verification ran with
C drive at least 14.52 GiB free and D drive 20.37 GiB free; heavyweight hosted
gates remain Phase 3/4 work.

## Unresolved questions

None for the Phase 1 local contract. Gold publishing, scoped API/UI,
hosted acceptance, and package publication remain intentionally unstarted
Phase 2-4 deliverables.
