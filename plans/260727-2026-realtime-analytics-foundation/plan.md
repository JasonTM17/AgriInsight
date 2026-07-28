---
title: Realtime analytics foundation
description: >-
  Publish the existing transactional outbox to Kafka and materialize a
  replay-safe, tenant-scoped realtime summary API.
status: completed
priority: P1
effort: 7-9d
branch: main
tags:
  - feature
  - backend
  - database
  - api
  - infra
  - critical
blockedBy: []
blocks: []
created: '2026-07-27T13:26:28.063Z'
createdBy: 'ck:plan'
source: skill
---

# Realtime analytics foundation

## Overview

Deliver the first production-shaped realtime slice after the accepted outbox
handoff. A separately enabled worker leases outbox events, publishes the exact
v1 envelope to Apache Kafka, consumes it idempotently into PostgreSQL read
models, and exposes an authenticated tenant summary. Existing operational APIs,
Python artifacts, and browser routes remain compatible.

## Scope challenge

- Existing code: V18-V19 outbox, JSON Schema v1, fenced lease/retry/dead-letter,
  restricted integration role, Spring tenant security, and hosted CI.
- Minimum change: one optional Kafka worker, one V20 read model, one bounded
  authenticated GET, real broker integration evidence.
- Complexity: more than eight files is justified by four independent trust
  boundaries: DB role, broker publication, consumer dedupe/order, tenant API.
- Selected scope: HOLD. Defer SSE/web presentation, domain-specific alerts,
  IoT ingestion, CDC/Debezium, mobile, ML, and AI until this transport is green.

## Decisions

| Area | Decision |
|---|---|
| Broker | Official `apache/kafka:4.3.1`, KRaft; local single node, production replication remains an operator input |
| Delivery | At-least-once end to end; no distributed exactly-once claim |
| Producer | Spring Kafka, `acks=all`, idempotence enabled, aggregate key |
| Consumer | Record-at-a-time DB transaction, event-id/checksum dedupe, version-gap rejection, bounded DLT recovery |
| Read model | PostgreSQL V20 tables; integration worker writes, runtime reads under FORCE RLS |
| API | `GET /api/v1/realtime/summary`, tenant-wide `REALTIME_READ`, bounded server aggregation |
| Runtime | Kafka disabled by default; one separate non-web worker credentialed as the least-privilege realtime login |

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Contract and worker boundary](./phase-01-contract-and-worker-boundary.md) | Completed |
| 2 | [Outbox Kafka publisher](./phase-02-outbox-kafka-publisher.md) | Completed |
| 3 | [Replay-safe consumer and summary API](./phase-03-replay-safe-consumer-and-summary-api.md) | Completed |
| 4 | [Hosted integration and handoff](./phase-04-hosted-integration-and-handoff.md) | Completed |

## Dependencies

- Consumes the verified core output of
  [`260719-0753-backend-auth-rbac`](../260719-0753-backend-auth-rbac/plan.md):
  outbox schema v1 and fenced drain. Its external release approvals do not
  block implementation or hosted verification here.
- Does not depend on the blocked external promotion portion of
  [`260722-2342-production-web-platform`](../260722-2342-production-web-platform/plan.md).
- Apache Kafka and Spring Kafka versions/configuration are verified against
  current official documentation in
  [`research/research-realtime-transport.md`](./research/research-realtime-transport.md).

## Success criteria

- One committed operational event reaches Kafka and the tenant read model.
- Duplicate delivery never increments metrics twice; payload reuse with a
  conflicting checksum fails closed.
- Per-aggregate order is preserved; forward gaps and poison records reach an
  explicit DLT after bounded retries.
- Runtime cannot lease outbox rows; worker cannot read unrelated operational
  tables; cross-tenant API access remains impossible.
- Kafka outage leaves the outbox retryable/dead-lettered without acknowledging
  an unconfirmed publish.
- Unit, PostgreSQL, embedded/real Kafka, OpenAPI, Python, web contract, secret,
  image, and hosted disk gates pass.
- Docs state measured freshness, replay behavior, operations/rollback, and all
  deferred scope without making a production-release claim.

## Acceptance evidence

- Hosted workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) completed successfully at commit `90131d26da8694e63899183ebe20b1866943f657`.
- Real PostgreSQL/Kafka job [`90207600976`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699/job/90207600976) logged `REALTIME_E2E result=PASS freshness_seconds=0 recovery_millis=5094 freshness_p95_millis=130 samples=20`.
- The [acceptance report](./reports/acceptance-2026-07-28-realtime-foundation.md) records the scope, rollback boundary, review basis, and production-owner gates.

## Unresolved questions

- Production Kafka broker count, SASL/TLS provider, retention, and ownership
  remain deployment inputs; safe defaults and validation must fail closed.
- External registry and production OIDC approvals remain owner-gated and do not
  change this plan's internal acceptance boundary.
