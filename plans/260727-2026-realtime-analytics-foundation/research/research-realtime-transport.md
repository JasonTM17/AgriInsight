---
type: researcher
date: 2026-07-27
---

# Research: Realtime transport

## Summary

The existing fenced outbox should publish to Kafka directly through an optional
Spring worker. A direct PostgreSQL consumer would be simpler but would not
create the replayable transport required by the roadmap. Debezium adds Connect,
connector, and CDC operational surfaces before they are needed.

## Existing evidence

- `OutboxDrainService` bounds batch, lease, backoff, and error text.
- `PostgresOutboxStore` provides per-aggregate predecessor gating plus
  token/generation fencing.
- The exact event value already exists as JSON Schema v1.
- No Kafka dependency, scheduler, consumer, or public drain route exists.
- The integration role is NOLOGIN; a future worker login was intentionally
  deferred and must be separately constrained.

## Options

| Option | Services added | Typical local latency | Replay/decoupling | Decision |
|---|---:|---:|---|---|
| Poll outbox directly into read model | 1 worker | 1-5s | DB-coupled, no independent replay | Reject |
| Outbox worker -> Kafka -> consumer | broker + worker | 0.1-5s | durable replay, partition ordering, multiple future consumers | Select |
| Debezium/Connect CDC -> Kafka | broker + Connect + connector | 0.5-5s | powerful generic CDC, larger ops/security surface | Defer |

## Selected design

- Official Apache Kafka 4.3.1 JVM image in KRaft mode.
- Spring Boot 4.1 auto-configuration and Boot-managed Spring Kafka dependency.
- Producer explicitly uses idempotence, `acks=all`, retries, and no more than
  five in-flight requests. The outbox still owns application-level replay.
- Record key is tenant + aggregate type + aggregate ID. Outbox predecessor
  gating and Kafka partitioning preserve aggregate order.
- Consumer writes an event receipt, aggregate progress, and tenant metric in
  one PostgreSQL transaction. Identical event IDs dedupe; conflicting checksums
  and version gaps fail.
- Listener offset advances only after DB work returns successfully. Bounded
  recovery publishes poison records to a same-partition DLT.
- Production broker replication, SASL/TLS, retention, and alert ownership are
  operator decisions; local single-node evidence is not a production topology.

## Primary sources

- Apache Kafka 4.3.1 release and official image:
  https://kafka.apache.org/community/downloads/
- Apache Kafka Docker/KRaft quickstart:
  https://kafka.apache.org/43/getting-started/docker/
- Kafka producer idempotence requirements:
  https://kafka.apache.org/43/configuration/producer-configs/
- Spring Boot Kafka configuration:
  https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- Spring Kafka manual/record acknowledgment semantics:
  https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/ooo-commits.html
- Spring Kafka retry/DLT behavior:
  https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Spring Kafka KRaft testing:
  https://docs.spring.io/spring-kafka/reference/testing.html

## Risks

- Producer confirmation can be lost after broker acceptance. Consumer dedupe,
  not producer-session idempotence, closes this application retry gap.
- First observed aggregate version may be greater than zero after deployment.
  Treat it as a recorded baseline; require consecutive versions thereafter.
- DLT keeps the main partition moving but needs operations visibility before
  alerting can be called complete.

## Unresolved questions

- Production Kafka provider, broker count, credentials, retention, and on-call
  ownership.
