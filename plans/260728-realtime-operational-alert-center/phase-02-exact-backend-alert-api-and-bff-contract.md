---
phase: 2
title: "Exact backend alert API and BFF contract"
status: pending
effort: "1.5-2d"
---

# Phase 2: Exact backend alert API and BFF contract

## Overview

Priority: P1  
Current status: pending; depends on Phase 1 durable alert lifecycle  
Owner boundary: Spring HTTP contract + Next server BFF

Expose the new projection through a narrow, tenant-safe feed and an idempotent
acknowledgement operation. The existing realtime summary remains unchanged;
the new contract owns alert lifecycle data and never becomes a general proxy or
client-selectable tenant query.

## Context links

- [Plan overview](./plan.md)
- [Phase 1 lifecycle](./phase-01-tenant-safe-alert-lifecycle.md)
- [`D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeRoutes.java`](../../backend/src/main/java/com/agriinsight/backend/realtime/api/RealtimeRoutes.java)
- [`D:\AgriInsight\web\src\server\bff\allowed-operation.ts`](../../web/src/server/bff/allowed-operation.ts)
- [`D:\AgriInsight\docs\data-contracts.md`](../../docs/data-contracts.md)

## Requirements

### Backend contract

1. Register exact operations in `SecuredRouteRegistry`:
   `GET /api/v1/realtime/alerts` and
   `POST /api/v1/realtime/alerts/{id}/acknowledgements`.
2. The GET operation derives tenant/profile from the authenticated context,
   uses `REALTIME_ALERT_READ`, accepts no client filter, tenant, profile, page,
   or cursor controls in v1, and returns at most the latest 50 open alerts in
   fixed severity-rank/last-observed/ID order plus `hasMore`. Historical and
   resolved-alert pagination is explicitly deferred rather than pretending a
   mutable live feed can provide snapshot cursors.
3. The envelope contains source (`realtime_operational`), policy/severity/state,
   safe evidence type/ID, timestamps, current-profile acknowledgement state,
   freshness metadata, fixed limit, and `hasMore`. It must not contain raw
   outbox data, error strings, other profiles' acknowledgement data, or a
   writable tenant/profile field.
4. The acknowledgement POST validates the path UUID, requires same existing
   state-changing-route idempotency semantics, uses
   `REALTIME_ALERT_ACKNOWLEDGE`, locks/captures the current observation time,
   writes one immutable current-profile revision, and returns a currently
   authorized alert representation. It is a no-op/replay on a second identical
   request, while a later alert observation allows a new revision.
5. Update the deterministic backend OpenAPI artifact and generated web types;
   contract drift tests stay green.

### BFF contract

1. Add only the two generated backend operations to the typed BFF allowlist.
2. Provide same-origin GET/POST routes. The GET rejects unexpected query keys;
   the POST enforces opaque session, same-origin/CSRF, content type, body size,
   UUID path, idempotency key, no client tenant/profile/policy fields, response
   cap, cancellation, correlation ID forwarding, and sanitized upstream
   problems.
3. Keep server-held bearer tokens internal. Do not call the backend from a
   browser component or cache a response without tenant/principal/permission
   context.

## Architecture

```text
Current opaque session
        │
        ├─ server route validates session + input
        │
        ▼
Exact BFF allowed operation ───────────────┐
        │                                  │
        ▼                                  │
Spring secured route → @TenantScoped       │
        │                                  │
        ▼                                  │
FORCE RLS alert + current-profile acknowledgement revision  │
                                           │
Browser receives typed, bounded, source-labelled view data only
```

## Related code files

| Path | Action | Purpose |
|---|---|---|
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeRoutes.java` | Modify | Register the two exact permission-bound routes. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeOperationalAlertController.java` | Create | GET feed and acknowledgement controller mappings. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeOperationalAlertResponse.java` | Create | Closed, source-labelled response records and OpenAPI annotations. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertService.java` | Create | `@TenantScoped` permission/scope/service boundary. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertStore.java` | Modify | Add fixed latest-50 tenant feed and acknowledgement-revision port methods only. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\PostgresRealtimeOperationalAlertStore.java` | Modify | Fixed bounded ordering, current-profile revision join, locked idempotent acknowledgement SQL. |
| `D:\AgriInsight\backend\src\main\resources\contracts\agriinsight-api-v1.openapi.json` | Modify (generated) | Deterministic OpenAPI output after route contract tests. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\RealtimeOperationalAlertHttpContractTest.java` | Create | Permission/shape/bounds/idempotency HTTP proof. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\RealtimeOperationalAlertStoreIntegrationTest.java` | Modify | Fixed-window/current-profile revision/cross-tenant mutation proof. |
| `D:\AgriInsight\web\src\server\bff\allowed-operation.ts` | Modify | Add exact operation keys, paths, methods, and allowlisted controls. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\route.ts` | Create | Same-origin feed BFF route. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\[alertId]\acknowledgements\route.ts` | Create | CSRF/idempotency acknowledgement BFF route. |
| `D:\AgriInsight\web\src\server\generated\backend\schema.d.ts` | Modify (generated) | Regenerated typed backend contract. |
| `D:\AgriInsight\web\tests\bff\allowed-operation.test.ts` | Modify | Exact route/query/method allowlist evidence. |
| `D:\AgriInsight\web\tests\bff\upstream-client.test.ts` | Modify | Reject unallowed paths/query/body and bound alert responses. |
| `D:\AgriInsight\web\tests\bff\proxy-security.test.ts` | Modify | Session/CSRF/origin/token/non-enumeration tests. |

## Implementation steps

1. Start with service/store tests that prove permission precedes data access,
   server-fixed latest-50 ordering is stable, profile revision joining is
   isolated, and 404/403 behavior does not enumerate a foreign tenant alert.
2. Add `@TenantScoped` service methods. Require new permissions before calling
   the store; bind the database-derived tenant/profile only through the existing
   aspect. Do not implement a controller-level scope shortcut.
3. Implement exact response records with a server-fixed 50-record cap and
   matching tenant/state/severity/last-observed index. Reject every query key
   in v1 rather than adding unreviewed cursor/filter semantics.
4. Implement acknowledgement through the existing canonical idempotency flow.
   Under the alert lock, copy `last_observed_at` into the immutable current
   profile revision and then return only a current authorized projection. It
   never writes a user-provided note or status transition.
5. Register the exact routes and add OpenAPI contract cases. Regenerate the
   checked-in artifact only from the deterministic task established by the
   project; inspect the diff for unrelated drift.
6. Add BFF operations and same-origin route handlers by copying the hardened
   Work/Inventory mutation order. Define zod/runtime schemas that reject extra
   JSON/query fields, do not manufacture upstream headers, and preserve safe
   correlation IDs.
7. Run focused Java and web contract suites before broader type/lint/build
   gates. Update no UI component in this phase.

## Test scenario matrix

| Priority | Scenario | Proof |
|---|---|---|
| Critical | Caller sends tenant/profile/policy override | BFF/schema rejects it; backend derives scope only from identity. |
| Critical | Foreign tenant/same-tenant foreign-profile alert state | 404/403-safe behavior, no representation or acknowledgement revision leak. |
| Critical | Replay/current-vs-later acknowledgement | Stable idempotent response for one observation; a later observation permits a distinct revision. |
| High | Query/filter injection and over-limit result | Every query key rejected; fixed 50-record server cap and matching index. |
| High | Role distinction | New read/ack permissions independent from prior `REALTIME_READ`. |
| High | Missing CSRF/origin/session | Same-origin BFF rejects before upstream call. |
| Medium | Upstream failure/oversize | Sanitized problem and bounded response body/cancellation. |

## Todo list

- [ ] Define closed no-query/latest-50 response and acknowledgement-revision contract/API examples.
- [ ] Add permission-first tenant/profile service/store route implementation.
- [ ] Regenerate and verify deterministic OpenAPI/web type contract.
- [ ] Add same-origin BFF GET/POST handlers with no general-proxy capability.
- [ ] Prove HTTP/BFF no-query, profile isolation, and idempotency/re-acknowledgement cases.

## Success criteria

- [ ] Only permitted tenant users can list or acknowledge their own alert data.
- [ ] Feed is server-fixed at latest 50 open alerts, source-labelled, and never
  exposes raw payload/error/role/profile/tenant input controls or a mutable
  cursor promise.
- [ ] Acknowledgement is idempotent and replay-safe under a currently valid
  authorization context.
- [ ] Backend OpenAPI and generated web schema remain deterministic and exact.
- [ ] BFF never leaks a backend bearer token or accepts proxy-style paths.

## Risk assessment

- Cursor pagination can introduce duplicates/skips if worker-owned ordering
  changes. Mitigation: V1 has no cursor/history pagination; it exposes a
  bounded live alert panel only. A future history feed needs a separately
  approved snapshot or signed-cursor design.
- A new permission can be seeded inconsistently in Java/SQL. Mitigation:
  catalog and fresh-migration/role integration tests.
- A friendly UI API can accidentally become a proxy. Mitigation: literal
  allowlist operations and route-specific schemas.

## Security considerations

- Every state-changing route must keep current same-origin/CSRF/session/
  idempotency ordering. The browser never controls tenant or profile scope.
- Do not expose `event_id` if it would enable an unauthorized resource lookup;
  only bounded evidence labels/IDs already permitted by the API contract may
  be returned.
- Avoid a cache key that lacks tenant/principal/permission/current-profile
  acknowledgement revision state.

## Next steps

Phase 3 consumes only the generated BFF contract. It does not add a browser
connection to Spring/Kafka, change a backend route, or recompute alert state.
