# Inventory page override

Status: design contract only. Existing Gold inventory is read-only; receipts/issues/reversals depend on backend phase 5.

## User and decision

- Primary role: inventory manager. Supplier sees only explicitly assigned supplier-safe views.
- Primary job: prevent stockout and expiry while preserving warehouse, lot, and supplier scope.
- Primary action: open a critical SKU-location alert, inspect FEFO lots/movements, then prepare the permitted replenishment or movement action.
- Cost invariant: procurement spend and inventory value are never presented as operating cost.

## Data contract

| Surface | Current/future source | Required context |
|---|---|---|
| Health summary | `inventory_summary.csv`, `inventory_alerts.csv` | warehouse scope, cutoff, severity counts |
| SKU/lot status | `inventory_status.csv`, `inventory_abc.csv` | material code/unit, lot/expiry, quantity, ABC, days of supply |
| Movement trend | `inventory_movements_monthly.csv` | movement type, unit, period |
| Receipts/issues/reversals | Phase 5 operational API | idempotency, optimistic version, actor/audit, reason |

## Structure and interaction

1. Warehouse scope + alert summary for stockout, low stock, overstock, expiry, and critical count.
2. Priority queue grouped by required action, not color; each row keeps material code, unit, quantity, lot/expiry, supplier, and last movement.
3. FEFO lot drawer with expiry sequence, quality status, movement lineage, and permitted action.
4. ABC and days-of-supply comparison with text/table alternative.
5. Mutation flow shows before/after balance, unit, warehouse/lot, idempotency state, confirmation, and audit reason.

## State contract

| State | Required treatment |
|---|---|
| Loading | Stable priority rows and balance columns; no zero stock flash |
| Empty warehouse | Explain assignment/provisioning; do not imply zero inventory |
| No alerts | Show scope, cutoff, and monitored SKU coverage |
| Stale movement | Label last movement/cutoff and block unsafe mutation per policy |
| Partial lot data | Keep SKU balance visible; mark FEFO unavailable |
| Permission denied | Generic warehouse/material denial; no foreign stock count |
| Conflict/idempotency | Preserve request, show authoritative balance/result, never duplicate movement |
| Save failed | Retain entered reason/data, correlation ID, safe retry |

## URL, responsive, and accessibility

- URL owns warehouse, alert type, severity, material/category/ABC, expiry window, sort, and page.
- Mobile uses filter sheet + priority rows; lot/transaction detail opens a labeled sheet with back path and safe-area padding.
- Alert state is icon + text + severity. Quantity/unit remain together. Confirmation focuses the changed balance on success.

## Acceptance

- Critical/expiry/stockout/no-alert, empty/stale/partial/denied, conflict/idempotent replay/save-failure, desktop/mobile fixtures are approved.
