# Inventory page override

Status: source-complete read-only prototype available; browser approval pending. Receipts/issues/reversals depend on backend phase 5.

Current artifact: [`inventory-control-prototype.html`](../prototypes/inventory-control-prototype.html). The frozen fixture limits the inventory manager to assigned warehouse `WH-001`; it does not imply cross-warehouse access.

## User and decision

- Primary role: inventory manager. Supplier sees only explicitly assigned supplier-safe views.
- Primary job: prevent stockout and expiry while preserving warehouse, lot, and supplier scope.
- Primary action: open a critical SKU-location alert, inspect FEFO lots/movements, then prepare the permitted replenishment or movement action.
- Cost invariant: procurement spend and inventory value are never presented as operating cost.

## Data contract

| Surface | Current/future source | Required context |
|---|---|---|
| Health summary | `inventory_alerts.csv`, `inventory_status.csv` | authorized warehouse scope, cutoff, severity counts; enterprise summary excluded from this fixture |
| SKU status | `inventory_status.csv`, class cross-check from `inventory_abc.csv` | warehouse-filtered value, material code/unit, nearest expiry, quantity, ABC, days of supply |
| Batch/movement evidence | Silver `inventory_transactions.csv`, `suppliers.csv` | source receipt, matching expiry/batch, supplier, latest movement; no remaining lot balance |
| Movement trend | `inventory_movements_monthly.csv` | movement type, unit, period |
| Receipts/issues/reversals | Phase 5 operational API | idempotency, optimistic version, actor/audit, reason |

## Structure and interaction

1. Warehouse scope + alert summary for stockout, low stock, overstock, expiry, and critical count.
2. Priority queue grouped by required action, not color; each row keeps material code, unit, quantity, expiry context, ABC, and days of supply.
3. Current evidence sheet shows aggregate balance plus the source receipt and latest movement. It explicitly marks FEFO unavailable because no source contract provides remaining quantity or quality by lot.
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
- The current prototype implements only severity, alert type, material, and alert selection. It does not accept warehouse from the URL; assigned scope is fixed outside client-controlled state. Production adds the remaining URL keys only after their controls exist and server authorization resolves allowed warehouse scope.
- Mobile uses filter sheet + priority rows; lot/transaction detail opens a labeled sheet with back path and safe-area padding.
- Alert state is icon + text + severity. Quantity/unit remain together. Confirmation focuses the changed balance on success.

## Acceptance

- [x] WH-001 source fixture reconciles 10 alerts and 15 scoped SKU-location value rows; enterprise ABC is used only to cross-check classification, not to ship global totals.
- [x] Source checks cover fixture syntax, URL allowlists, filter/empty handling, semantic tables, ARIA references, focus-return routing, and absence of external runtime or browser storage.
- [ ] Browser checks cover desktop/mobile/landscape, keyboard, zoom/text scaling, reduced motion, offline reload, and focus return after responsive changes.
- [ ] Loading, empty warehouse, no-alert, stale, permission-denied, conflict/idempotent replay, and save-failure fixtures are approved before production React acceptance.
- [ ] Full FEFO and mutation fixtures wait for Phase 5 lot-balance, idempotency, optimistic-version, actor, and audit contracts.
