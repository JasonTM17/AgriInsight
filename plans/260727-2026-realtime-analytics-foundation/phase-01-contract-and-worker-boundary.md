---
phase: 1
title: Contract and worker boundary
status: in-progress
priority: P1
effort: 1.5-2d
dependencies: []
---

# Phase 1: Contract and worker boundary

## Context links

- [Plan](./plan.md)
- [Transport research](./research/research-realtime-transport.md)
- [Scout report](./reports/scout-report.md)
- [Existing event schema](../../backend/src/main/resources/contracts/agriinsight-operational-events-v1.schema.json)

## Overview

Freeze the Kafka record contract, configuration bounds, and least-privilege
worker identity before any broker loop is enabled.

## Requirements

- Functional: the Kafka value is the exact outbox v1 JSON; key is canonical
  `tenant_id:aggregate:aggregate_id`; required headers are bounded metadata.
- Security: worker uses a dedicated login that inherits only
  `agriinsight_integration`; no raw secret, token, or payload is logged.
- Non-functional: Kafka remains disabled by default and backend HTTP startup
  must not contact a broker.

## Architecture

```text
runtime login -> operational tables + outbox INSERT only
realtime login -> integration role -> outbox lease + V20 read-model write only
Kafka record -> aggregate key + exact schema-v1 JSON + bounded headers
```

## Related code files

- Modify: `D:\AgriInsight\backend\pom.xml`
- Modify: `D:\AgriInsight\backend\src\main\resources\application.yml`
- Modify: `D:\AgriInsight\backend\ops\postgres\bootstrap-roles.sql`
- Create: `D:\AgriInsight\backend\ops\postgres\configure-local-realtime-role-password.sql`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\application\OperationalEventRecord.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\integration\infrastructure\RealtimeWorkerProperties.java`
- Create/modify focused configuration and role contract tests under `backend/src/test/java`.

## Tests before

- Assert Kafka disabled by default, bounded batch/lease/send/poll settings, valid
  topic names, and no secret-bearing defaults.
- Assert the worker login has exactly the allowed integration membership and
  runtime/migrator roles do not gain it.
- Assert exact record key/header/value mapping and oversize envelope rejection.

## Implementation steps

1. Add Boot-managed Spring Kafka and Kafka test dependencies; do not pin an
   incompatible client separately.
2. Model validated configuration for topic, DLT, batch, lease, send timeout,
   poll delay, partitions, replication, and maximum record bytes.
3. Add the canonical Kafka record mapper over existing `OutboxEvent`.
4. Add `agriinsight_realtime` LOGIN and explicit safe membership to the
   NOLOGIN integration role; extend local password configuration without
   embedding values.
5. Keep all Kafka beans and scheduling behind explicit properties.

## Todo

- [ ] Write configuration, role, and record-mapping tests.
- [ ] Add managed dependencies and bounded properties.
- [ ] Add least-privilege realtime login contract.
- [ ] Prove ordinary backend startup is broker-independent.

## Success Criteria

- [ ] Contract fixtures accept current v1 events and reject drift/oversize.
- [ ] Worker role can lease outbox rows but cannot read tenant business tables.
- [ ] Runtime cannot lease/update outbox rows or write realtime tables.
- [ ] No enabled Kafka property or credential appears in checked-in defaults.

## Risk assessment

| Risk | Mitigation |
|---|---|
| Role privilege creep | exact membership/grant integration tests |
| Contract drift | exact v1 fixture plus unknown-field/type tests |
| Broker dependency breaks API | conditional beans; startup test with unreachable broker |

## Regression gate

`backend\mvnw.cmd -DskipITs test` plus focused PostgreSQL role tests on hosted CI.
