---
phase: 6
title: "work-operations"
status: completed
priority: P1
effort: "4d"
dependencies: [1, 3, 4]
---

# Phase 6: work-operations

## Completion — 2026-07-26

Accepted locally after two review cycles. Final gates: backend activity HTTP
contracts 5/5, 31 focused work web tests (including the 10-case negative
Host/CSRF/malformed-JSON/oversized-body route matrix), 127-pass broad suite,
lint/typecheck/contract-drift clean, Python demo suite 13/13, and the guarded
real-browser E2E runner `WEB_PLATFORM_E2E=PASS` with 6/6 Playwright scenarios
including all three `@work` journeys. Both High findings (cross-target
draft/key reuse; silent 50-row truncation) are fixed. The first Medium
demo-revocation fix was rejected by the remediation review as violating the
one-way revocation trigger; the final fix keeps revocation authoritative
(no un-revoke, `ON CONFLICT DO NOTHING`, fail-closed identity guard) and is
proven on real PostgreSQL by `scripts/test-demo-assignment-revocation.ps1`
(`DEMO_ASSIGNMENT_REVOCATION=PASS preserved=1 active=0 history=1`). See
[evidence report](reports/phase-06-work-operations-evidence-2026-07-26.md),
[review](reports/code-review-2026-07-26-phase-06.md), and
[remediation review](reports/code-review-remediation-2026-07-26-phase-06.md).

## Progress Snapshot — 2026-07-26

- Contract and UI scouting completed before implementation.
- The frozen backend surface has all required activity, assignment, log, and
  correction-history reads plus append/correction commands. No backend route or
  schema change is required.
- Generated TypeScript declarations exist, but the shared BFF is currently
  GET-only. Phase 6 therefore includes an exact POST allowlist/transport slice
  for log append and correction only.
- The approved CK FE Work prototype is the visual source. No new Stitch screen
  is needed; prototype offline queue and file-upload behavior are explicitly
  excluded because no such production contract exists.
- The guarded demo seeds snapshot-derived activities plus a deterministic
  `FIELD_WORKER` employee and three assignments. Stable assignment IDs never
  un-revoke history; the E2E lifecycle probe revokes one and leaves two active
  tasks for real browser mutation and cross-target proof.

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
  - Show the scoped mobile-first activity list and per-activity assignments
    using the frozen generated contract GETs.
  - Show append flow for new work logs with idempotent retry behavior.
  - Show correction flow for existing logs using append-only correction commands, not in-place updates.
  - Show immutable correction history for work-log changes. No approval model
    exists in the frozen contract.
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
| CREATE | `web/src/features/work/components/work-operations.module.css` | feature-local responsive styling |
| CREATE | `web/src/app/(platform)/work/{layout,loading,error}.tsx` | stable shell and recovery boundaries |
| CREATE | `web/src/app/api/work/activities/[activityId]/logs/route.ts` | CSRF/session-protected append BFF handler |
| CREATE | `web/src/app/api/work/activities/[activityId]/logs/[logId]/corrections/route.ts` | CSRF/session-protected correction BFF handler |
| CREATE | `web/tests/contracts/work-operations.contract.test.ts` | generated-client and header contract tests |
| CREATE | `web/tests/e2e/work-operations-mobile.spec.ts` | narrow-viewport flow |
| MODIFY | `web/src/server/bff/{allowed-operation,upstream-client}.ts` | exact work GETs and two POST transports |
| MODIFY | `web/src/lib/permission-navigation.ts` | expose and activate `/work` |
| MODIFY | `scripts/run-web-e2e-tests.ps1` | expose the fixed field-worker E2E persona |
| CREATE | `scripts/test-demo-assignment-revocation.ps1` | execute tenant-scoped seed → revoke → reseed lifecycle proof |
| MODIFY | `src/agriinsight/demo_tenant_{bootstrap_sql,sample_sql}.py` | deterministic employee/assignment demo bridge |

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
  - scoped operator/supervisor -> successful `2xx`; generated OpenAPI currently
    declares `200`, while Spring HTTP contract tests prove mutation `201` plus
    `Location`/`ETag`.
- UI contract:
  - assignment cards, append form, correction form, and history timeline all
    bind to server-recorded lineage only.
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
  - `python -m pytest tests/test_demo_tenant_bootstrap.py tests/test_demo_tenant_reconciliation.py -q`
  - `npm --prefix web run test -- work-operations work-mutations work-route-security`
  - `npm --prefix web exec -- playwright test tests/e2e/work-operations-mobile.spec.ts --grep "@work"`
- Broad:
  - `powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify`
  - `npm --prefix web run lint`
  - `npm --prefix web run typecheck`
  - `npm --prefix web run test`

## Acceptance Criteria

- [x] Phase 6 consumes the frozen Phase 1 generated client for assignment/log/history GETs and adds no backend routes.
- [x] `/work` works on a 375 px viewport without fake offline sync, fabricated
  priority, team, approval, or upload behavior.
- [x] Append uses `Idempotency-Key`; retry cannot create duplicate logs.
- [x] Correction uses append-only `POST /api/v1/activities/{id}/logs/{logId}/corrections` with `Idempotency-Key`, not a fabricated patch route.
- [x] `If-Match` is not attached to log append/correction flows unless a real update/revoke route is explicitly consumed outside this phase.
- [x] History timeline is server-backed and immutable from the client perspective, with bounded 50-row pagination driven by the upstream `hasMore` signal.
- [x] Demo reseed never un-revokes deterministic assignment history; the real PostgreSQL lifecycle probe preserves the revoked row fail-closed.
- [x] No speculative query layer or backend additions are introduced in this phase.

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
