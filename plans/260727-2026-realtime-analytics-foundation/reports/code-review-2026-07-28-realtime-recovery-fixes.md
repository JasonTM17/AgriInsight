# Realtime recovery compatibility review — 2026-07-28

## Scope

- `cc44c00`: normalize writer timestamps to PostgreSQL microsecond precision.
- `79107ae`: bound Kafka producer metadata waits and normalize publisher failures.
- `90131d2`: accept already-persisted legacy payload timestamps at PostgreSQL precision.

## Result

No unresolved Critical or High source finding. The changes preserve at-least-once delivery, existing tenant/RLS boundaries, and the strict v1 envelope contract while preventing recovery loops caused by storage precision or an unbounded metadata wait.

## Verified behavior

| Boundary | Review evidence |
|---|---|
| New outbox write | `PostgresOutboxWriter` emits the same normalized `occurred_at` instant to the PostgreSQL row and JSON envelope. |
| Existing queued event | `OperationalEventRecord` accepts only exact, PostgreSQL-truncated, or PostgreSQL-rounded timestamp equivalents; a materially different instant still fails closed. |
| Timestamp overflow | The rounding helper catches `DateTimeException`; malformed/extreme values do not become an unhandled listener failure. |
| Broker metadata outage | `max.block.ms` is bound to the configured send timeout, so `KafkaTemplate.send` cannot hide a longer metadata wait before a future is returned. |
| Error classification | Only broker-send runtime failures are normalized to `IllegalStateException`; envelope parsing remains outside that boundary and preserves validation failure semantics. |
| Authorization and tenancy | No route, permission, RLS grant, role, or tenant transaction behavior changed. |

## Adversarial cases checked

1. A payload created before timestamp normalization retained nanoseconds while PostgreSQL rounded to microseconds. The legacy-equivalence test accepts the matching rounded instant and rejects a different microsecond.
2. Kafka lacks topic metadata. The producer now returns within the configured bound instead of blocking for the client default metadata timeout.
3. A malformed payload reaches the consumer. Strict parser validation still rejects it rather than relabeling it as a broker fault.
4. A timestamp near the representable limit is rounded. Overflow is contained and produces a normal mismatch instead of escaping the validation boundary.

## Validation

- Focused Maven regression: `OperationalEventRecordTest`, `PostgresOutboxWriterTest`, `KafkaOperationalEventPublisherTest`, and `RealtimeKafkaWorkerConfigurationTest` — 8 tests passed, 0 failures/errors/skips.
- `git diff --check` passed before the source commit.
- Hosted full workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) passed the real PostgreSQL/Kafka E2E gate after this compatibility follow-up.

## Follow-up

- Full CI for `90131d2` passed in workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699); the latest main snapshot is internally accepted.
- The existing development-tool dependency advisory follow-up remains separate; no production dependency graph finding was introduced by these changes.

## Unresolved questions

- None in the recovery compatibility source. Production Kafka topology, protected release approval, and Docker Hub publication remain owner-gated.
