---
phase: 10
title: "tenant-administration"
status: pending
priority: P1
effort: "3d"
dependencies: [1, 3, 4]
---

# Phase 10: tenant-administration

## Overview

Ship the browser-grade tenant administration surface over Phase 1 backend read and mutation resource families. This phase includes the real allowed admin actions, must deny `Supplier`, and must not invent invite, JIT, password, or email workflows.

## Context Links

- Parent phase boundary and dependency order: `plans/260722-2342-production-web-platform/plan.md:119-147`
- Existing exact identity, tenant context, audit, and conflict behavior in backend facts: `README.md:25-38`
- Current dashboard has no auth/RBAC and must not be exposed publicly: `README.md:85`

## Requirements

- Functional: consume Phase 1 read models and existing backend resource route families for user lifecycle, role assignments, external identity links, and farm/warehouse/activity assignments.
- Functional: expose the real allowed admin mutations: create/activate/deactivate user lifecycle, role assignment changes, external identity link/unlink where Phase 1 allows it, and farm/warehouse/activity assignment changes.
- Functional: keep all admin routes permission-gated on the server, not just hidden in navigation.
- Functional: deep-linking to any admin URL without permission returns a true `403` state in the protected shell; `Supplier` is always denied.
- Functional: show conflict states when the selected subject was changed, revoked, or removed between list and detail fetches.
- Functional: surface a secure bounded audit timeline for the admin actions above, with explicit pagination/filter limits and no unbounded history dump.
- Non-functional: reuse existing backend authorization source of truth; never trust browser role hints or user-supplied scope labels.
- Non-functional: never return or render raw external claims/provider subjects. Manual create/link may accept an administrator-supplied subject in a non-persisted, non-autocomplete secret-like field because the existing command requires it; immediately submit server-side, never echo it, and scrub it from logs, errors, cache, history, analytics, and client props.
- Non-functional: no invite, JIT provisioning, password reset, password bootstrap, or email-trigger fiction in copy, tests, or UI affordances.

## Data Flow And Interfaces

1. Browser -> Next protected admin route -> BFF -> existing Spring user/role/external-identity/assignment resource families from Phase 1 -> safe read models -> HTML/JSON.
2. Browser mutation -> server action/BFF -> existing Spring mutation resource families -> optimistic/version or idempotency check -> safe success/conflict state -> refreshed read model.
3. Server authorization failure -> Spring `403` -> BFF preserves `403` -> UI renders permission-denied state with no redirect loop; `Supplier` stays denied.
4. Concurrent change -> backend `404`, `409`, or equivalent Phase 1 conflict code -> BFF maps to conflict state -> UI offers reload/back actions only.

Resource-family rule to freeze before UI work:

- Reuse the exact Phase 1 resource families for users, roles, external identities, farm assignments, warehouse assignments, activity assignments, and audit history.
- Do not mint `/api/v1/admin/*`.
- If a required read or mutation path is missing, stop and amend the earlier backend contract rather than inventing a parallel admin namespace in Phase 10.

Minimum UI contract:

```ts
type AdminReadError = "forbidden" | "not-found" | "conflict" | "unavailable";
type AdminMutation =
  | "deactivate-user"
  | "create-user"
  | "reactivate-user"
  | "assign-role"
  | "remove-role"
  | "link-external-identity"
  | "unlink-external-identity"
  | "assign-farm"
  | "unassign-farm"
  | "assign-warehouse"
  | "unassign-warehouse"
  | "assign-activity"
  | "unassign-activity";

interface AdminSubjectVm {
  userKey: string;
  displayName: string;
  status: "active" | "inactive";
  roleLabels: string[];
  providerLinks: Array<{ providerLabel: string; linked: boolean }>;
  assignments: Array<{ scopeType: "farm" | "warehouse" | "activity"; scopeLabel: string; status: string }>;
}

interface AdminAuditEntryVm {
  at: string;
  actorLabel: string;
  actionLabel: string;
  targetLabel: string;
  scopeLabel?: string;
}
```

Rule: if the backend cannot distinguish `403`, `404`, and conflict states, or if it only exposes raw subject/claims, do backend contract work first. The web tier must not collapse statuses or leak identity internals.

## File Matrix

| Action | Path | Purpose |
|---|---|---|
| CREATE | `web/src/app/(platform)/admin/page.tsx` | landing page with safe summaries |
| CREATE | `web/src/app/(platform)/admin/users/[userKey]/page.tsx` | subject detail over safe opaque key |
| CREATE | `web/src/app/(platform)/admin/users/[userKey]/actions.ts` | server actions for allowed mutations |
| CREATE | `web/src/app/(platform)/admin/audit/page.tsx` | bounded audit timeline |
| CREATE | `web/src/lib/server/admin-resource-client.ts` | BFF client for Phase 1 resource families |
| CREATE | `web/src/lib/server/admin-read-model.ts` | safe read model normalization and error mapping |
| CREATE | `web/src/lib/server/admin-command-model.ts` | mutation envelopes, conflict mapping, idempotency keys |
| CREATE | `web/src/components/admin/*` | tables, forms, forbidden, conflict, empty states |
| CREATE | `web/tests/components/admin*.test.tsx` | UI permission/conflict coverage |
| CREATE | `web/tests/e2e/admin-lifecycle-and-assignments.spec.ts` | read + mutation journeys with `Supplier` denied |
| CREATE | `web/src/content/vi/administration.ts` | domain copy without invite/JIT/password/email fiction |

## TDD Plan

### RED

1. Add BFF tests proving Phase 1 reads and mutations map `403`, `404`, and conflict states to distinct UI states.
2. Add component/server-action tests proving subjects are never returned/rendered/echoed and are scrubbed from logs/errors/cache after an authorized manual create/link submission.
3. Add server-action tests proving allowed mutations emit idempotent/conflict-safe requests over the existing resource families.
4. Add Playwright tests proving `Supplier` deep-link gets a rendered `403` page and authorized roles can execute the allowed lifecycle/assignment changes.

### GREEN

1. Implement BFF model mapping and protected admin route loaders over existing backend resources.
2. Implement server actions and UI affordances for the allowed lifecycle and assignment mutations only.
3. Implement admin landing, subject detail, and bounded audit timeline pages with explicit read/write boundaries.

### REFACTOR

1. Extract shared protected-shell forbidden/conflict components for reuse in Phase 11.
2. Collapse duplicated table/action formatters across roles, provider links, and assignments.
3. Tighten response payloads so only display-safe labels and opaque keys cross the BFF boundary.

## Implementation Steps

1. Freeze the Phase 1 resource-family contract and permissions with exact status-code semantics; re-grep the real paths before coding.
2. Build BFF mappings, safe read models, and protected routes first.
3. Build server actions for the allowed lifecycle, role, external-identity, farm, warehouse, and activity assignment mutations second.
4. Build admin pages and explicit conflict/forbidden states third.
5. Close with end-to-end permission journeys, bounded audit evidence checks, and `Supplier` denial coverage.

## Commands

Focused:

```powershell
.\backend\mvnw.cmd -q test -Dtest=*User*,*Role*,*ExternalIdentity*,*Assignment*
npm --prefix web run test -- admin
npm --prefix web run test:e2e -- admin-lifecycle-and-assignments
```

Broad:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
npm --prefix web run build
npm --prefix web run test
```

## Acceptance Criteria

- [ ] Phase 10 consumes the exact Phase 1 user/role/external-identity/assignment resource families and does not mint `/api/v1/admin/*`.
- [ ] The real allowed admin mutations are available for authorized users only: user lifecycle, role, external identity, farm, warehouse, and activity assignments.
- [ ] No invite, JIT provisioning, password, or email-trigger copy exists anywhere in this phase.
- [ ] Server-side deep-link authorization returns a real `403` state for denied users, and `Supplier` is denied everywhere.
- [ ] Concurrent removal/revocation surfaces explicit conflict or not-found states; no silent empty tables.
- [ ] Browser-visible responses use only safe labels/opaque keys; an administrator-supplied create/link subject is one-way, never echoed, logged, cached, or retained in client state.
- [ ] Audit timeline is bounded, evidence-oriented, and reflects backend truth for the allowed actions.

## Risks And Rollback

| Risk | Likelihood x Impact | Mitigation | Rollback |
|---|---|---|---|
| Phase 10 invents a parallel admin API namespace | High x High | re-grep and reuse Phase 1 resource families only | remove the parallel client/server path and realign to Phase 1 |
| Admin UI exposes actions not actually allowed | Medium x High | allowed-mutation matrix from Phase 1 + E2E coverage | hide unsupported actions and keep existing backend truth |
| Sensitive identifiers or claims leak to browser | Medium x High | display-safe DTO review + BFF filtering | trim payloads and redeploy; no schema migration needed |
| Deep-link 403 redirects loop in shell | Medium x Medium | dedicated SSR forbidden route tests | bypass redirect logic and render static forbidden page |

## Dependencies And Ownership

- Depends on Phase 1 admin/auth contracts plus Phase 3 BFF foundation and Phase 4 shell/navigation.
- Owns admin BFF models, server actions, admin route tree, and admin tests only unless a verified Phase 1 contract gap forces explicit re-plan.
- Must not modify release workflows, analytics routes, or non-admin domain pages.

## Commit Slices

1. `feat(web): add tenant admin read models and safe labels`
2. `feat(web): add tenant lifecycle and assignment actions`
3. `test(web): cover supplier denial and admin conflicts`

## Locked Contract Inputs

- Resource families are `/api/v1/users`, `/api/v1/users/{id}/roles`, `/api/v1/users/{id}/external-identities`, `/api/v1/farm-assignments`, `/api/v1/warehouse-assignments`, `/api/v1/activities/{id}/assignments`, and `/api/v1/audit-events`; Phase 1 adds only the missing GETs and preserves existing mutation templates.
- `userKey` is the backend profile UUID used as an opaque URL identifier. External-identity link/unlink remains allowed only for `IDENTITY_USER_MANAGE`; raw issuer subject is one-way input and never returned/rendered.
