# Predictive capability scout report

## Summary

The batch MVP, warehouse, KPI, six analytics dashboards, secured business
backend, realtime foundation, alert center, RAG assistant, hosted CI, and four
image publication have evidence. Inventory “prediction” is currently only
30-day trailing usage. Yield/inventory/pest forecasting, anomaly detection,
what-if analysis, Text-to-SQL, and model monitoring remain future work.

## Findings

| Capability | Evidence | State |
|---|---|---|
| Bronze–Silver–Gold + star schema | `pipeline.py`, `warehouse.py`, `sqlite_schema.sql`, pipeline tests | Proven |
| Inventory 30-day need | `metrics_inventory.py` uses `usage_30d / 30 * 30` | Descriptive, not forecast |
| Forecasting/anomaly/what-if | No source/test symbols; roadmap lines 125–126 | Absent |
| RAG assistant | Guardrailed evidence retrieval/provider client and tests | Proven locally/hosted candidate |
| Text-to-SQL | Architecture explicitly says RAG is not Text-to-SQL | Absent |
| External production operations | OIDC, broker, recovery ownership and deployment docs | Owner-gated/partial |

## Recommendation

Implement a versioned, backtested inventory-demand baseline first. It has dense
warehouse facts, immediate business value, no secret/provider dependency, and a
clear path through Gold/API/Inventory UI. Keep advanced models behind new model
versions after the baseline establishes leakage, evaluation, scope, and
monitoring contracts.

## Scout execution notes

- Three delegated scouts were attempted. Service capacity/usage limits stopped
  their turns; controller completed evidence gathering directly with `rg` and
  source/doc inspection.
- C and D remain below heavy-work floors. Docker/browser/big-data validation
  must stay on hosted CI.

## Unresolved Questions

None required for Phase 1. Procurement automation and service-level accuracy
targets remain explicit later business-owner decisions.
