# Phase 1 Forecast Contract Evidence

Date: 2026-07-29
Scope: isolated Python inventory-demand forecast contract; no Gold/API/UI caller

## Accepted behavior

| Contract | Accepted result |
|---|---|
| Grain | one row per warehouse/material/base unit |
| History | at most 180 days ending at `as_of_date` |
| Point forecast | latest 90 dense daily OUT-demand days × 30-day horizon |
| Planning range | empirical rolling 30-day p10/p90, clamped around point forecast |
| Backtest | weekly rolling origins; exact MAE and WAPE; minimum two windows for `ready` |
| Status | `ready`, `no_demand`, or `insufficient_history` |
| Safety | deterministic ordering; ISO date-only strings; finite non-negative arithmetic; malformed eligible facts fail closed |
| Isolation | valid dates establish eligibility; malformed non-date fields outside the window cannot affect an earlier result |

The range is descriptive planning evidence, not a probabilistic confidence
interval. The forecast never creates a purchase order or mutates inventory.

## TDD chronology

1. RED: `python -m pytest tests\test_inventory_demand_forecast.py -q`
   failed during collection with `ModuleNotFoundError` for
   `agriinsight.inventory_demand_forecast` before implementation existed.
2. Initial GREEN: focused forecast and pipeline tests passed.
3. Review round 1 blocked aggregate overflow, missing/date coercion,
   out-of-window coupling, string quantity coercion, and incomplete metric
   assertions. Regression tests and fail-closed arithmetic/date boundaries were
   added.
4. Review round 2 blocked same-day row-order drift, `today`/`now` wall-clock
   parsing, and duplicate-column exception leakage. Permutation-stable summation,
   canonical date-only parsing, domain errors, and regression tests were added.
5. Review round 3 scored 9.5/10 and accepted the phase with no remaining code
   blocker.

## Verification

| Gate | Result |
|---|---|
| Root focused forecast + contract + pipeline | 29 passed |
| Independent tester focused gate | 29 passed |
| Independent edge replay | overflow/date/token/duplicate-column failures closed; out-of-window and row-order invariants passed |
| Three forecast modules | `py_compile` passed |
| Independent final review | PASS, 9.5/10, no blocker |
| Full Python regression | 291 passed, 3 intentional optional-report skips; 294 collected |

The first full regression used a global temp path containing the reserved
substring `_tmp`; one existing XLSX path-policy test correctly treated that as
an allowed staging ancestor and therefore missed its expected rejection. The
unchanged suite was rerun with `TEMP/TMP` at
`D:\AgriInsight\artifacts\test-temp-root`: 291 passed and 3 skipped. No product
code or existing test was weakened.

## Files

- `src/agriinsight/inventory_demand_forecast.py`
- `src/agriinsight/inventory_demand_forecast_contract.py`
- `src/agriinsight/inventory_demand_forecast_numeric.py`
- `tests/test_inventory_demand_forecast.py`
- `tests/test_inventory_demand_forecast_contract.py`

## Boundary and next work

Phase 1 has no production caller and changes no Gold, API, UI, schema, secret,
or environment contract. Phase 2 must materialize the accepted record into Gold
and decision support. Phase 3 must expose the server-computed evidence, pass
hosted acceptance, then generate the verified SVG/PNG architecture diagram and
UI GIF. No placeholder visual is claimed as forecast evidence.

Docker, browser, and big-data gates were intentionally not run locally while C
and D remained below heavy-work floors. Hosted gates remain mandatory when the
forecast becomes part of runtime images.

## Unresolved questions

- None for the isolated Phase 1 contract.
