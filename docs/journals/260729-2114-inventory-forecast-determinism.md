# Inventory Forecast Determinism

**Date**: 2026-07-29 21:14
**Severity**: High
**Component**: Inventory demand forecast Phase 1 contract
**Status**: Resolved

## What Happened

Phase 1 opened with a hard RED. `python -m pytest tests\test_inventory_demand_forecast.py -q` died during collection with `ModuleNotFoundError` for `agriinsight.inventory_demand_forecast`. After the module landed, the review cycle kept finding real contract leaks: overflow and `NaN` handling were not fail-closed, missing dates could still influence results, same-day row order drifted because of sequential `+=`, wall-clock strings like `today` and `now` were being accepted, and duplicate columns leaked raw pandas errors instead of a domain error.

## The Brutal Truth

This was annoying in the exact way bad numeric code always is. The first version looked fine until adversarial inputs and permutation tests proved it was not deterministic, not strict, and not safe to trust. Accepting ordering-dependent output or permissive parsing would have made the contract easier to ship and much harder to defend later.

## Technical Details

- Missing-module RED: collection failed before any forecast code existed.
- Overflow and `NaN` were closed with fail-closed numeric helpers, `math.fsum`, and bounded arithmetic checks.
- Same-day drift came from sequential `+=`; that was removed in favor of permutation-stable aggregation.
- Date handling was tightened to exact `YYYY-MM-DD` parsing and timezone-free dates only.
- Duplicate required columns now raise `InventoryDemandForecastError` instead of surfacing raw pandas exceptions.
- Final review scored 9.5/10 and accepted the phase.
- Focused gate: 29 passed.
- Full regression: 291 passed, 3 skipped.
- One early full run was a false failure because the temp path included `_tmp`; rerunning with `D:\AgriInsight\artifacts\test-temp-root` fixed it without weakening tests.

## What We Tried

- Rejected the shortcut of accepting row-order-dependent arithmetic.
- Rejected permissive date coercion for `today` and `now`.
- Added domain errors at the boundary instead of leaking library exceptions.
- Verified the hardened contract with focused review and full regression.

## Root Cause Analysis

The root cause was a sloppy contract boundary. The code trusted input shape too much, let numeric edge cases leak into behavior, and treated parsing convenience as if it were validation. That is how determinism gets lost quietly.

## Lessons Learned

- Forecast code must fail closed, not guess.
- Determinism is a contract, not an implementation detail.
- If a parser accepts wall-clock words, the boundary is already too loose.
- Library exceptions are not a useful public contract.

## Next Steps

- Phase 2 owner must preserve this exact contract when wiring Gold integration, especially deterministic ordering, strict date parsing, and fail-closed numeric checks.
- Phase 3 must not add SVG, PNG, or GIF outputs until the UI accepts the server-computed contract unchanged.
- Keep the next review focused on contract drift, not convenience.

## Unresolved Questions

- None for the Phase 1 contract.
