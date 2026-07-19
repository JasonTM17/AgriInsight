# Work page override

Status: design contract only. Operational activities, assignments, corrections, and harvest records depend on backend phase 4.

## User and decision

- Primary roles: field worker and farm manager.
- Primary job: see assigned work, record bounded activity evidence, and correct mistakes without rewriting history.
- Primary action: open one assigned task and submit the permitted status/evidence transition.
- Audit invariant: corrections append a reasoned event and link to the original; the UI never offers silent destructive edit/delete.

## Data contract

| Surface | Future source | Required context |
|---|---|---|
| Assigned queue | Phase 4 activity/assignment API | assignee, farm/field/season, due window, status, version |
| Activity detail | Phase 4 operational API | type, inputs/units, workforce, evidence, correction lineage |
| Harvest record | Phase 4 operational API | quantity/unit, revenue context, field/season, correction lineage |
| Analytics context | Approved Gold read model | clearly labeled cutoff; read-only comparison only |

## Structure and interaction

1. Today/overdue/upcoming queue with assigned scope and offline/stale status.
2. Detail stepper: verify location/context → enter bounded evidence → review → submit.
3. Manager assignment view lists only authorized workforce and fields; no free-form identity lookup.
4. Correction flow shows original/current values, reason, effect, and append-only outcome.
5. Unsaved entry survives recoverable network failure locally only under an approved non-sensitive draft policy.

## State contract

| State | Required treatment |
|---|---|
| Loading | Queue skeleton preserves priority/order space |
| No assigned work | State the selected date/scope and next refresh; no generic celebration |
| Offline | Read cached assignment if policy allows; queue submission visibly, never claim sync |
| Assignment revoked | Stop mutation, retain safe draft, explain manager contact path |
| Permission denied | Generic task denial; no foreign assignee/field data |
| Version conflict | Compare authoritative task status with draft and offer safe reconciliation |
| Validation/save failed | Field-level error, retained draft, correlation ID; focus first invalid field |

## URL, responsive, and accessibility

- URL owns date/status/task UUID and returns to the prior queue position. Filter state is not hidden only in component memory.
- Mobile is primary: one task/action per screen, 48 px controls, safe areas, visible labels, numeric keyboards with unit shown, back/cancel and unsaved-change guard.
- Status never relies on color. Evidence upload has type/size/progress/error text and does not expose local paths.

## Acceptance

- Worker/manager, empty/offline/revoked/denied/conflict/validation/save-failure, correction, and queued-sync fixtures exist before implementation.
- Reviewed design fixture: [`../prototypes/work-activity-prototype.html`](../prototypes/work-activity-prototype.html), with evidence in [`../prototypes/work-prototype-review.md`](../prototypes/work-prototype-review.md). Use `?state=offline`, `?state=conflict`, or `?state=revoked` to inspect protected state behavior.
