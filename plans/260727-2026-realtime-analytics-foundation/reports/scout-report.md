---
type: scout
date: 2026-07-27
---

# Scout report: Realtime foundation

## Summary

The repo has a complete producer-side outbox boundary but zero broker or
consumer implementation. Realtime should extend the Java backend and add a new
top-level `realtime` module; Python artifacts remain read-only and unchanged.

## Relevant files

- `backend/src/main/java/com/agriinsight/backend/integration/application/OutboxDrainService.java`
  - validates owner/batch/lease/error and computes bounded backoff.
- `backend/src/main/java/com/agriinsight/backend/integration/infrastructure/PostgresOutboxStore.java`
  - claims ordered rows and fences ack/fail.
- `backend/src/main/java/com/agriinsight/backend/integration/infrastructure/PostgresOutboxWriter.java`
  - serializes the exact event envelope before transaction commit.
- `backend/src/main/resources/contracts/agriinsight-operational-events-v1.schema.json`
  - canonical value contract.
- `backend/src/main/resources/db/migration/V18__create_outbox_tables.sql`
  and `V19__add_outbox_rls_and_indexes.sql` - durable handoff and integration RLS.
- `backend/ops/postgres/bootstrap-roles.sql` - NOLOGIN integration role and
  membership deny gate.
- `backend/src/main/resources/db/migration/R__tenant_rls_helpers_and_grants.sql`
  - current runtime/integration column grants.
- `backend/src/main/java/com/agriinsight/backend/authorization/domain/Permission.java`
  and `Role.java` - 19-permission fixed catalog; no realtime permission.
- `backend/src/main/resources/application.yml` - schema readiness 19 and no
  Kafka properties.
- `compose.backend.yaml` - loopback PostgreSQL/migrate/backend; no broker/worker.
- `.github/workflows/ci.yml` - existing Java/Python/web/security/browser/image
  gates; hosted storage is the safe integration location.

## Contract consumers

- `CommandExecutionService` is the only publisher of `CommandCommittedEvent`.
- `PostgresOutboxWriter` is the only current `OutboxWriter` implementation.
- `OutboxDrainServiceTest` and `OutboxLeaseIntegrationTest` protect the drain
  contract and must stay green.
- Adding one API route changes the deterministic backend OpenAPI artifact and
  the generated TypeScript schema under `web/src/server/generated/backend/`.

## Constraints

- V1-V19 are immutable; new storage starts at V20.
- C drive is below the 8 GiB heavy-work floor. Broker/Docker/browser evidence
  must run hosted; Maven repo/temp remains on D.
- External registry, license, OIDC, and production recovery approvals remain
  unrelated owner gates.

## Unresolved questions

- None for implementation. Production Kafka operations remain a documented
  deployment input.
