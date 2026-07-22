# Backend development

Backend là Java 21/Spring Boot 4 modular monolith trong `backend/`. Analytics Python sở hữu `artifacts/`; backend không được ghi Gold, SQLite, manifest hoặc đường dẫn runtime của pipeline.

## Local gates

Chạy từ repository root trên Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

Guarded runner giữ Maven repository, Java temp và user home trên D, từ chối hidden Maven flags có thể bỏ qua test, và yêu cầu Docker daemon cho `verify`. Không stage `.env`, token, database password hoặc `artifacts/`.

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

Migrations hiện tại là V1–V19; repeatable grants chạy sau V19. Mọi migration mới phải tăng version, giữ `ENABLE/FORCE ROW LEVEL SECURITY`, cập nhật readiness test và chạy fresh + upgrade-from-phase-6 integration tests.

## Transactional outbox

`CommandExecutionService` phát một typed `CommandCommittedEvent` sau khi command record hoàn tất. `PostgresOutboxWriter` nhận event ở `BEFORE_COMMIT`, serialize allowlisted envelope và insert `outbox_events` trong cùng transaction. Rollback domain transaction thì event không tồn tại.

Envelope v1 được kiểm soát bởi [`agriinsight-operational-events-v1.schema.json`](../backend/src/main/resources/contracts/agriinsight-operational-events-v1.schema.json):

| Field | Contract |
|---|---|
| `event_id`, `tenant_id`, `command_id`, `aggregate_id` | UUID strings |
| `event_ordinal`, `aggregate_version`, `schema_version` | non-negative integers; schema version = 1 |
| `aggregate`, `event_type` | canonical uppercase resource/type |
| `business_code` | string or null; generic command events currently use null |
| `occurred_at` | UTC RFC 3339 metadata; never the ordering key |
| `payload` | allowlisted object; no bearer token, password, private path or raw provider response |

Delivery is at-least-once. Producer idempotency is enforced by `(tenant_id, command_id, event_ordinal)`; consumers deduplicate by `event_id`.

`OutboxDrainService` is an internal port, not an HTTP route or scheduler. It uses `FOR UPDATE SKIP LOCKED`, batch ≤100, lease ≤15 minutes, owner/token/generation fencing, predecessor gating per aggregate version, exponential retry backoff capped at 15 minutes, and terminal `DEAD_LETTER` after `max_attempts`. A dead-lettered predecessor blocks later aggregate versions. Stale ack/fail returns false/`STALE`.

## Role matrix

| Role | Login | Purpose | RLS / privilege boundary |
|---|---:|---|---|
| `agriinsight_migrator` | yes | Flyway and restore owner | schema owner/migration grants; separate secret |
| `agriinsight_runtime` | yes | API requests | no owner, superuser or `BYPASSRLS`; tenant transaction context required |
| `agriinsight_identity_definer` | no | tightly-scoped identity lookup | set only by migrator with `INHERIT FALSE` |
| `agriinsight_integration` | no | future outbox adapter | cross-tenant read + lease-column update only; no login credential |

The bootstrap script is idempotent and fails if role attributes or memberships drift. Runtime receives only explicit outbox INSERT columns; integration receives explicit claim/ack columns. No production adapter is enabled by this phase.

## Code ownership rules

- API/application services own command validation, authorization, optimistic version and idempotency.
- `shared.application` owns the generic command event only; domain-specific enrichers must add a new schema version when semantics change.
- `integration` owns outbox persistence and drain fencing; it must not mutate Gold or invent a broker.
- Tests that need a PostgreSQL role use Testcontainers and the dedicated `agriinsight_integration` role; unit tests use the three-argument command-service constructor with a no-op event publisher.
