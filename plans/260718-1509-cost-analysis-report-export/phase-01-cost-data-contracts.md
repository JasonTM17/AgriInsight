---
phase: 1
title: "Cost Data Contracts"
status: pending
priority: P1
effort: "1d"
dependencies: []
---

# Phase 1: Cost Data Contracts

## Overview

Add Gold contracts that make cost semantics and grain explicit. Keep the existing `executive_summary.total_cost_vnd` behavior backwards compatible, while exposing separate operating-cost, procurement-spend, and inventory-valuation measures for the new report.

## Context Links

- Research: `./research/research-cost-data-contracts.md`
- `src/agriinsight/sqlite_schema.sql`, `src/agriinsight/warehouse.py`
- `src/agriinsight/metrics.py`, `src/agriinsight/metrics_inventory.py`
- `docs/data-contracts.md`, `docs/kpi-catalog.md`, `docs/architecture.md`

## Requirements

- Functional: emit `cost_summary`, `cost_monthly`, `cost_farm`, `cost_season`, `cost_activity`, `cost_activity_detail`, `procurement_summary`, `procurement_detail`, and `cost_reconciliation` Gold frames.
- Functional: primary activity grain is one `activity_id`; procurement grain is one `IN` `transaction_id`; valuation remains a stock snapshot and is not an expense.
- Non-functional: pre-aggregate facts before joins, deterministic ordering, no schema change, no NaN/Infinity for zero revenue or zero usage, preserve existing Gold keys.

## Architecture

`build_gold_datasets()` delegates to `build_cost_analysis_gold(connection)`. The cost module aggregates activity and harvest facts separately at season/farm/month grains, then joins only one-row-per-key frames. A procurement module aggregates inbound inventory separately. Reconciliation rows compare activity total to material + labor and actual to season budget; they never bridge inventory `OUT` to activity because no allocation ledger exists.

## Related Code Files

- Create: `src/agriinsight/metrics_cost_analysis.py`
- Create: `src/agriinsight/metrics_cost_procurement.py`
- Modify: `src/agriinsight/metrics.py`
- Modify: `tests/test_pipeline.py`
- Modify: `docs/data-contracts.md`, `docs/kpi-catalog.md`, `docs/architecture.md`
- Do not modify: `src/agriinsight/sqlite_schema.sql`

## Implementation Steps

1. Define typed column contracts and SQL helpers for season, farm, month, activity, and procurement grains; use explicit `operating_*` and `procurement_*` names.
2. Add pre-aggregated season cost/revenue/yield and budget variance; derive farm/month/activity views from those or from independently aggregated facts.
3. Add detail queries with one-to-one dimension joins and stable sort keys; restrict procurement spend to inbound transactions and preserve supplier/warehouse/material identity.
4. Return new frames from `metrics.py`; let existing pipeline table writer/manifest include them without changing the warehouse loader.
5. Add reconciliation and anti-double-count assertions, then update contracts/KPI/architecture docs with grains and non-COGS limitation.

## Success Criteria

- [ ] New Gold CSVs are present after a default and small pipeline run.
- [ ] `sum(cost_activity_detail.total_cost_vnd)` equals the activity fact total; material + labor equals total per row and per rollup.
- [ ] Procurement spend equals only `IN` `total_amount_vnd`; inventory value is absent from operating-cost totals.
- [ ] Season/farm/month joins do not fan out; zero-revenue and zero-usage cases remain finite.
- [ ] Existing pipeline tests and foreign-key checks remain green.

## Tests / Validation

Run `pytest tests/test_pipeline.py -q`, then `python -m compileall -q src tests`. Query the SQLite facts independently to reconcile every new frame. Compare two same-seed runs byte-for-byte for new Gold CSVs.

## Risk Assessment

- **High:** fan-out or semantic merge inflates costs. Mitigate with independent pre-aggregation and explicit reconciliation tests.
- **Medium:** adding many frames increases artifact size. Keep only decision-useful columns and measure artifact growth on D.
- **Rollback:** revert the phase commit; old Gold frames and dashboard consumers remain available.

## Security Considerations

No user SQL or arbitrary column names enter this phase. Queries are static, dimensions are allowlisted by code, and no credentials are introduced.

## Next Steps

Phase 2 consumes these Gold frames through a validated report contract.
