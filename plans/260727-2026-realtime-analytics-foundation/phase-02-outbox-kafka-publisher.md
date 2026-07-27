---
phase: 2
title: Outbox Kafka publisher
status: in-progress
priority: P1
effort: 1.5-2d
dependencies:
  - 1
---

# Phase 2: Outbox Kafka publisher

## Context links

- [Plan](./plan.md)
- [Existing drain service](../../backend/src/main/java/com/agriinsight/backend/integration/application/OutboxDrainService.java)
- [Existing PostgreSQL store](../../backend/src/main/java/com/agriinsight/backend/integration/infrastructure/PostgresOutboxStore.java)

## Overview

Connect the fenced outbox drain to Kafka with synchronous, bounded confirmation
before acknowledgement and safe retry/dead-letter behavior on ambiguity.

## Requirements

- Publish the exact v1 envelope with aggregate key and `event_id`,
  `schema_version`, `event_type`, `tenant_id` headers.
- Acknowledge outbox only after Kafka confirms the send.
- On timeout/error, fail the current lease with a redacted bounded error.
- Never claim end-to-end exactly once; downstream dedupe is mandatory.

## Architecture

```text
scheduled poll -> lease fenced rows -> Kafka send confirmation
    success -> fenced outbox acknowledge
    failure -> fenced retry/backoff/dead-letter
```

## Related code files

- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\infrastructure\PostgresOutboxStore.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\application\OutboxPublishingService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\application\OperationalEventPublisher.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\infrastructure\KafkaOperationalEventPublisher.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\infrastructure\OutboxPublishingSchedule.java`
- Create: `D:\AgriInsight\compose.realtime.yaml`
- Create/modify focused publisher tests under `backend/src/test/java`.

## Tests before

- Kafka success acknowledges once; error/timeout fails once; stale lease never
  reports success.
- Duplicate application-level publish remains safe for the consumer.
- One aggregate always maps to one partition key.
- Disabled publisher creates no scheduler, topic, or broker connection.

## Implementation steps

1. Make `PostgresOutboxStore` worker-conditional instead of identity-conditional.
2. Add a small publishing service around the existing lease/ack/fail contract.
3. Send records with an idempotent producer, `acks=all`, bounded delivery/send
   timeouts, and no application-level blind retry outside the outbox.
4. Add one conditional fixed-delay schedule; prevent overlapping local runs.
5. Add explicit main and DLT `NewTopic` definitions.
6. Add optional Kafka 4.3.1 KRaft + non-web worker Compose services, D-local
   state, loopback external listener, healthchecks, read-only rootfs where
   supported, and no host pull/build when disk guard fails.

## Todo

- [ ] Write publisher failure-matrix tests.
- [ ] Implement conditional publisher and schedule.
- [ ] Add explicit topic configuration.
- [ ] Add optional local topology and config validation.

## Success Criteria

- [ ] Kafka confirmation precedes fenced outbox acknowledgement.
- [ ] Timeout/error preserves replay and redacts operational error text.
- [ ] Same aggregate uses the same key; different aggregates may publish in parallel.
- [ ] Existing backend profile works without Kafka.

## Risk assessment

| Risk | Mitigation |
|---|---|
| Confirmed send but lost response | at-least-once retry plus consumer event-id dedupe |
| Publisher overlap | single schedule lock and fenced DB leases |
| Kafka outage blocks commands | outbox decouples domain commit; bounded worker retry |

## Regression gate

Publisher unit tests, Compose config, and a hosted broker send/ack integration test.
