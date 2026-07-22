---
phase: 6
title: "Cost management and reporting boundary"
status: in-progress
priority: P1
effort: "1-2d"
dependencies: [4, 5]
---

# Phase 6: Cost management and reporting boundary

## Overview

Add a single operating-cost ledger and secured cost query surface while preserving the existing Python Cost Analysis contract. This phase deliberately does not generate CSV/PDF/XLSX or claim COGS/allocation that the current Gold v1 does not define.

## Requirements

- Functional: create/read/correct operating cost entries by category and optional farm/field/season/activity; expose bounded summaries and filters; retain season budget as a comparison input.
- Security: only explicit cost permissions can view or mutate financial records; farm managers see assigned farms; inventory managers and suppliers do not receive operating-finance access by default.
- Integrity: one source of truth for operating costs; correction/reversal rather than hard delete; VND precision and UTC timestamps; no summing of operating cost, procurement spend, and inventory value.

## Architecture

```text
activity/season/farm -> operating_cost_entry ledger -> cost query projections
inventory IN ---------> procurement lens (separate; no implicit join)
stock balance ---------> inventory value lens (separate)
```

`operating_cost_entries` is an append-oriented ledger. A posting accepts exactly one canonical target (`TENANT`, `FARM`, `FIELD`, `SEASON`, or `ACTIVITY`) and one target UUID; persistence uses a one-hot FK column matching that type rather than trusting multiple client-supplied hierarchy IDs. Queries derive ancestors through the canonical parent chain. Activity resources do not duplicate totals. The backend returns operational JSON only; the existing Python service remains the owner of controlled report formatting and Gold v1 exports.

## Related Code Files

- Modify: `D:\AgriInsight\backend\pom.xml` (only if query/projection dependency is required)
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\api\OperatingCostController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\api\CostQueryController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\api\CostRouteAuthorization.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\application\OperatingCostService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\application\CostQueryService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\domain\OperatingCostEntry.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\domain\CostCategory.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\cost\infrastructure\CostProjectionRepository.java`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V16__create_operating_cost_ledger.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V17__add_cost_rls_policies.sql`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\cost\OperatingCostApiTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\cost\CostAuthorizationTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\cost\CostInvariantTests.java`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — invariant tests:** test allowed categories, strictly positive submitted amount, VND scale/rounding, UTC dates, exactly one valid canonical target, cross-tenant target rejection, activity/season/field/farm hierarchy-derived summaries, duplicate command key, one-way reversal linkage, no reversal cycle/double reversal, and rejection of a request that tries to mark procurement as operating cost.
2. **Red — authorization:** register exact cost route + permission entries, then test executive/data-analyst read, tenant-admin mutation, assigned farm-manager read, unassigned manager denial, inventory-manager denial, supplier denial, hidden-resource 404 behavior, and endpoint-inventory completeness.
3. **Green — migration:** create `cost_categories` seed data and `operating_cost_entries` with UUID, tenant, target type, one-hot farm/field/season/activity FK columns, category, positive amount `NUMERIC`, entry kind (`POSTING`/`REVERSAL`), occurred_at, description/source reference, reversal_of, command reference, audit/version fields, and indexes for tenant + period/target. Add a check tying target type to exactly the allowed nullable FK shape, composite tenant FKs, RLS, and uniqueness/link constraints that prevent a second or cyclic reversal. Parent entities cannot be reparented after referenced facts exist.
4. **Green — commands:** implement create and correction/reversal services inside a tenant-scoped transaction using phase 3's command record. Callers submit one target type/id and a positive posting; the service resolves it under scope. A reversal is service-generated, copies the original target/category/dimensions, links the original once, and summaries apply its negative sign from `entry_kind`. A correction creates that exact reversal plus a separately validated new positive posting (which may intentionally use a corrected target) and emits one versioned correction event containing both public IDs. No destructive delete endpoint.
5. **Green — queries:** implement bounded list/detail and summary projections (by month/farm/season/category) with explicit filters, stable ordering, permission/scope joins, and query-count tests. Enforce a maximum date range/page size to prevent accidental full-table exports.
6. **Green — reporting boundary:** keep the secured, bounded JSON query DTOs as user-facing operational API responses only. Do not add a Python read adapter, integration read port, artifact writer, SQLite access, or Gold-frame producer. Phase 7's versioned transactional outbox is the sole machine-integration handoff selected by this milestone.
7. **Refactor:** keep ledger writes separate from read projections, centralize BigDecimal scale policy, document the three-lens cost invariant, and verify Modulith dependencies.

## API and data contracts

- Routes: `/api/v1/cost-entries`, `/api/v1/cost-entries/{id}`, `/api/v1/cost-summaries` (exact request/response schemas are committed in OpenAPI).
- Categories start with `LABOR`, `MATERIAL`, `MACHINERY`, `TRANSPORT`, `UTILITY`, `OTHER`; adding a category requires a migration and contract note.
- A manual `MATERIAL` operating-cost posting cannot reference or auto-copy an inventory transaction. Inventory consumption/allocation needs a future versioned ledger contract before it may feed operating cost.
- Operating cost is never automatically derived from inventory `IN` procurement, stock value, or an unallocated `OUT`. Existing docs explicitly state there is no outbound-to-activity allocation ledger.
- Cost clients never send a redundant farm/field/season/activity tuple. One target is accepted, ancestor groups are derived, and same-tenant-but-inconsistent hierarchy combinations are structurally unrepresentable.
- Summary responses identify the lens, period, tenant, source, and optional season budget variance; they do not pretend to be the nine Gold Cost frames until a versioned Python consumer is implemented.
- Export limits from the existing Python report contract are not duplicated or weakened; a later adapter must reuse/translate them explicitly.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -Dtest='*Cost*Test' test`
- Testcontainers PostgreSQL + Flyway/RLS + query-plan checks.
- Run the full Python cost/report tests and verify no new files appear under `artifacts/`.
- `git diff --check` and secret scan.

## Success Criteria

- [ ] Operating cost ledger has one source of truth, immutable posting, audited reversals, and idempotent creation.
- [ ] Cost routes enforce role and farm scope, with no inventory-manager/supplier finance leak.
- [ ] BigDecimal/NUMERIC, category, parent, and time invariants pass unit and database tests.
- [ ] One-hot canonical targets and derived hierarchy tests prevent same-tenant cross-farm/season/activity summary corruption; reversals copy the original target exactly.
- [ ] Summaries are bounded, indexed, and distinguish operating/procurement/inventory-value lenses.
- [ ] No duplicate integration read port/adapter exists; Java writes no Gold/manifest/SQLite and phase 7 outbox remains the sole machine handoff.
- [ ] Existing nine-frame Cost Analysis and controlled export tests remain green and unchanged.

## Risk Assessment

- Double counting: make lens names explicit in types/responses and reject cross-lens fields at validation.
- Allocation temptation: document that COGS is deferred; require a new data-contract version before adding it.
- Financial data exposure: permission checks plus RLS plus bounded projections; audit all reads/writes that are sensitive.
- Huge summaries: require date/page limits and inspect plans before enabling broad filters.

## Rollback

Disable cost write routes and keep existing Python reporting. Correct posted entries through reversal/forward migration; do not delete financial facts or alter the published Gold v1 contract.
