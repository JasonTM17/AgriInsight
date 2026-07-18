# Cost Contracts Semantic Audit

Date: 2026-07-18
Scope: Phase 1 Cost Data Contracts, read-only audit
Artifact root: `D:\AgriInsight\artifacts\_tmp\cost-contract-audit`

## Executive Summary

Issue investigated: whether the new Phase 1 cost Gold contracts violate semantic boundaries by inflating rollups, mixing procurement/inventory into operating cost, or emitting NaN/Infinity / dropping rows on zero-denominator paths.

Root cause result: no defect proven. All 3 required failure hypotheses were eliminated by independent SQLite/Pandas reconciliation against a fresh small pipeline run and by code-path inspection.

Business impact: Phase 1 cost Gold contracts appear semantically sound for the audited dataset. Existing `executive_summary.total_cost_vnd` backward-compatible behavior still maps to `fact_crop_activity` total cost.

## Environment / Change Context

- Repo state relevant to this phase:
  - modified: `plans/260718-1509-cost-analysis-report-export/phase-01-cost-data-contracts.md`
  - modified: `src/agriinsight/metrics.py`
  - untracked: `src/agriinsight/metrics_cost_analysis.py`
  - untracked: `src/agriinsight/metrics_cost_procurement.py`
  - untracked: `tests/test_cost_metrics.py`
- Recent git history on these paths: `82da3ac feat(core): add deterministic analytics pipeline`
- TEMP/TMP forced to `D:\AgriInsight\artifacts\_tmp`
- C/D measurement:
  - before: `C used 228.93 GB free 2.89 GB`, `D used 214.15 GB free 29.79 GB`
  - after: `C used 229.00 GB free 2.82 GB`, `D used 214.37 GB free 29.58 GB`
  - audit artifact dir size: `0.60 MB`
- No intentional writes were directed to `C:`. Audit output and TEMP/TMP stayed under `D:\AgriInsight\artifacts\_tmp`.

## Verification Timeline

1. Read repo context: `README.md`, `CLAUDE.md`, phase file, schema, metrics modules, test file.
2. Ran targeted test: `python -m pytest tests/test_cost_metrics.py -q` with TEMP/TMP on `D:\AgriInsight\artifacts\_tmp` -> passed (`2 passed`).
3. Ran fresh small pipeline with Phase 1 test config into `D:\AgriInsight\artifacts\_tmp\cost-contract-audit`.
4. Queried warehouse facts independently and rebuilt expected rollups in Pandas.
5. Compared expected row counts, sums, keys, and finite-value behavior to emitted Gold CSVs.

## Evidence

Fresh audit dataset metrics:

- `fact_crop_activity`: `48` rows
- `fact_harvest`: `5` rows
- `dim_season`: `8` rows
- inbound inventory transactions: `72` rows
- operating total from facts: `105,718,075,000 VND`
- inbound procurement total: `6,661,680,700 VND`
- outbound inventory total: `5,298,008,700 VND`
- inventory valuation summary: `1,276,451,608.60 VND`

Gold row counts:

- `cost_monthly`: `14`
- `cost_farm`: `2`
- `cost_season`: `8`
- `cost_activity`: `48`
- `cost_activity_detail`: `48`
- `procurement_detail`: `72`
- `procurement_summary`: `70`

## Hypothesis Testing

### H1: fact fan-out inflates farm/season/month/activity rollups

Hypothesis status: eliminated.

Independent fact total: `105,718,075,000 VND`

Gold rollup totals:

- `cost_monthly.operating_total_cost_vnd` sum = `105,718,075,000`
- `cost_farm.operating_total_cost_vnd` sum = `105,718,075,000`
- `cost_season.operating_total_cost_vnd` sum = `105,718,075,000`
- `cost_activity.operating_total_cost_vnd` sum = `105,718,075,000`
- `cost_activity_detail.operating_total_cost_vnd` sum = `105,718,075,000`

Independent vs Gold group counts:

- `cost_monthly`: expected `14`, actual `14`
- `cost_farm`: expected `2`, actual `2`
- `cost_season`: expected `8`, actual `8`
- `cost_activity`: expected `48`, actual `48`
- `cost_activity_detail`: expected `48`, actual `48`

Duplicate-key checks:

- duplicate `activity_id` in `cost_activity_detail`: `0`
- duplicate natural key in `cost_activity` (`farm_code`,`field_code`,`season_code`,`crop_code`,`activity_type`): `0`
- duplicate `season_code` in `cost_season`: `0`

Independent numeric diffs vs Gold:

- `cost_monthly`: max abs diff for total/revenue/profit = `0`
- `cost_farm`: max abs diff for total/budget variance = `0`; `operating_cost_per_ha_vnd` diff `2.98e-08` only floating-point noise
- `cost_season`: max abs diff for total/budget variance/cost_per_kg = `0`
- `cost_activity`: max abs diff for activity_count/total/share = `3.55e-15` at worst; floating-point noise only
- `cost_activity_detail`: max abs diff for total/material/labor = `0`

Conclusion: no fan-out evidence. The Gold rollups reconcile exactly to fact grain.

### H2: procurement OUT or inventory valuation leaks into operating cost

Hypothesis status: eliminated.

Evidence chain:

- Operating cost facts total = `105,718,075,000 VND`
- Inbound procurement total = `6,661,680,700 VND`
- Outbound inventory total = `5,298,008,700 VND`
- Inventory valuation summary = `1,276,451,608.60 VND`
- `cost_summary.operating_total_cost_vnd` = `105,718,075,000 VND`, exactly the activity-fact total, not any activity+inventory blend
- `procurement_detail.procurement_spend_vnd` sum = `6,661,680,700 VND`
- `procurement_summary.procurement_spend_vnd` sum = `6,661,680,700 VND`
- non-`IN` rows in `procurement_detail` = `0`
- operating tables contain no `procurement*` columns and no `inventory_value_vnd` column

Code-path evidence:

- [`src/agriinsight/metrics_cost_analysis.py`](D:/AgriInsight/src/agriinsight/metrics_cost_analysis.py) operating-cost queries source `fact_crop_activity`, `fact_harvest`, and dimension tables only.
- [`src/agriinsight/metrics_cost_procurement.py`](D:/AgriInsight/src/agriinsight/metrics_cost_procurement.py) procurement queries explicitly filter `WHERE t.transaction_type = 'IN'`.
- [`src/agriinsight/metrics_inventory.py`](D:/AgriInsight/src/agriinsight/metrics_inventory.py) computes `inventory_value_vnd` only inside inventory Gold outputs, separate from cost Gold outputs.
- [`src/agriinsight/metrics.py`](D:/AgriInsight/src/agriinsight/metrics.py) still defines `executive_summary.total_cost_vnd` from `SUM(total_cost_vnd) FROM fact_crop_activity`, preserving backward-compatible semantics.

Conclusion: procurement and valuation semantics are separated as designed. No leakage proven.

### H3: zero denominators or nullable joins produce NaN/Infinity or dropped facts

Hypothesis status: eliminated.

Finite-value evidence:

- non-finite numeric cells across audited Gold frames:
  - `cost_summary`: `0`
  - `cost_monthly`: `0`
  - `cost_farm`: `0`
  - `cost_season`: `0`
  - `cost_activity`: `0`
  - `cost_activity_detail`: `0`
  - `procurement_summary`: `0`
  - `procurement_detail`: `0`
  - `cost_reconciliation`: `0`

Zero-denominator rows present in real audit data:

- zero-revenue seasons: `3`
- zero-harvest seasons: `3`
- zero-revenue months: `11`
- `operating_profit_margin_pct` on zero-revenue seasons: `[0.0, 0.0, 0.0]`
- `operating_cost_per_kg_vnd` on zero-harvest seasons: `[0.0, 0.0, 0.0]`

Dropped-row checks:

- `cost_season`: expected `8`, actual `8`
- `cost_farm`: expected `2`, actual `2`
- `cost_activity_detail`: expected `48`, actual `48`

Ordering checks:

- `cost_season` sorted as documented: `true`
- `cost_activity_detail` sorted as documented: `true`
- `procurement_detail` sorted as documented: `true`

Conclusion: real zero-revenue and zero-harvest cases were present and stayed finite. Nullable joins did not drop audited season/farm/detail rows.

## Elimination Summary

- Fan-out inflation ruled out by exact reconciliation of every operating-cost rollup to fact totals and by zero duplicate natural keys.
- Procurement/valuation leakage ruled out by both code-path separation and by exact matching of operating totals to activity facts only.
- NaN/Infinity / dropped-fact hypothesis ruled out by actual zero-revenue/zero-harvest rows remaining finite and by matching expected row counts.

## Root Cause

No semantic defect found in the audited Phase 1 implementation for the tested dataset. The evidence supports that the current design goal in the phase file is implemented correctly: pre-aggregated one-row-per-key rollups, procurement isolated to inbound inventory transactions, and finite outputs on zero-denominator paths.

## Recurrence Prevention / Monitoring Gap

- Keep the current reconciliation test in `tests/test_cost_metrics.py`; it is the main protection against future fan-out regressions.
- Add one explicit regression asserting that `executive_summary.total_cost_vnd == cost_summary.operating_total_cost_vnd` to protect backward-compat behavior.
- If Phase 2 adds report-layer joins, preserve the same one-row-per-key invariant before any farm/season/month merge.

## Unresolved Questions

None from this audit scope.

Status: DONE
Summary: Independent SQLite/Pandas reconciliation eliminated all 3 required hypotheses; no cost-contract semantic defect proven. Targeted test passed, rollups matched facts exactly, procurement stayed `IN`-only, and zero-revenue/zero-harvest rows remained finite without dropped audited rows.
Concerns/Blockers: None within audit scope. Before broad sign-off, re-run the same checks on the default pipeline dataset if lead wants larger-sample confidence.
