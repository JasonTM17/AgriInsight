---
phase: 4
title: "Farm season workforce and activity APIs"
status: in-progress
priority: P1
effort: "2-3d"
dependencies: [3]
---

# Phase 4: Farm season workforce and activity APIs

## Overview

Add the operational farm hierarchy and field-work records: farms, fields, crops, seasons, employees, activities, assignments, and harvests. Every command uses the phase-3 scope/transaction boundary and has no analytics-file side effects.

Current progress (2026-07-22): the operations schema through V10, FORCE RLS, permission-aware farm core, versioned farm/field/crop/season masters, Employee lifecycle/redacted picker, and tenant-admin farm-assignment grant/revoke are implemented and verified. Assignment locking, idempotent replay, parent visibility-before-claim, tenant-wide Field writes, schema-aligned validation, and parent/child/profile/employee lifecycle serialization are covered by focused tests. This phase remains in progress until activity, activity-assignment, log, harvest, and worker-scope boundaries meet every success criterion below.

Gate evidence: [Phase 4 master-data production review](./reports/reviewer-2026-07-22-phase4-master-data.md) and [workforce/assignment production review](./reports/reviewer-2026-07-22-phase4-workforce-assignments.md).

## Requirements

- Manage farm/field/crop/season masters; create/update activities and harvest records; assign employees; query progress with bounded pagination and filters.
- Executives and permitted analysts read the tenant; farm managers read/write assigned farms; field workers see assigned tasks and append/correct only their own task logs; other roles are denied.
- Enforce canonical per-tenant codes, parent/child tenant integrity, date/status invariants, optimistic locking, and correction/reversal for operational facts.

## Architecture

```text
tenant -> farm -> field -> season -> activity -> activity_assignee
                                      -> immutable activity_log
                          -> harvest
tenant -> crop
tenant -> employee <- user_profile (optional identity link)
```

Seasons belong to fields for this MVP and carry farm/tenant columns for indexed authorization and composite parent checks. Managers create/assign activity tasks; assigned workers append immutable activity logs instead of creating arbitrary tasks. A field's responsible employee is separate. Costs are not duplicated here; phase 6 owns the operating-cost ledger.

## Related Code Files

Implemented and verified in the current master-data slice:

- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FarmReadController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FarmMutationController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FarmLifecycleController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FarmRoutes.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FarmService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FarmCommandService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\infrastructure\PostgresFarmStore.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\infrastructure\PostgresFarmLifecycleStore.java`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V5__create_farm_and_operations_tables.sql`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V6__add_farm_and_operations_rls_policies.sql`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V7__serialize_farm_lifecycle_dependencies.sql`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V8__serialize_field_crop_and_season_lifecycle.sql`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V9__serialize_employee_lifecycle_dependencies.sql`
- `D:\AgriInsight\backend\src\main\resources\db\migration\V10__serialize_farm_assignment_profile_lifecycle.sql`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\EmployeeService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\api\EmployeeRoutes.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FarmAssignmentService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FarmAssignmentController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\domain\UserProfile.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FieldCreateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FieldReadController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FieldUpdateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\FieldLifecycleController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\CropCreateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\CropReadController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\CropUpdateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\CropLifecycleController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\SeasonCreateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\SeasonReadController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\SeasonUpdateController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\api\SeasonTransitionController.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\FieldService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\CropService.java`
- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\farm\application\SeasonService.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\FieldCropLifecycleConcurrencyIntegrationTest.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\farm\infrastructure\FarmScopedWriteAuthorizationIntegrationTest.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\farm\FarmReadHttpContractTest.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\farm\FarmMutationHttpContractTest.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\farm\FarmLifecycleHttpContractTest.java`
- `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\FarmLifecycleConcurrencyIntegrationTest.java`

Remaining planned Phase 4 boundaries:

- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\api\ActivityController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\api\ActivityLogController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\api\HarvestController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\ActivityService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\ActivityLogService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\application\HarvestService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\domain\ActivityAssignment.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\domain\ActivityStatus.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\operations\domain\ActivityLog.java`
- Create focused field/crop/season HTTP contract tests under `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\farm\`.
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\operations\ActivityAuthorizationTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\operations\OperationDatabaseTests.java`

## Implementation Steps (TDD: red -> green -> refactor)

1. **Red — API contract:** define records and MockMvc tests for CRUD/command routes, pagination caps, unknown fields, canonical codes, and ProblemDetail errors. Encode the role/scope matrix before service code.
2. **Red — relational invariants:** test duplicate per-tenant codes, child/parent tenant mismatch, field area/date constraints, season ordering, status transitions, and activity/harvest foreign keys on PostgreSQL.
3. **Green — migration:** create `farms`, `crops`, `fields`, `seasons`, `employees`, `user_farm_assignments`, `activities`, `activity_assignees`, `activity_logs`, `harvests`, and controlled `activity_types`. `user_farm_assignments` is created here—not phase 3—so it has real composite tenant/user/farm FKs. Seasons include an optional nonnegative `budget_vnd` comparison input. Use UUIDs, tenant columns, `NUMERIC`, `TIMESTAMPTZ`, `DATE`, audit/version fields, tenant-led indexes, composite uniqueness/FKs, and RLS policies.
4. **Green — masters and farm grants:** implement scoped services/controllers plus explicit farm grant/revoke commands restricted to tenant admins. Normalize codes at the boundary, use version/If-Match semantics, return 409 on optimistic-lock conflict, deactivate only masters whose restore semantics are defined, reserve codes while inactive, and never physically delete referenced facts.
5. **Green — activities and field logs:** implement planned/started/completed/cancelled task transitions, optimistic `PATCH` of nonterminal task metadata, and audited assignment grant/revoke without deleting assignment history. Managers/admins create tasks and control assignments. An assigned worker may append an immutable activity log with occurred time, notes, quantity/unit and optional evidence URI metadata; the worker cannot reassign or create an arbitrary task. Evidence metadata is length/scheme allowlisted and is never fetched by the backend. Log corrections reference a prior log rather than overwriting it; terminal activity facts are corrected through append-only log/harvest corrections, not task history rewrite. Every mutation uses phase 3's tenant/principal/route-bound command record.
6. **Green — harvests:** implement append/correction records with quantity, waste, grade, revenue `NUMERIC`, unit normalization, and season/field/crop relationships. Tenant admins and assigned farm managers may post; executives/analysts are read-only; field workers do not post harvest facts in this MVP. Corrections reference the original record.
7. **Green — authorization queries:** register exact farm/operations route + permission entries and farm/activity implementations of phase 3's authorization-owned scope extension port, keeping the authorization module independent of farm internals. Use projections/entity graphs. Farm managers join farm assignments; workers join activity assignments and employee link; executives/analysts use tenant scope plus permission. Hidden resources return safe 404.
8. **Green — RLS:** add policies for every new table and direct SQL tests as the restricted runtime role. UUID guessing must not bypass application filtering.
9. **Refactor:** split controllers/services/mappers below ~200 LOC, verify Modulith dependencies, and generate OpenAPI examples from tests.

## API and data contracts

- Routes and methods are the exact entries in `authorization-matrix.md`, including activity `PATCH`, assignment grant/revoke, task transition, log correction, and harvest correction; schemas are recorded in OpenAPI before implementation.
- List responses have bounded page/size, stable server-owned sort, and allowlisted filters only.
- Codes remain explicit integration keys; Java UUIDs are canonical API IDs. Python `*_key` values are never integration IDs.
- Farm/field/season/activity parent links become immutable once referenced by a posted operational/cost fact; moving a record between parents requires a new record plus deactivation/correction, never an in-place reparent.
- Persist UTC `Instant`, business `LocalDate`, and nonnegative `BigDecimal` quantities with declared units.
- Activity/harvest cost fields are absent until phase 6, preventing duplicate totals and preserving Gold separation.
- Evidence is bounded, allowlisted URI metadata only; upload/storage/fetching is later scope and cannot trigger SSRF or create a writable artifact path.
- A profile-to-employee link is unique and tenant-safe. Employee responses expose only fields required for assignment; sensitive HR/payroll data is not part of this milestone.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -Dtest='*Farm*Test,*Season*Test,*Activity*Test,*Operation*Test' test`
- Testcontainers PostgreSQL + Flyway/RLS tests; MockMvc role/scope matrix; query-count assertion.
- `git diff --check` and full Python suite before handoff.

## Success Criteria

- [ ] Farm/field/crop/season/employee/activity/log/harvest APIs are versioned and documented; a worker can only append logs to an assigned task.
- [ ] Manager is limited to assigned farms, worker to assigned activities, executive/analyst to permitted tenant data, supplier to no finance.
- [ ] Parent/child tenant and date/status invariants hold at API and DB layers.
- [ ] Farm/activity assignments have real tenant-safe FKs; grant/revoke is admin-only and immediately changes scoped queries.
- [ ] Optimistic locking and correction/reversal prevent silent fact loss.
- [ ] Optional season budget is nonnegative and available to phase-6 variance queries without becoming an operating-cost fact.
- [ ] Pagination/query plans are bounded and no N+1 appears in focused tests.
- [ ] Every new table has tenant RLS, indexes, and migration tests.
- [ ] Backend tests never write existing analytics artifacts.

### Verified in the current slice

- [x] Farm/field/crop/season master routes use versioned HTTP contracts, idempotency, ETag/If-Match, bounded reads, and safe ProblemDetail errors.
- [x] Field/Crop/Season parent and tenant invariants are enforced by both application predicates and V5–V8 PostgreSQL constraints/triggers.
- [x] FARM-scoped writes lock active assignments through commit; tenant-wide administrator writes remain supported where the permission matrix allows them.
- [x] Current master-data, Employee, and farm-assignment unit/HTTP/migration/persistence/RLS/concurrency gates pass; activity/log/harvest and worker-scope criteria remain open.
- [x] Employee full-master and redacted-picker contracts, active-responsibility lifecycle guards, and V9 upgrade safety pass.
- [x] Tenant-admin farm grant/revoke is tenant-safe, append-preserved, idempotent, versioned, and serialized with profile/farm lifecycle through V10.

## Risk Assessment

- Activity/task semantics may broaden later: keep assignment/status vocabulary explicit; defer media/mobile sync.
- Composite tenant constraints prevent cross-tenant parent bypass; direct SQL tests are mandatory.
- Cost duplication is prevented by keeping cost fields out of this phase.
- Soft delete is restricted to mutable masters; facts use corrections.

## Rollback

Disable operation routes and use forward migrations to repair records; do not delete referenced facts or edit applied SQL. Revert only phase-owned Java files if the API contract changes.
