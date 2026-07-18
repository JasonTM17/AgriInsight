---
title: Research Report - Cost Data Contracts
date: 2026-07-18
plan: 260718-1509-cost-analysis-report-export
scope: cost-analysis drill-down without double counting
status: done
---

# Cost Data Contracts Research

## Summary

The current model is already close to usable for a production-grade cost drill-down, but only if the report contract keeps **operating cost**, **inventory procurement spend**, and **inventory value** as separate measures.

The biggest risk is not a bad fact table. It is semantic bleed: `fact_crop_activity.total_cost_vnd` is an operating cost fact, while `fact_inventory_transaction.total_amount_vnd` is procurement spend, and `metrics_inventory.py` turns inbound transactions into a balance-sheet style `inventory_value_vnd`. If an export or dashboard sums these together, it will double count economics.

## Evidence Base

Primary sources reviewed:

- `README.md`
- `CLAUDE.md`
- `docs/data-contracts.md`
- `docs/kpi-catalog.md`
- `docs/architecture.md`
- `src/agriinsight/sqlite_schema.sql`
- `src/agriinsight/warehouse.py`
- `src/agriinsight/transform.py`
- `src/agriinsight/transform_inventory.py`
- `src/agriinsight/synthetic.py`
- `src/agriinsight/synthetic_inventory.py`
- `src/agriinsight/metrics.py`
- `src/agriinsight/metrics_inventory.py`
- `tests/test_pipeline.py`

Confidence is high because the sources are the actual contract docs, schema, loaders, transformers, and tests.

## Reusable Contracts

| Contract | Reuse value |
|---|---|
| `fact_crop_activity` | canonical operating-cost fact; already carries `material_cost_vnd`, `labor_cost_vnd`, `total_cost_vnd` |
| `fact_harvest` | revenue and yield fact; needed for profit and cost-per-kg |
| `fact_inventory_transaction` | procurement, stock movement, and inbound cost basis |
| `dim_season` | budget envelope, target yield, status; needed for variance and completed-season filters |
| `dim_material` | base unit, reorder point, target stock, reference cost; needed for procurement and valuation |
| `dim_warehouse` | location grain for inventory drill-down |
| `dim_supplier` | procurement attribution |
| `dim_farm`, `dim_field`, `dim_date` | rollups and time slicing |

## Exact Grain And Dimensions

| Fact | Grain | Dimensions already present | Notes |
|---|---|---|---|
| `fact_crop_activity` | one activity execution | `date_key`, `farm_key`, `field_key`, `crop_key`, `season_key`, `activity_type_key` | correct grain for labor/material cost drill-down |
| `fact_harvest` | one harvest event | `date_key`, `farm_key`, `field_key`, `crop_key`, `season_key` | revenue/yield only, not cost |
| `fact_inventory_transaction` | one SKU movement at one warehouse on one day | `date_key`, `warehouse_key`, `farm_key`, `material_key`, `supplier_key` | procurement + stock movement grain |
| `fact_sensor_reading` | one sensor reading at one timestamp | irrelevant to cost drill-down | keep out of cost exports |
| `fact_weather_daily` | one farm/day weather record | irrelevant to cost drill-down | keep out of cost exports |

Current warehouse build confirms these grains in `src/agriinsight/warehouse.py` and `src/agriinsight/sqlite_schema.sql`.

## Reconciliation Invariants

1. `fact_crop_activity.total_cost_vnd = material_cost_vnd + labor_cost_vnd` after silver cleaning in `transform.py`.
2. Activity costs are non-negative and duplicate `activity_id` rows are quarantined before warehouse load.
3. Inbound inventory rows must have supplier, expiry date, and positive quantity after `transform_inventory.py`.
4. `quantity_base_unit` and `unit_cost_base_unit_vnd` are normalized to the material base unit; tonne is only converted to kg when the material base unit is kg.
5. `metrics.py` totals revenue from `fact_harvest` and total cost only from `fact_crop_activity`.
6. `metrics_inventory.py` values inventory from inbound cost basis or `dim_material.reference_unit_cost_vnd`, not from operating cost.
7. No source fact directly links inventory consumption to crop activity usage. That means there is no deterministic COGS bridge today.

## Double-Counting Risks

| Risk | Why it happens | Impact | Severity |
|---|---|---|---|
| Summing activity cost and inventory spend into one `total cost` | procurement spend is not the same as consumed operating cost | inflated cost, margin collapse | high |
| Adding inventory value to profit or cost totals | inventory value is a stock snapshot, not an expense | double count balance sheet as P&L | high |
| Joining season/activity/harvest facts before pre-aggregation | different grains fan out across many rows | multiplied cost and revenue | high |
| Treating outbound inventory as cost without allocation ledger | no FIFO/FEFO consumption ledger exists yet | implied COGS is fake | high |
| Rollup by material only across warehouses | same SKU can exist in multiple warehouses | duplicated stock value if not careful | medium |

Current code avoids the first two in the dashboard:

- `metrics.py` separates `cost_breakdown` from inventory metrics.
- `metrics_inventory.py` computes inventory value and ABC independently.

But there is still no explicit semantic barrier in the exported Gold contract. That is the real gap.

## Proposed Gold Datasets

| Dataset | Grain | Core columns | Purpose |
|---|---|---|---|
| `cost_analysis_summary` | season or farm-month | `farm_code`, `farm_name`, `season_code`, `crop_code`, `month`, `total_activity_cost_vnd`, `material_cost_vnd`, `labor_cost_vnd`, `revenue_vnd`, `profit_vnd`, `profit_margin_pct`, `cost_per_ha_vnd`, `cost_per_kg_vnd`, `budget_cost_vnd`, `budget_variance_vnd` | drill-down entry point |
| `cost_analysis_by_activity_type` | season + activity type | `season_code`, `activity_type`, `activity_count`, `material_cost_vnd`, `labor_cost_vnd`, `total_cost_vnd`, `share_pct` | identify cost drivers |
| `inventory_procurement_summary` | warehouse + material + month | `warehouse_code`, `material_code`, `supplier_code`, `month`, `inbound_qty_base_unit`, `inbound_spend_vnd`, `outbound_qty_base_unit`, `ending_stock_qty`, `average_inbound_unit_cost_vnd`, `days_of_supply`, `days_to_expiry` | procurement drill-down, not P&L |
| `inventory_valuation_snapshot` | warehouse + material + as_of_date | `warehouse_code`, `material_code`, `stock_qty`, `average_unit_cost_vnd`, `inventory_value_vnd`, `abc_class`, `stock_status` | balance sheet style stock view |
| `season_cost_reconciliation` | season | `season_code`, `budget_cost_vnd`, `actual_activity_cost_vnd`, `variance_vnd`, `target_yield_kg`, `actual_yield_kg`, `cost_per_kg_vnd` | audit and variance control |

Recommended rule: do not include `inventory_value_vnd` in any cost summary that is meant to represent actual operating expense.

## Edge Cases To Handle

- Zero revenue seasons: margin must stay `0`, not divide by zero.
- Zero usage materials: `days_of_supply` should stay null or zero, never infinity.
- Mixed unit inbound rows: tonne to kg conversion only when base unit is kg.
- Near-expiry stock: use earliest valid inbound batch date, not a synthetic average.
- Completed season with no harvest rows: treat as zero yield only if source data is truly absent, not silently as success.
- Multiple warehouses holding the same material: stock value must aggregate only after location-level valuation is correct.
- Duplicate or invalid bronze rows: rely on quarantine counts, not silent dedupe in Gold.

## Test Gaps

Existing tests prove the pipeline is internally consistent, but not the cost-contract boundaries.

Current coverage in `tests/test_pipeline.py` verifies:

- warehouse builds cleanly
- foreign keys pass
- executive profit reconciles to revenue minus `total_cost_vnd`
- inventory value sums to inventory status totals
- ABC classification is monotonic and bounded

Missing tests for the cost drill-down:

1. Assert `cost_analysis_summary.total_activity_cost_vnd` equals only `fact_crop_activity.total_cost_vnd`.
2. Assert procurement spend and inventory value are never merged into activity cost.
3. Assert season/farm rollups pre-aggregate each fact before join.
4. Assert a zero-revenue season does not produce NaN profit margin.
5. Assert inventory inbound rows with missing supplier/expiry remain rejected in Silver.
6. Assert cost drill-down and inventory drill-down can be exported together without duplicated spend.

## File Impact For Implementation

Likely files to change:

- `src/agriinsight/metrics.py` - add cost drill-down datasets and season/farm reconciliations
- `src/agriinsight/metrics_inventory.py` - keep procurement/value semantics explicit
- `src/agriinsight/pipeline.py` - write new Gold tables to artifact output
- `src/agriinsight/insights.py` - if narrative cards need the new cost summaries
- `tests/test_pipeline.py` - add reconciliation and anti-double-count tests
- `docs/kpi-catalog.md` - if user-facing KPI names change

No schema change is required for the current SQLite warehouse unless the new drill-down needs extra persisted contract tables. The safer path is to keep the warehouse unchanged and materialize new Gold CSVs.

## Recommendation

Ranked choice:

1. **Best fit:** split the export into separate Gold contracts for operating cost, procurement spend, and inventory valuation. This is the minimum change that prevents double counting and fits the current warehouse.
2. **Next step:** add a reconciliation dataset at season and farm grain so margin and cost variance are auditable.
3. **Later, only if needed:** build a real consumption ledger so inventory `OUT` can be allocated to crop activities and true COGS can be computed.

Why this ranking:

- keeps semantic boundaries clean
- matches current code shape
- avoids a risky schema redesign
- leaves room for proper COGS later

## Unresolved Questions

- Do you want cost drill-down at `season`, `farm-month`, or `field-activity` as the primary UI pivot?
- Should procurement spend remain a separate inventory report only, or also appear in a finance-style summary as a non-P&L balance metric?
- Is future COGS attribution expected to be FIFO/FEFO, average cost, or a simpler allocation rule?
