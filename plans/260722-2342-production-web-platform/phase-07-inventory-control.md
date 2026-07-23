---
phase: 7
title: "inventory-control"
status: pending
priority: P1
effort: "4d"
dependencies: [2, 3, 4]
---

# Phase 7: inventory-control

## Overview

Deliver warehouse-scoped inventory control by separating Spring operational balances/lots/transactions from read-only FastAPI analytics rooted under `/internal/v1/inventory`. Operational mutations use the existing inventory transaction and reversal contracts only; no transfer route or browser-side ABC math is introduced.

## Context

- Verified Spring read contracts are rooted at `/api/v1/inventory/balances`, `/lots`, and `/transactions` in `backend/src/main/java/com/agriinsight/backend/inventory/api/InventoryBalanceReadController.java:18`, `InventoryLotReadController.java:19`, and `InventoryTransactionReadController.java:21`.
- Verified Spring mutation contracts are `POST /api/v1/inventory/transactions` and `POST /api/v1/inventory/transactions/{id}/reversals` in `backend/src/main/java/com/agriinsight/backend/inventory/api/InventoryTransactionMutationController.java:32` and `:83`.
- Phase 2 owns the internal inventory analytics router rooted under `/internal/v1/inventory`.
- Phase 3 owns auth/session-safe BFF proxying.
- Phase 4 owns shell and shared layout primitives.
- This phase owns only inventory route trees, inventory-specific BFF wrappers, and inventory-domain tests.

## Requirements

- Functional:
  - Render warehouse-scoped stock balances, lot views, and movement history from Spring operational data.
  - Support mutation paths for receipt and issue transactions through `POST /api/v1/inventory/transactions`.
  - Support append-only compensating reversals through `POST /api/v1/inventory/transactions/{id}/reversals`.
  - Render read-only analytic panels for ABC, alerts, and trends from `/internal/v1/inventory`.
  - Preserve server contract order for lots and transactions; do not invent a `sort=fefo` query parameter or browser re-sort.
  - Keep the selected warehouse explicit in URL and in every server request, but only if it is server-visible to the current user.
  - Allow existing supplier master references for authorized admins/managers where the operational flow already needs them; do not give Supplier-role users this screen.
- Non-functional:
  - Never write to analytics datasets from this phase.
  - Never introduce transfer mutations, supplier CRUD, or supplier-master expansion in this phase.
  - Cross-warehouse visibility must respect server-enforced warehouse scope.
  - Browser cannot recalculate ABC classes, FEFO priority, or alert thresholds.

## Data Flow

1. Browser requests `/inventory` with explicit `warehouseId` and optional SKU/date filters.
2. BFF confirms the selected warehouse is in the server-visible warehouse set for the current user before making domain calls.
3. BFF requests Spring balances, lots, and transactions for that warehouse scope and preserves returned order.
4. BFF requests read-only analytics from `/internal/v1/inventory` using the same server-visible warehouse scope.
5. BFF normalizes operational and analytic sections without mixing writeable and read-only records.
6. User triggers receipt or issue mutations; BFF forwards `POST /api/v1/inventory/transactions` with `Idempotency-Key`.
7. User triggers a reversal; BFF forwards `POST /api/v1/inventory/transactions/{id}/reversals` with `Idempotency-Key` and `If-Match`.
8. UI refreshes operational state after mutation and keeps analytics panels labeled as read-only.

## File Matrix

These are the fixed Phase 7 ownership targets under the Phase 3 `web/` layout.

| Action | Path | Purpose |
| --- | --- | --- |
| CREATE | `web/src/app/(platform)/inventory/page.tsx` | inventory route entry |
| CREATE | `web/src/app/(platform)/inventory/loading.tsx` | route loading state |
| CREATE | `web/src/features/inventory/load-inventory-view-model.ts` | Spring + analytics composition |
| CREATE | `web/src/features/inventory/inventory-filter-schema.ts` | explicit server-visible warehouse parsing |
| CREATE | `web/src/features/inventory/post-inventory-transaction.ts` | receipt/issue wrapper |
| CREATE | `web/src/features/inventory/post-inventory-reversal.ts` | reversal wrapper |
| CREATE | `web/src/features/inventory/components/*.tsx` | balances, lots, transactions, analytics panels |
| CREATE | `web/tests/contracts/inventory-control.contract.test.ts` | warehouse visibility and header contract tests |
| CREATE | `web/tests/e2e/inventory-control.spec.ts` | warehouse user journey |

## Interfaces And Contracts

- Verified Spring operational reads consumed via BFF:
  - `GET /api/v1/inventory/balances`
  - `GET /api/v1/inventory/lots`
  - `GET /api/v1/inventory/transactions`
  - `GET /api/v1/inventory/transactions/{id}`
- Verified Spring mutation commands consumed via BFF:
  - `POST /api/v1/inventory/transactions` with transaction kinds limited here to `RECEIPT` and `ISSUE`
  - `POST /api/v1/inventory/transactions/{id}/reversals` with `Idempotency-Key` and `If-Match`
- Phase 2 analytics reads consumed via BFF:
  - `/internal/v1/inventory` family for ABC, alerts, and trends
- Separation rules:
  - operational cards/tables mutate and refresh from Spring only.
  - analytic cards/tables never expose edit controls and never recompute ABC in the browser.
  - preserve server order for lots and transactions; client formatting must not change ranking semantics.
  - existing supplier master may be referenced only where already authorized; Supplier-role access stays denied.

## TDD Track

### RED

- Add contract tests for mandatory server-visible `warehouseId`, denied foreign warehouse selection, and preserved server order.
- Add loader tests proving Spring operational sections still render when analytics is unavailable.
- Add mutation tests for `Idempotency-Key` forwarding on transaction posts and `Idempotency-Key` plus `If-Match` on reversals.
- Add E2E for warehouse selection, receipt/issue happy path, reversal happy path, and denied Supplier-role access.

### GREEN

- Implement inventory filter schema and route loaders with strict server-visible warehouse gating.
- Implement operational panels for balances, lots, and transactions from Spring.
- Implement transaction and reversal wrappers only for the verified Spring command endpoints.
- Implement read-only analytics panels from `/internal/v1/inventory` with visible snapshot labeling.
- Leave shared navigation registration to a serialized controller-only integration step.

### REFACTOR

- Extract server-visible warehouse helpers reused across operational and analytic loaders.
- Consolidate lot and transaction row formatting once operational and analytic panels are stable.
- Keep abstractions inventory-local; do not create cross-domain stock framework in this phase.

## Implementation Steps

1. Freeze the inventory URL filter contract with required `warehouseId` and optional SKU/date inputs.
2. Write contract tests proving the chosen warehouse must be server-visible and that client code preserves server order.
3. Implement Spring-backed operational loaders for balances, lots, and transactions.
4. Implement analytics loaders from `/internal/v1/inventory` with explicit read-only labeling.
5. Implement inventory mutation wrappers for `POST /api/v1/inventory/transactions` and reversal wrappers for `POST /api/v1/inventory/transactions/{id}/reversals`.
6. Build the inventory page with clear visual separation between operational and analytic sections.
7. Add post-mutation refresh and degraded analytics behavior without mixing stale and live numbers.
8. Finish with warehouse-scope E2E, Supplier-role denial checks, and auth/regression coverage.

## Validation

- Focused:
  - `.\backend\mvnw.cmd -f .\backend\pom.xml -Dtest=InventoryReadHttpContractTest,InventoryTransactionMutationHttpContractTest test`
  - `npm --prefix web run test -- inventory-control`
  - `npm --prefix web exec playwright test --grep "@inventory"`
- Broad:
  - `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [ ] Inventory balances, lots, and movements load from Spring with explicit warehouse scope.
- [ ] The selected warehouse must be server-visible; arbitrary URL warehouse injection is rejected.
- [ ] Client code preserves server-provided lot and transaction order and does not invent `sort=fefo`.
- [ ] ABC, alerts, and trends render from `/internal/v1/inventory` as read-only analytics with clear snapshot labeling.
- [ ] Operational mutations are limited to transaction posts and reversals; no transfer route is introduced.
- [ ] Existing supplier master references remain available only to already-authorized admins/managers; Supplier-role access stays denied.
- [ ] Operational mutations target Spring only and refresh operational state without mutating analytics data.

## Risks And Rollback

- High: inventing client-side FEFO semantics can drift from server truth.
  - Mitigation: preserve server order and ban browser re-sorting in tests.
- High: arbitrary warehouse selection can leak broader inventory scope.
  - Mitigation: require warehouse selection from the server-visible list and reject foreign ids before data fetch.
- Medium: supplier access can sprawl beyond the existing authorized master-data surface.
  - Mitigation: keep supplier usage reference-only and deny Supplier-role access in route guards and E2E.
- Rollback:
  - Hide inventory route and BFF wrappers.
  - Revert mutation entrypoints first if operational side effects appear unsafe; keep read-only views disabled until fixed.

## Dependencies And Ownership

- Hard blockers: Phases 2, 3, and 4 complete.
- Parallel safety:
  - Do not edit overview, work, cost, crop-health, or admin route trees.
  - Do not change shared analytics service internals from Phase 2; consume only established contracts.
  - Do not edit shared shell/navigation registration files in this phase.
- Owned artifacts:
  - inventory route tree and inventory-local loaders
  - inventory transaction and reversal wrappers
  - inventory tests and warehouse-scope validation

## Commit Plan

1. `feat(web): add warehouse-scoped inventory loaders and filters`
2. `feat(web): render inventory control and read-only analytics panels`
3. `feat(web): wire inventory transaction and reversal actions`
4. `test(web): cover warehouse scope order preservation and denied supplier flows`
