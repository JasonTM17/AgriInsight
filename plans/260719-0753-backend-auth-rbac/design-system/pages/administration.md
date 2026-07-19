# Administration page override

Status: design contract only. Production implementation depends on backend identity/RBAC phases 2-3.

## User and decision

- Primary role: tenant administrator. Secondary read-only role: auditor.
- Primary job: manage tenant users, fixed roles, and farm/warehouse/activity assignments with an audit trail.
- Primary action: invite/activate an identity or change one bounded assignment. Deactivation is destructive and always asks for confirmation.
- Security invariant: backend remains the authorization boundary. Navigation visibility is advisory; every deep link handles server-side 403/404.

## Data contract

| Surface | Authoritative source | Required context |
|---|---|---|
| Identity directory | Phase 2 internal identity contract | status, issuer subject mapping, last successful sign-in; never raw token |
| Roles and permissions | Fixed authorization matrix + Phase 3 API | role description, permission version, effective scope |
| Assignments | Phase 3-5 operational APIs | farm/warehouse/activity name + canonical code + version |
| Audit history | Phase 7 append-only audit/outbox boundary | actor, action, target, scope, UTC time, correlation ID |

Never display secrets, tokens, raw OIDC claims, provider diagnostics, or evidence that another tenant exists.

## Structure and interaction

1. Directory header: active/disabled/pending counts, scoped search, status filter, one invite action.
2. Identity table: name, email/login label, status, fixed roles, assignment summary, last activity, row menu.
3. Detail drawer: identity status, effective permissions, assignment groups, audit timeline.
4. Assignment flow: select one fixed role, then only the scopes allowed by that role; show the net permission change before save.
5. Conflict recovery: when optimistic version changes, preserve the draft, show the current server state, and require explicit reapply/cancel.

## State contract

| State | Required treatment |
|---|---|
| Loading | Stable directory rows; never flash an empty tenant |
| Empty | Explain how the first administrator provisions access; no invented identities |
| Partial assignment | Name the unavailable scope type and keep unaffected groups readable |
| Permission denied | Generic 403 recovery to Overview; no tenant/user counts |
| Identity disabled | Read-only detail plus reason/time if the viewer may see it |
| Conflict | Side-by-side current/draft change summary; no blind overwrite |
| Save failed | Correlation ID, retry, and retained draft; no raw provider/backend detail |

## URL, responsive, and accessibility

- Directory state is addressable by status, search, role, and page. Identity detail uses a stable UUID route; canonical business codes remain visible integration labels.
- Mobile uses a step-indicated assignment flow with back/cancel and unsaved-change protection. Tables become identity rows; critical actions never hide behind swipe or hover.
- Scope chips always include text. Status is icon + label. Confirmation returns focus to the invoking control.
- Audit timestamps use locale-aware display plus accessible UTC detail.

## Acceptance

- Tenant admin, auditor, disabled identity, stale permission, cross-tenant guessed ID, conflict, and save-failure journeys are all represented before implementation starts.
