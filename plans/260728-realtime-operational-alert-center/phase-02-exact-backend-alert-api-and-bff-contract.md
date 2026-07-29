---
phase: 2
title: Exact backend alert API and BFF contract
status: in-progress
effort: 1.5-2d
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
3. The envelope contains `generatedAt`, fixed `limit=50`, and `hasMore`.
   Each item contains source (`realtime_operational`), alert ID,
   policy/severity/state, safe evidence, opened/source/last-observed/
   last-evaluated timestamps, non-negative `ageSeconds`, and current-profile
   acknowledgement state/time. `OUTBOX_PUBLISH_BACKLOG` uses evidence type
   `TENANT_BACKLOG` with no evidence ID; delivery-lag and DLT policies use
   `OPERATIONAL_EVENT` plus the source event UUID. It must not contain the
   dedupe key, raw outbox data, error strings, worker counters, another
   profile's acknowledgement data, or a writable tenant/profile field.
4. The acknowledgement POST validates the path UUID, requires same existing
   state-changing-route idempotency semantics, uses
   `REALTIME_ALERT_ACKNOWLEDGE`, locks/captures the current observation time,
   writes one immutable current-profile revision, and returns a currently
   authorized open-alert representation with status `200`. Its request body is
   exactly `{}`; unknown fields and absent/non-JSON bodies fail closed.
   Resolved or foreign alerts return the same sanitized not-found contract.
   A second identical request/key replays without writing. A later observation
   requires a new idempotency key and can create a new revision.
5. Update the deterministic backend OpenAPI artifact and generated web types;
   contract drift tests stay green.
6. Preserve V22-V28 byte-for-byte. Add transactional V29 to forward-replace the
   acknowledgement function so the locked row must still be `OPEN`, then add
   nontransactional V30 with one explicit severity-rank, last-observed, ID
   partial feed index built through `CREATE INDEX CONCURRENTLY`. Backend
   readiness advances to 30; the isolated worker verifier continues to require
   successful V28 plus the latest repeatable grant because V29-V30 do not
   change its startup privilege contract.

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

## Resolved contract decisions

| Concern | Decision |
|---|---|
| Acknowledgement body | Exact empty JSON object `{}`. Both Next and Spring reject unknown fields. |
| Acknowledgement status | Always `200` with the current open-alert representation; the immutable revision is internal. |
| Replay after observation change | The original key remains a replay and reloads current state. A new observation is acknowledged only with a new key. If the alert has since resolved or is no longer visible inside an otherwise authorized scope, the replay loader is empty and the controller deliberately maps that absence through `RealtimeOperationalAlertNotFoundException` to the same sanitized `404`; it never calls a generic empty-optional failure path or returns a stored stale receipt. A caller that has lost `REALTIME_ALERT_ACKNOWLEDGE` instead fails at the permission-first boundary with `403` before any alert or idempotency lookup. |
| Resolved alert | Not acknowledgeable. V29 checks `state='OPEN'` under the same row lock and returns no row, which maps to sanitized 404. |
| Evidence | `TENANT_BACKLOG` with no ID for backlog; `OPERATIONAL_EVENT` with source UUID for delivery lag/DLT. Never expose `dedupe_key`. |
| Freshness | `generatedAt` plus per-item UTC timestamps and non-negative `ageSeconds`; no unowned “fresh/stale” threshold. |
| Ordering | `CRITICAL` before `WARNING`, then `lastObservedAt DESC`, then UUID ascending. V30 uses the same explicit rank expression. |
| Public versioning | Alert `version` is internal command-target metadata; v1 response has no ETag/If-Match contract. |

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
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertQueryStore.java` | Create | Runtime-only fixed latest-50/current-profile query port; keep the worker mutation port isolated. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\PostgresRealtimeOperationalAlertQueryStore.java` | Create | Fixed bounded ordering and exact current-profile/current-observation acknowledgement join. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertAcknowledgementStore.java` | Reuse | Existing runtime acknowledgement port backed by the locked V29 function. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertNotFoundException.java` | Modify | Extend the shared sanitized 404 contract. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V29__restrict_realtime_alert_acknowledgement_to_open_alerts.sql` | Create | Forward-replace the V28 function with an atomic open-state predicate. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V30__add_realtime_alert_feed_index_concurrently.sql` | Create | Add the exact partial feed index without a table-writing transaction. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V30__add_realtime_alert_feed_index_concurrently.sql.conf` | Create | Keep Flyway transaction mode disabled for concurrent index creation. |
| `D:\AgriInsight\backend\src\main\resources\application.yml` | Modify | Advance generic backend readiness to schema version 30. |
| `D:\AgriInsight\backend\src\main\resources\contracts\agriinsight-api-v1.openapi.json` | Modify (generated) | Deterministic OpenAPI output after route contract tests. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\RealtimeOperationalAlertHttpContractTest.java` | Create | Permission/shape/bounds/idempotency HTTP proof. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\api\RealtimeRoutesTest.java` | Modify | Assert the exact summary, alert-feed, and acknowledgement secured-route inventory. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\RealtimeOperationalAlertStoreIntegrationTest.java` | Modify | Resolved-alert acknowledgement rejection under the V29 locked function. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\RealtimeOperationalAlertQueryStoreIntegrationTest.java` | Create | Real runtime-role tenant/profile isolation, exact ordering, and 51-row lookahead proof. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\FlywayMigrationIntegrationTest.java` | Modify | Fresh/upgrade/zero-op V29-V30 and exact index eligibility proof. |
| `D:\AgriInsight\web\src\server\bff\allowed-operation.ts` | Modify | Add exact operation keys, paths, methods, and allowlisted controls. |
| `D:\AgriInsight\web\src\server\bff\upstream-client.ts` | Modify | Preserve the hardened wrappers while forwarding caller cancellation. |
| `D:\AgriInsight\web\src\features\realtime-alerts\realtime-alert-contract.ts` | Create | Strict generated-type-aligned request/response runtime schemas for Phase 2/3. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\route.ts` | Create | Same-origin feed BFF route. |
| `D:\AgriInsight\web\src\app\api\realtime\alerts\[alertId]\acknowledgements\route.ts` | Create | CSRF/idempotency acknowledgement BFF route. |
| `D:\AgriInsight\web\src\server\generated\backend\schema.d.ts` | Modify (generated) | Regenerated typed backend contract. |
| `D:\AgriInsight\web\tests\bff\allowed-operation.test.ts` | Modify | Exact route/query/method allowlist evidence. |
| `D:\AgriInsight\web\tests\bff\upstream-client.test.ts` | Modify | Reject unallowed paths/query/body and bound alert responses. |
| `D:\AgriInsight\web\tests\contracts\alert-route-security.contract.test.ts` | Create | Route-specific session/CSRF/origin/body/token/non-enumeration tests. |

## Implementation steps

1. Start with service/store tests that prove permission precedes data access,
   server-fixed latest-50 ordering is stable, profile revision joining is
   isolated, and 404/403 behavior does not enumerate a foreign tenant alert.
2. Add V29/V30 and migration tests first. V29 must retain the function
   signature, security-definer search path, grants, named unique constraint,
   and transaction-scoped lock while adding `state='OPEN'`. V30 must use one
   concurrent index whose predicate/order exactly matches the feed query.
3. Add `@TenantScoped` service methods. Require new permissions before calling
   the store; bind the database-derived tenant/profile only through the existing
   aspect. Do not implement a controller-level scope shortcut.
4. Implement a separate runtime query port/repository with a server-fixed
   51-record read, 50-record response cap, explicit rank ordering, and exact
   current-profile/current-observation join. Reject every query key in v1
   rather than adding unreviewed cursor/filter semantics.
5. Implement acknowledgement through the existing canonical idempotency flow.
   Under the alert lock, copy `last_observed_at` into the immutable current
   profile revision and then return only a current authorized projection. It
   never writes a user-provided note or status transition. Both first execution
   and replay convert an absent current open projection through the sanitized
   alert not-found exception; replay after resolution therefore returns `404`
   rather than a stale receipt, an empty `200`, or a generic `500`.
6. Register the exact routes and add OpenAPI contract cases. Regenerate the
   checked-in artifact only from the deterministic task established by the
   project; inspect the diff for unrelated drift. Update
   `RealtimeRoutesTest` to prove the registry contains exactly the existing
   summary route plus the two new alert routes.
7. Add BFF operations and same-origin route handlers by copying the hardened
   Work/Inventory mutation order. Define zod/runtime schemas that reject extra
   JSON/query fields, pass `request.signal`, do not manufacture upstream
   headers, and preserve safe correlation IDs.
8. Run focused Java and web contract suites before broader type/lint/build
   gates. Update no UI component in this phase.

## Test scenario matrix

| Priority | Scenario | Proof |
|---|---|---|
| Critical | Caller sends tenant/profile/policy override | BFF/schema rejects it; backend derives scope only from identity. |
| Critical | Foreign tenant/same-tenant foreign-profile alert state | 404/403-safe behavior, no representation or acknowledgement revision leak. |
| Critical | Replay/current-vs-later acknowledgement | Stable idempotent response for one observation; a later observation permits a distinct revision. |
| Critical | Replay after resolution/non-visibility or permission loss | An authorized replay whose projection is absent becomes sanitized `404`; a caller without the acknowledgement permission gets `403` before lookup. No stale receipt, empty success, or generic `500`. |
| Critical | Resolve races acknowledgement | V29 checks open state while locking the current alert; resolved/foreign paths return the same sanitized 404. |
| High | Query/filter injection and over-limit result | Every query key rejected; fixed 50-record server cap and matching index. |
| High | Role distinction | New read/ack permissions independent from prior `REALTIME_READ`. |
| High | Missing CSRF/origin/session | Same-origin BFF rejects before upstream call. |
| Medium | Upstream failure/oversize | Sanitized problem and bounded response body/cancellation. |

## Todo list

- [ ] Define closed no-query/latest-50 response and acknowledgement-revision contract/API examples.
- [ ] Add V29 open-only acknowledgement repair and V30 concurrent exact feed index without rewriting earlier migrations.
- [ ] Add permission-first tenant/profile service/store route implementation.
- [ ] Regenerate and verify deterministic OpenAPI/web type contract.
- [ ] Add same-origin BFF GET/POST handlers with no general-proxy capability.
- [ ] Prove exact secured-route inventory, HTTP/BFF no-query, profile isolation,
      replay-after-resolution `404`, and idempotency/re-acknowledgement cases.

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
