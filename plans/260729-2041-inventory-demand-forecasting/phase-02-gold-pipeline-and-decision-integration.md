---
phase: 2
title: "Gold pipeline and decision integration"
status: pending
priority: P1
effort: "6h"
dependencies: [1]
---

# Phase 2: Gold pipeline and decision integration

## Overview

Materialize the accepted forecast as a checksummed Gold contract and use it as
explicit decision support without silently rewriting existing stock policy.

## Requirements

- Add `inventory_demand_forecast.csv` at warehouse/material grain.
- Preserve manifest row-count/checksum/idempotency behavior.
- Join the forecast to `inventory_status` with validated one-to-one keys.
- Keep current `recommended_order_quantity`; add separately named forecast
  coverage and suggested-order evidence based on the upper planning range.
- Fail the pipeline on duplicate keys, unit mismatch, stale as-of date, or
  non-finite values.

## Architecture

Warehouse facts → Phase 1 forecaster → Gold forecast → validated join →
inventory status/alerts/insights. Existing transactional inventory remains the
source of truth; Gold is read-only.

## Related Code Files

- Modify: `src/agriinsight/metrics_inventory.py`
- Modify: `src/agriinsight/metrics.py`
- Modify: `src/agriinsight/insights.py` only if a forecast insight is accepted
- Modify: `tests/test_pipeline.py`
- Create: focused Gold integration tests if pipeline tests become oversized
- Modify: `docs/data-contracts.md`

## Implementation Steps

1. Add failing warehouse integration tests for Gold grain/schema/checksum,
   deterministic rerun, as-of cutoff, join cardinality, and unit agreement.
2. Query bounded OUT movements and call the Phase 1 pure forecaster.
3. Add the forecast Gold table and validated forecast fields to inventory
   status while retaining backward-compatible current-policy fields.
4. Add explicit `forecast_coverage_status` and suggested-order evidence; do not
   create an operational purchase order or replace immutable ledger facts.
5. Update data contracts and run focused plus full Python hosted gates.

## Success Criteria

- [ ] Gold manifest includes forecast row count and checksum.
- [ ] Same input/as-of date produces byte-stable forecast CSV.
- [ ] Inventory status cannot join a forecast across warehouse, material, or
  base unit.
- [ ] Missing/insufficient forecasts degrade to explicit status.
- [ ] Existing pipeline acceptance remains green.

## Risk Assessment

- Contract drift: append fields and version the new dataset; generated/API
  contract checks gate adoption.
- Unsafe ordering recommendation: expose evidence separately from the current
  business rule until product acceptance.

## Security Considerations

Gold output inherits authorized artifact boundaries and contains no supplier,
identity, token, or free-text payload.
