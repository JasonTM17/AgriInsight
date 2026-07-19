---
phase: 5
title: "Inventory and procurement APIs"
status: pending
priority: P1
effort: "3-4d"
dependencies: [3]
---

# Phase 5: Inventory and procurement APIs

## Overview

Implement warehouse, material, supplier, stock-balance, and immutable inventory-transaction APIs. The ledger and current-balance projection must remain a separate procurement/inventory lens; it must never be silently merged into operating cost or Gold v1.

## Requirements

- Functional: manage warehouse/material/supplier masters; record receipt/issue movements and linked reversals; query current balances, expiry, low-stock, and bounded movement history.
- Visibility: inventory managers operate only assigned warehouses; executives/data analysts receive permitted tenant-wide inventory reads; farm managers receive only explicitly granted inventory views; suppliers have no financial/tenant-wide access.
- Integrity: `RECEIPT` requires supplier, batch, and expiry; `ISSUE` cannot make a balance negative; posted movements are corrected only by service-generated linked reversals; quantity/unit and monetary precision are canonical; duplicate retries are idempotent; all records are tenant-scoped and RLS-protected.

## Architecture

```text
warehouse + material -> stock_balance (aggregate projection)
                      -> stock_lot (batch/expiry projection)
                      -> inventory_transaction (append-only ledger)
                             -> lot_allocation (ISSUE traceability)
supplier  ------------^ for RECEIPT only
```

`inventory_transactions` is the audit/source ledger. `stock_lots` preserves batch/expiry traceability, `inventory_transaction_lot_allocations` records which lots an `ISSUE` consumed, and `stock_balances` is the warehouse/material aggregate projection. Both projections update in the same database transaction under deterministic row locks; reconciliation compares them to signed ledger effects plus allocations. An `ISSUE` either names one valid lot or uses deterministic FEFO allocation across eligible lots. A reversal references one original movement and applies the inverse quantity/procurement effect subject to remaining-lot constraints. No Redis/cache is needed. Net procurement spend is receipts minus receipt reversals and remains distinct from operating cost and inventory value.

## Related Code Files

- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\WarehouseController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\MaterialController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\SupplierController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\InventoryTransactionController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\WarehouseAssignmentController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\api\InventoryRouteAuthorization.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\WarehouseService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\MaterialService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\SupplierService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\WarehouseAssignmentService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\InventoryTransactionService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\InventoryReversalService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\application\StockBalanceService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\InventoryTransaction.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\StockBalance.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\StockLot.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\InventoryLotAllocation.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\WarehouseAssignment.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\inventory\domain\CanonicalUnit.java`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V7__create_inventory_tables.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V8__add_inventory_rls_policies.sql`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\inventory\InventoryApiTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\inventory\InventoryConcurrencyTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\inventory\InventoryReconciliationTests.java`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — request/correction contract:** test canonical code normalization, allowed units (`kg`, `liter`, `piece`), positive quantities, nonnegative unit price, page caps, unknown-field rejection, `RECEIPT`/`ISSUE` field rules, valid reversal linkage, reversal-of-reversal/over-reversal rejection, and net procurement math. Test that supplier and finance permissions are not inferred from a request body.
2. **Red — balance/concurrency:** seed multiple lots for one warehouse/material and write concurrent `ISSUE` tests that prove aggregate and per-lot quantities cannot become negative. Test two concurrent first receipts for a previously absent balance/lot, explicit-lot and deterministic FEFO allocation, expired-lot rejection policy, duplicate command keys, concurrent reversal versus issue, same-version master updates, and a failed transaction that leaves ledger, allocations, lots, balance, and command record unchanged.
3. **Green — migration:** create `warehouses`, `materials`, `suppliers`, `user_warehouse_assignments`, `inventory_transactions`, `inventory_transaction_lot_allocations`, `stock_lots`, and `stock_balances`. Transactions include kind (`RECEIPT`, `ISSUE`, `REVERSAL`), `reversal_of`, and signed quantity/procurement effects generated by service policy. Database constraints prevent invalid/self/cross-tenant links and invalid kind/field combinations; locked service logic plus concurrency tests prevent cyclic/excess cumulative reversal. The assignment table is created here so it has real composite tenant/user/warehouse FKs. Use phase 3's central command-record table rather than a second idempotency store. Use UUIDs, tenant + parent columns, `NUMERIC` quantities/amounts, canonical-unit checks, UTC transaction date/time, audit/version fields, foreign-key indexes, and unique per-tenant business codes.
4. **Green — masters and warehouse grants:** implement scoped warehouse/material/supplier CRUD plus explicit warehouse grant/revoke commands restricted to tenant admins. Deactivate only unused masters, keep their codes reserved, and never physically delete a referenced supplier/material/warehouse.
5. **Green — transaction command:** in one tenant-scoped transaction, resolve and scope the warehouse/material, reserve phase 3's command record, establish a missing aggregate/lot with `INSERT ... ON CONFLICT DO NOTHING` (or an equivalent unique-key create-or-lock protocol), then select and lock the persisted aggregate/affected lot rows in deterministic `(expiry_date, received_at, id)` order. Validate the command, insert the immutable ledger row and issue-allocation rows, update lot/aggregate projections, and commit the replay result. Reject negative quantities, expired/unknown lots according to the documented policy, and tenant/parent mismatches. Never assume `SELECT ... FOR UPDATE` locks a row that does not exist.
6. **Green — reversals:** expose a reversal command that accepts only the original transaction UUID, bounded quantity when partial reversal is allowed, reason, expected version, and command key. The service derives direction, unit price, supplier/batch, signed quantity, and signed procurement amount from the original; clients cannot submit inverse finance fields. Receipt reversal is limited to quantity still present in the original lot; issue reversal restores the original allocation lots. Lock original, prior reversals, aggregate, and lots deterministically; total reversed quantity cannot exceed original.
7. **Green — reads:** expose bounded balance and movement endpoints with stable sort, optional date/material/warehouse filters, and aggregate projections. Add expiry/low-stock response flags without inventing a reorder forecast; the existing 30-day analytics forecast remains Python-owned.
8. **Green — RLS and authorization:** register exact inventory route + permission entries and the warehouse implementation of phase 3's authorization-owned scope extension port, keeping authorization independent of inventory internals. Add policies for all inventory tables; inventory-manager queries must join warehouse assignments before fetching rows. Supplier role has no transaction/finance permission. Direct UUID guessing tests must return safe 404/403.
9. **Green — reconciliation:** add a service/test-only query that recomputes each lot and aggregate balance from signed immutable ledger/allocation effects and fails on drift. Do not silently repair drift in a request path.
10. **Refactor:** keep command validation, locking, persistence, projections, and controllers separate; add OpenAPI examples and query-count checks.

## API and data contracts

- Routes and methods are the exact entries in `authorization-matrix.md`, covering `/api/v1/warehouses`, `/api/v1/materials`, `/api/v1/suppliers`, `/api/v1/warehouse-assignments`, `/api/v1/inventory/balances`, `/api/v1/inventory/lots`, `/api/v1/inventory/transactions`, and `/api/v1/inventory/transactions/{id}/reversals`.
- `RECEIPT`: supplier, batch code, expiry date, base quantity, unit cost, and command key required. `ISSUE`: reason/reference and command key required; it either identifies one batch/lot or requests FEFO allocation across eligible lots, and quantity cannot exceed the locked eligible total. Supplier is never accepted from the issue body. API compatibility names may display `IN`/`OUT`, but persistence and OpenAPI define the unambiguous kinds.
- The `unit` submitted by a caller is normalized to the material's base unit. If a source tonne is accepted in a later import path, convert quantity and unit price together as the existing Python contract requires.
- Money uses VND `BigDecimal`/`NUMERIC(18,2)` (or a documented scale); quantity uses a scale suitable for kg/liter/piece and is rounded only at an explicit boundary.
- Net procurement spend is `SUM(procurement_effect_vnd)` over receipts and their service-generated reversals; issues have zero procurement effect. Inventory value is a separate balance valuation; neither is included in the operating-cost ledger or a combined “total cost.”
- Movement responses expose public UUIDs, business codes, unit, quantity, amount, dates, and audit metadata—not internal SQL or another tenant's rows.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -Dtest='*Inventory*Test' test`
- Testcontainers PostgreSQL for row locks, RLS, Flyway, and ledger/balance reconciliation.
- Explain representative balance/movement queries and assert indexes begin with tenant/warehouse/material where appropriate.
- Rerun Python suite and `git diff --check`.

## Success Criteria

- [ ] Valid receipt/issue/reversal commands atomically update ledger, allocations, lots, and aggregate balance; invalid commands leave no partial state.
- [ ] Concurrent outbound commands cannot create a negative lot/aggregate balance or lost update; explicit-lot and FEFO behavior are deterministic.
- [ ] Concurrent first receipts safely create/lock one aggregate and the intended lot rows; no absent-row locking assumption remains.
- [ ] Receipt/issue reversals are linked, bounded, immutable, allocation-aware, and produce correct net procurement without client-supplied inverse finance fields.
- [ ] Duplicate command key creates one domain result and only the contract-declared eventual event candidate set.
- [ ] Inventory manager scope is enforced at application and RLS layers; supplier cannot view finance.
- [ ] Warehouse assignments have real tenant-safe FKs; grant/revoke is admin-only and immediately changes scoped queries.
- [ ] Unit/amount precision and tonne/kg conversion rules are explicit and tested.
- [ ] Reconciliation detects drift without silently mutating source records.
- [ ] Existing Gold inventory/procurement contracts remain unchanged until a versioned ETL phase consumes backend data.

## Risk Assessment

- Projection drift: keep ledger immutable, reconcile in tests/operations, and correct source facts only through linked reversals; any projection rebuild is a separate audited maintenance action.
- Lock contention: lock one balance row in a deterministic order and cap batch size; do not add Redis prematurely.
- Supplier data exposure: use explicit permission allowlists and deny finance routes even if a supplier UUID is valid.
- Unit/rounding drift: use BigDecimal and test boundary values; document any scale change before Python integration.

## Rollback

Disable transaction-write routes and continue the read-only Python inventory dashboard. Repair schema through forward migrations and posted facts through the linked reversal command; never delete ledger rows, hand-edit projections, or disable RLS.
