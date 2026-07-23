---
phase: 6
title: "work-operations"
status: pending
priority: P1
effort: "4d"
dependencies: [1, 3, 4]
---

# Phase 6: work-operations

## Overview

Deliver mobile-first work assignment, append-log, correction, and history flows over the frozen Phase 1 work contracts and generated client. Phase 6 does not add backend routes; it consumes the existing `/api/v1/activities` family and keeps log corrections append-only.

## Context

- Verified work family is rooted at `/api/v1/activities` in `backend/src/main/java/com/agriinsight/backend/operations/api/ActivityReadController.java:21`, `ActivityLogController.java:31`, and `ActivityAssignmentController.java:32`.
- Verified log mutations are `POST /api/v1/activities/{id}/logs` and `POST /api/v1/activities/{id}/logs/{logId}/corrections` in `backend/src/test/java/com/agriinsight/backend/operations/ActivityLogHttpContractTest.java:50` and `:82`.
- Phase 1 freezes the generated client signatures for assignment/log/history GETs that this phase consumes.
- Phase 3 already owns session-safe BFF proxying and upstream error normalization.
- Phase 4 already owns shell/nav and shared mobile layout patterns.
- This phase owns work route trees, generated-client adapters, work-specific tests, and mobile workflows.

## Requirements

- Functional:
  - Show mobile-first assignment list for current user/team using the frozen generated client GETs.
  - Show append flow for new work logs with idempotent retry behavior.
  - Show correction flow for existing logs using append-only correction commands, not in-place updates.
  - Show immutable history timeline for work-log changes and approvals.
  - Consume Phase 1 frozen GET assignment/log/history contracts instead of adding new Spring reads here.
- Non-functional:
  - No fake offline sync, local queue, or background replay service.
  - Double-submit must not create duplicate work logs.
  - `Idempotency-Key` is required on append and correction commands.
  - Do not fabricate `If-Match` on append-only log correction flows; reserve it only for actual update/revoke routes that already exist outside this phase.
  - Generated-client drift must fail tests before UI wiring lands.

## Data Flow

1. Mobile browser loads `/work` and requests assignments, logs, and history through the Phase 1 generated client adapter.
2. BFF or server loaders call the frozen generated client GETs and return normalized assignment/log/history models.
3. Operator appends a work log; browser sends request with `Idempotency-Key`.
4. BFF forwards `POST /api/v1/activities/{id}/logs` and returns canonical saved log lineage.
5. Operator corrects a prior log; browser sends `Idempotency-Key` to `POST /api/v1/activities/{id}/logs/{logId}/corrections`.
6. Spring accepts or rejects the correction; BFF maps denial or validation failure to explicit recoverable UI state.
7. History timeline reads only server-recorded corrections; no client-synthesized audit trail.

## File Matrix

These are the fixed Phase 6 ownership targets under the Phase 3 `web/` layout.

| Action | Path | Purpose |
| --- | --- | --- |
| CREATE | `web/src/app/(platform)/work/page.tsx` | work route entry |
| CREATE | `web/src/features/work/load-work-view-model.ts` | assignment/log/history loader |
| CREATE | `web/src/features/work/work-generated-client-adapter.ts` | wrapper over Phase 1 generated client |
| CREATE | `web/src/features/work/submit-work-log.ts` | append command wrapper |
| CREATE | `web/src/features/work/correct-work-log.ts` | correction command wrapper |
| CREATE | `web/src/features/work/components/*.tsx` | mobile-first cards/forms/timeline |
| CREATE | `web/tests/contracts/work-operations.contract.test.ts` | generated-client and header contract tests |
| CREATE | `web/tests/e2e/work-operations-mobile.spec.ts` | narrow-viewport flow |

## Interfaces And Contracts

- Phase 1 generated client contracts consumed here:
  - assignment GETs
  - activity-log GETs
  - activity-log history GETs
- Verified Spring command contracts consumed through that client family:
  - `POST /api/v1/activities/{id}/logs` requires `Idempotency-Key`
  - `POST /api/v1/activities/{id}/logs/{logId}/corrections` requires `Idempotency-Key`
- Auth expectations:
  - anonymous -> `401`
  - authenticated without scope -> `403`
  - scoped operator/supervisor -> `200/201`
- UI contract:
  - assignment cards, append form, correction form, and history timeline all bind to server-recorded lineage only.
  - no fabricated `PATCH /api/work/logs` route exists in this phase.
  - no optimistic completion that hides server rejection.

## TDD Track

### RED

- Write web contract tests for generated-client adapter signatures, idempotent append retry, append-only correction behavior, and history rendering.
- Write tests proving correction requests never attach a fabricated `If-Match` header.
- Write mobile E2E for append success, duplicate-submit retry, correction append success, and denied-scope behavior.

### GREEN

- Implement generated-client adapters and route loaders that consume the frozen Phase 1 GETs.
- Implement command wrappers that forward `Idempotency-Key` unchanged on append and correction.
- Implement mobile-first work page with assignment cards, append sheet, correction sheet, and history timeline.
- Implement explicit network failure and denial states without fake local queueing.

### REFACTOR

- Extract shared generated-client and header-forwarding helpers if they remain work-domain-specific.
- Collapse duplicated date/filter parsing after mobile flow stabilizes.
- Keep any shared form primitives under work feature scope; do not reopen global form systems in this phase.

## Implementation Steps

1. Freeze the Phase 1 generated client signatures for assignment/log/history GETs before any UI wiring.
2. Write adapter tests proving this phase consumes existing GETs and does not add backend reads.
3. Implement work loaders that normalize generated-client payloads into one route model for `/work`.
4. Implement append command wrapper for `POST /api/v1/activities/{id}/logs` with required `Idempotency-Key` propagation.
5. Implement correction command wrapper for `POST /api/v1/activities/{id}/logs/{logId}/corrections` with required `Idempotency-Key` propagation.
6. Build mobile-first work page with assignment cards, append flow, correction flow, and immutable history timeline.
7. Add denied-scope, validation-failure, and duplicate-submit recovery UI.
8. Finish with mobile E2E, viewport regression, and generated-client drift checks.

## Validation

- Focused:
  - `.\backend\mvnw.cmd -f .\backend\pom.xml -Dtest=ActivityReadHttpContractTest,ActivityLogHttpContractTest test`
  - `npm --prefix web run test -- work-operations`
  - `npm --prefix web exec playwright test --project="Mobile Chrome" --grep "@work"`
- Broad:
  - `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [ ] Phase 6 consumes the frozen Phase 1 generated client for assignment/log/history GETs and adds no backend routes.
- [ ] `/work` works on narrow mobile viewports without requiring fake offline sync.
- [ ] Append uses `Idempotency-Key`; retry cannot create duplicate logs.
- [ ] Correction uses append-only `POST /api/v1/activities/{id}/logs/{logId}/corrections` with `Idempotency-Key`, not a fabricated patch route.
- [ ] `If-Match` is not attached to log append/correction flows unless a real update/revoke route is explicitly consumed outside this phase.
- [ ] History timeline is server-backed and immutable from the client perspective.
- [ ] No speculative query layer or backend additions are introduced in this phase.

## Risks And Rollback

- High: append retries can duplicate server writes if idempotency is not forwarded end-to-end.
  - Mitigation: contract tests around header propagation and duplicate-submit behavior.
- High: developers may accidentally implement an in-place log patch path that does not exist.
  - Mitigation: adapter tests and explicit ban on fabricated `PATCH /api/work/logs`.
- Medium: generated-client drift can break the page after Phase 1 without obvious compile failures.
  - Mitigation: explicit adapter contract tests and focused backend HTTP contract runs.
- Rollback:
  - Disable `/work` navigation and BFF handlers.
  - Revert only the phase-local web adapters and workflows; backend command paths remain unchanged.

## Dependencies And Ownership

- Hard blockers: Phase 1 generated client freeze plus Phases 3 and 4 complete.
- Parallel safety:
  - Do not edit overview, inventory, cost, crop-health, or admin route trees.
  - Do not add or change Spring controllers/services in this phase.
- Owned artifacts:
  - web work route tree and generated-client adapters
  - work E2E and contract coverage

## Commit Plan

1. `feat(web): add work generated-client adapters and loaders`
2. `feat(web): add mobile-first work operations flows`
3. `test(web): cover idempotency and correction lineage journeys`
