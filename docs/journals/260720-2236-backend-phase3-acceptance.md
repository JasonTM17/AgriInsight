# Backend Phase 3: the audit transaction cannot compete with the transaction it is auditing

Date: 2026-07-20

## What happened

Phase 3 grew from a permission-enrichment task into the full tenant security boundary: restricted PostgreSQL roles, transaction-local context, FORCE RLS, first-admin provisioning, tenant user/identity/role administration, canonical idempotency, and durable audit. The final gate reached 134 unit/security/module tests plus 23 real PostgreSQL/Flyway integration tests.

The frustrating failure arrived near the end. A denied business operation still held the only Hikari connection while the audit recorder tried to open `REQUIRES_NEW`. With pool size one, the recorder waited for a connection that could not be released until the recorder returned, ending in `CannotCreateTransactionException`. A larger pool made the symptom disappear but left the ordering bug alive.

A second failure looked like a regression but was test coupling: the lifecycle audit assertion expected six rows and found seven because a new denial case had correctly added another event. Counting the whole table made test order part of the contract.

## Root cause

The first audit design treated transaction propagation as isolation without accounting for connection ownership. `REQUIRES_NEW` suspends the outer transaction but does not release its physical connection. The design therefore required at least two connections for a denial path and could deadlock under legitimate pool pressure.

The audit-count test had a separate cause: it asserted global database state instead of the lifecycle action set owned by the scenario.

## Decision

A typed authorization-denial exception now carries only redacted decision metadata. `TenantTransactionAspect` lets the failed business transaction roll back and release its connection first, then opens the independent audit transaction. The route handler keeps a defensive fallback for denials that occur outside that aspect. Audit persistence failure is logged by error type and never changes the generic 403 response.

The lifecycle test now filters its own action codes. It still proves the exact expected event set without depending on unrelated audit rows.

Rejected shortcuts:

- Increasing Hikari minimum/maximum size: hides a resource-ordering defect and fails again under saturation.
- Making denial audit fire-and-forget: loses deterministic durability and invents an async delivery problem before Phase 7.
- Swallowing the audit failure silently: preserves 403 but destroys operational evidence.
- Clearing the audit table between tests: masks state coupling instead of asserting scenario-owned data.

## Result

- Final guarded Maven `verify`: 61 suites, 157 tests, zero failures/errors/skips.
- Flyway V1-V4 fresh and upgrade paths passed with restricted roles and FORCE RLS.
- Tenant A/B direct-SQL isolation, pooled-context cleanup, provisioning concurrency, last-admin locking, route/service denial, idempotency replay/conflict/rollback, and query-count bounds passed.
- No Testcontainers/Ryuk/PostgreSQL test container remained.
- Final documentation guard: C `20.354 GB`, D `26.732 GB`, both PASS.

## Lesson

Transaction labels do not prove resource independence. When an error path starts a second transaction, test it with the smallest legal connection pool and verify exactly when the first connection is released. Audit assertions should identify events by domain action and correlation, never by a global row count.

## Next steps

1. Start Phase 4 with farm/season schema, composite tenant foreign keys, FORCE RLS, and direct-SQL isolation tests.
2. Reuse the Phase 3 command and audit boundary for every Phase 4 mutation.
3. Add production alerting and retention for audit-recorder failures in Phase 7.
4. Keep Docker Hub publication behind protected scan/SBOM/provenance and exact pushed-digest smoke gates.

## Unresolved Questions

- Production OIDC token/MFA contract.
- Tenant audit retention, compliance, and on-call ownership.
- Docker Hub namespace and protected release credentials.
- Production backup RPO/RTO and restore owner.
