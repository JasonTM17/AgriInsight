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

Migrations hiện tại là V1–V21; repeatable grants chạy sau các versioned migration. Mọi migration mới phải tăng version, giữ `ENABLE/FORCE ROW LEVEL SECURITY`, cập nhật readiness/schema tests và chạy fresh + controlled-upgrade integration tests.

## Transactional outbox

Generic command execution phát một typed command-commit event sau khi command record hoàn tất. PostgreSQL outbox writer nhận event ở `BEFORE_COMMIT`, serialize allowlisted envelope và insert `outbox_events` trong cùng transaction. Rollback domain transaction thì event không tồn tại.

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

Outbox drain path là internal port, không phải HTTP route. Khi `AGRIINSIGHT_REALTIME_PUBLISHER_ENABLED=true`, một opt-in worker có thể schedule publish ra Kafka với `acks=all`, producer idempotence và delivery at-least-once. Drain path dùng `FOR UPDATE SKIP LOCKED`, batch ≤100, lease ≤15 phút, owner/token/generation fencing, predecessor gating theo aggregate version, exponential retry backoff capped 15 phút và terminal `DEAD_LETTER` sau `max_attempts`. Một predecessor đã dead-letter sẽ chặn version sau của cùng aggregate. Stale ack/fail trả về false/`STALE`.

Realtime gate source coverage hiện gồm authenticated MockMvc summary-route coverage, tenant-scoped RLS/privilege coverage, và Kafka E2E coverage cho outbox publish, dead-letter, và poison-record paths sau `scripts/run-realtime-e2e-tests.ps1`. E2E lấy 20 accepted samples từ durable outbox append đến authenticated tenant summary, assert per-run `p95 <= 30s`, và log `freshness_p95_millis`. Runner có local và `-HostedCi` mode; hosted mode yêu cầu GitHub-hosted Linux markers (`GITHUB_ACTIONS=true`, `RUNNER_ENVIRONMENT=github-hosted`, `RUNNER_OS=Linux`, `CI=true`) cùng `RUNNER_TEMP`. Hosted workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) passed với `freshness_p95_millis=130`, `recovery_millis=5094` và 20 samples; đó là technical acceptance nội bộ, không phải production SLA.

## Role matrix

| Role | Login | Purpose | RLS / privilege boundary |
|---|---:|---|---|
| `agriinsight_migrator` | yes | Flyway and restore owner | schema owner/migration grants; separate secret |
| `agriinsight_runtime` | yes | API requests | no owner, superuser or `BYPASSRLS`; tenant transaction context required |
| `agriinsight_identity_definer` | no | tightly-scoped identity lookup | set only by migrator with `INHERIT FALSE` |
| `agriinsight_integration` | no | outbox/realtime integration grant set | cross-tenant outbox lease/read plus explicit realtime read-model writes; no login credential |
| `agriinsight_realtime` | yes | opt-in local publisher/consumer worker | inherits `agriinsight_integration`; no owner, superuser or `BYPASSRLS`; dedicated password required |

The bootstrap script is idempotent and fails if role attributes or memberships drift. Runtime receives only explicit outbox INSERT columns and read-only realtime summary access through tenant scope; integration/realtime receive only the explicit claim/ack and read-model write paths granted by SQL. Publisher/consumer remain disabled by default in `application.yml`; the local Compose worker enables both under the dedicated realtime login.

The realtime summary route is authenticated and tenant-scoped: `/api/v1/realtime/summary` requires `REALTIME_READ`, and the schema tests prove the runtime role can read only its tenant summary while the realtime role can write the read models under the fenced policies.

## Code ownership rules

- API/application services own command validation, authorization, optimistic version and idempotency.
- `shared.application` owns the generic command event only; domain-specific enrichers must add a new schema version when semantics change.
- `integration` owns outbox persistence and drain fencing; it must not mutate Gold or invent a broker.
- Tests that need a PostgreSQL role use Testcontainers and the dedicated `agriinsight_integration` role; unit tests use the three-argument command-service constructor with a no-op event publisher.
