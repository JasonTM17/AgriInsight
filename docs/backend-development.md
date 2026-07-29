# Backend development

Backend is the Java 21/Spring Boot 4 modular monolith in `backend/`. Analytics Python owns `artifacts/`; backend must not write Gold, SQLite, manifest, or pipeline runtime paths.

## Local gates

Run from repository root on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

The guarded runner keeps Maven repository, Java temp, and user home on D, rejects hidden Maven flags that can bypass tests, and requires Docker for `verify`. Do not stage `.env`, tokens, database passwords, or `artifacts/`.

Focused outbox tests:

```powershell
$env:TEMP='D:\AgriInsight\tmp\java'
$env:TMP=$env:TEMP
$env:MAVEN_OPTS='-Xmx384m -Djava.io.tmpdir=D:\AgriInsight\tmp\java'
Push-Location backend
try {
  .\mvnw.cmd '-Dmaven.repo.local=D:\AgriInsight\artifacts\_tmp\m2-repository' `
    '-Dtest=OutboxAtomicityIntegrationTest,OutboxLeaseIntegrationTest,OutboxDrainServiceTest' test
} finally { Pop-Location }
```

`V22` is immutable. The alert-worker hardening is V23-V30 with expected schema
version 30, but the worker startup gate independently pins successful V28 and
the latest repeatable grant execution; `AGRIINSIGHT_SCHEMA_EXPECTED_VERSION`
remains backend readiness only. V23 is additive and keeps its source/evidence
checks `NOT VALID`; a bounded idempotent operator backfill is required before
worker enablement. V24-V27 are one concurrent index each and have explicit
invalid-index recovery preconditions; V27 is a readiness-only partial index for
invalid source-evidence rows. Transactional V28 repairs the acknowledgement
function through its named unique constraint without rewriting V22, V29 locks
acknowledgement to open alerts only, and V30 adds the concurrent latest-open
feed index. Repeatable
grants run after versioned migration. Every new migration must keep
`ENABLE/FORCE ROW LEVEL SECURITY`, update readiness/schema tests, and run fresh
plus controlled-upgrade integration tests. Follow the pre-enable and recovery
procedure in
[deployment guide](deployment-guide.md#alert-worker-pre-enable-and-concurrent-index-recovery).

## Transactional outbox

Generic command execution emits a typed command-commit event after the command record completes. The PostgreSQL outbox writer receives the event in `BEFORE_COMMIT`, serializes an allowlisted envelope, and inserts `outbox_events` in the same transaction. Rolling back the domain transaction drops the event.

Envelope v1 is controlled by [`agriinsight-operational-events-v1.schema.json`](../backend/src/main/resources/contracts/agriinsight-operational-events-v1.schema.json):

| Field | Contract |
|---|---|
| `event_id`, `tenant_id`, `command_id`, `aggregate_id` | UUID strings |
| `event_ordinal`, `aggregate_version`, `schema_version` | non-negative integers; schema version = 1 |
| `aggregate`, `event_type` | canonical uppercase resource/type |
| `business_code` | string or null; generic command events currently use null |
| `occurred_at` | UTC RFC 3339 metadata; never the ordering key |
| `payload` | allowlisted object; no bearer token, password, private path, or raw provider response |

Delivery is at-least-once. Producer idempotency is enforced by `(tenant_id, command_id, event_ordinal)`; consumers deduplicate by `event_id`.

Outbox drain remains an internal port, not an HTTP route. When `AGRIINSIGHT_REALTIME_PUBLISHER_ENABLED=true`, the opt-in worker schedules Kafka publish with `acks=all`, producer idempotence, and at-least-once delivery. The drain path uses `FOR UPDATE SKIP LOCKED`, batch ≤100, lease ≤15 minutes, owner/token/generation fencing, predecessor gating by aggregate version, exponential retry backoff capped at 15 minutes, and terminal `DEAD_LETTER` after `max_attempts`. A dead-lettered predecessor blocks later versions for the same aggregate. Stale ack/fail returns false/`STALE`.

The isolated alert worker is separate. It runs under the `realtime-worker`
profile with `spring.main.web-application-type=none` and the
`agriinsight_alert_worker` login. The application profile receives
`AGRIINSIGHT_ALERT_WORKER_DB_PASSWORD`; local Compose requires
`AGRIINSIGHT_DB_ALERT_WORKER_PASSWORD` and maps it into that service. Only the
`realtime-alert-worker` service disables legacy publisher/consumer behavior.
The existing `realtime-worker` service remains the separate
`agriinsight_realtime` publisher/consumer path.

The alert worker is metadata-only: it can read only the narrow outbox, receipt,
tenant, alert, and cursor columns it needs; it has no outbox payload,
`last_error`, or business-table grant. Startup also verifies the exact
`agriinsight_alert_worker` login topology, no inherited memberships, the
named FORCE-RLS policies, and the narrow grants on the metadata-only tables.
The scanner uses a durable per-policy cursor and fair pages bounded by
`maximumCandidates=500` plus a continuation probe. Default
`maximumQueryDuration=20s` is validated with a 60-second cap. Its isolated
profile sets pgJDBC `socketTimeout=65`, exceeding that cap without loosening
the API datasource's fail-fast read timeout. Each policy runs in
`REPEATABLE_READ` behind a policy-level advisory lock, rechecks the current
condition before recovery, applies healthy-duration and clean-scan hysteresis,
and records saturation rather than expanding a scan. The distinct DLT observer
treats Kafka headers as untrusted, retains no raw value or error text, and
never republishes to the observed DLT topic; terminal observer failures emit a
fixed headerless marker to the distinct terminal topic rather than forwarding
the original key, payload, headers, or exception text. Receipt recording and
source attribution share a per-event advisory lock, so DLT handling is database
serialization, not exactly-once or broker ordering.

Realtime source coverage includes authenticated MockMvc summary-route coverage,
tenant-scoped RLS/privilege coverage, and Kafka E2E source paths after
`scripts/run-realtime-e2e-tests.ps1`. The follow-on alert-worker hardening is
merged on `main` and released in `v0.2.3`; that release is worker-only. Phase 2
feed/ack API and same-origin BFF are verified in PR `#13` / CI `30425647823`.
Phase 3's browser panel passed hosted CI `30445148252` at `e8a02a2` and is
merged through PR `#14`; the run included the real browser and candidate-image
gates without publishing a new image. The focused local gate passed 42 tests;
main CI `30413064146` and protected image publication `30413877863` passed at
commit `3e72ab5226a17d85fc42cb4f0cacb1900a416a1a`. This proves hosted PR
acceptance, not a production SLA, external deployment, or a new image release.

## Role matrix

| Role | Login | Purpose | RLS / privilege boundary |
|---|---:|---|---|
| `agriinsight_migrator` | yes | Flyway and restore owner | schema owner/migration grants; separate secret |
| `agriinsight_runtime` | yes | API requests | no owner, superuser, or `BYPASSRLS`; tenant transaction context required |
| `agriinsight_identity_definer` | no | tightly-scoped identity lookup | set only by migrator with `INHERIT FALSE` |
| `agriinsight_integration` | no | outbox/realtime integration grant set | cross-tenant outbox lease/read plus explicit realtime read-model writes; no login credential |
| `agriinsight_realtime` | yes | legacy realtime publisher/consumer worker | inherits `agriinsight_integration`; no owner, superuser, or `BYPASSRLS`; dedicated password required |
| `agriinsight_alert_worker` | yes | isolated metadata-only alert worker | no inheritance; no owner, superuser, or `BYPASSRLS`; dedicated password required; publisher/consumer disabled in this service |

The bootstrap script is idempotent and fails if role attributes or memberships drift. Runtime receives only explicit outbox INSERT columns and read-only realtime summary access through tenant scope; integration/realtime receive only the claim/ack and read-model write paths granted by SQL. The isolated alert worker has no inheritance and receives only tenant IDs, selected outbox/receipt metadata, its alert projection, and scan-cursor grants; it must never gain business-table, payload, or error-text access.

The realtime summary route is authenticated and tenant-scoped: `/api/v1/realtime/summary` requires `REALTIME_READ`, and schema tests prove the runtime role can read only its tenant summary while the realtime role can write the read models under fenced policies.

## Code ownership rules

- API/application services own command validation, authorization, optimistic versioning, and idempotency.
- `shared.application` owns the generic command event only; domain-specific enrichers must add a new schema version when semantics change.
- `integration` owns outbox persistence and drain fencing; it must not mutate Gold or invent a broker.
- The isolated alert worker owns only alert projection, cursor, and DLT observer behavior.
- Tests that need a PostgreSQL role use Testcontainers and the dedicated `agriinsight_integration` role; unit tests use the three-argument command-service constructor with a no-op event publisher.
