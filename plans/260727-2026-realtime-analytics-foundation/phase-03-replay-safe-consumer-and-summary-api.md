---
phase: 3
title: "Replay-safe consumer and summary API"
status: completed
priority: P1
effort: "2-3d"
dependencies: [2]
---

# Phase 3: Replay-safe consumer and summary API

## Context links

- [Plan](./plan.md)
- [Authorization matrix](../260719-0753-backend-auth-rbac/authorization-matrix.md)
- [System architecture](../../docs/system-architecture.md)

## Overview

Consume Kafka records into a durable idempotent PostgreSQL read model and expose
a tenant-scoped realtime summary through the existing deny-by-default API.

## Requirements

- Strictly validate schema v1, key/header/value agreement, event type, and size.
- Dedupe identical `event_id` + checksum; reject event-id payload conflicts.
- Preserve aggregate order; accept the first observed version as a baseline,
  then require consecutive versions.
- Materialize bounded counts/freshness without storing raw event payload.
- Expose only tenant-wide `REALTIME_READ` to Tenant Admin, Executive, and Data
  Analyst; Farm/Inventory/Field/Supplier roles remain denied.

## Architecture

```text
Kafka record
  -> strict parser
  -> one PostgreSQL transaction
       receipt(event_id, checksum, broker coordinate)
       aggregate_progress(last_version)
       tenant_metric(count, last timestamps)
  -> return
  -> Kafka record offset commit
failure -> bounded retry -> DLT -> source offset commit only after DLT confirm
```

## Related code files

- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V20__create_realtime_read_models.sql`
- Modify: `D:\AgriInsight\backend\src\main\resources\db\migration\R__tenant_rls_helpers_and_grants.sql`
- Modify: `D:\AgriInsight\backend\src\main\resources\application.yml`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\domain\Permission.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\domain\Role.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeRoutes.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeSummaryController.java`
- Create: response DTOs under `backend/src/main/java/com/agriinsight/backend/realtime/api/`
- Create: consumer/parser/service/store models under `backend/src/main/java/com/agriinsight/backend/realtime/`
- Modify: deterministic backend OpenAPI artifact and generated web types.
- Create/modify unit, HTTP, security, RLS, query-plan, and broker tests.

## Tests before

- Duplicate identical record increments no metric; conflicting same ID fails.
- First baseline applies; next consecutive version applies; stale/gap fails.
- DB commit failure prevents listener success/offset commit.
- Poison/schema/key/header failures reach same-partition DLT after bounded retry.
- Tenant A API sees only A; unauthorized roles get 403; missing context reads none.
- Summary is bounded, stable-sorted, and reports non-negative freshness.

## Implementation steps

1. Add V20 receipts, aggregate progress, and tenant metric tables with tenant
   FKs, unique broker coordinates, FORCE RLS, and tenant-leading indexes.
2. Grant integration-only writes and runtime tenant-scoped reads; update schema
   readiness from 19 to 20.
3. Implement strict envelope parsing and checksum comparison without persisting
   raw payload.
4. Apply receipt/progress/metric changes in one transaction.
5. Configure record ack after listener return and a bounded DLT recoverer.
6. Add `REALTIME_READ`, exact route registration, tenant transaction, query
   limits, response DTOs, error metadata, and OpenAPI export.
7. Regenerate the checked-in TypeScript schema and run contract drift checks.

## Todo

- [x] Write parser/dedupe/order tests.
- [x] Add V20 and least-privilege grants/RLS.
- [x] Implement consumer and DLT behavior.
- [x] Implement authenticated summary API.
- [x] Regenerate and verify public contracts.

## Success Criteria

- [x] Duplicate and replay behavior is deterministic and durable.
- [x] Gaps/conflicts fail closed and become observable in DLT evidence.
- [x] Raw event values are not copied into the read model or API.
- [x] Route registry, permission matrix, RLS, and OpenAPI remain aligned.
- [x] Summary query remains index-backed and bounded at scale.

## Completion evidence

Hosted job [`90207600976`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699/job/90207600976) passed real PostgreSQL/Kafka publish, replay, DLT, recovery, tenant RLS, and authenticated summary coverage. See the [plan acceptance report](./reports/acceptance-2026-07-28-realtime-foundation.md).

## Risk assessment

| Risk | Mitigation |
|---|---|
| Offset commits before DB durability | transaction completes before listener returns; record ack mode |
| Poison pill stalls partition forever | bounded retry then same-partition DLT |
| Cross-tenant metric leak | app permission + tenant transaction + FORCE RLS |
| Event payload becomes PII store | retain SHA-256 and metadata only |

## Regression gate

Focused unit/HTTP tests, V1-V20 PostgreSQL integration, embedded Kafka E2E,
OpenAPI drift, web generated-contract drift, and query-plan assertions.
