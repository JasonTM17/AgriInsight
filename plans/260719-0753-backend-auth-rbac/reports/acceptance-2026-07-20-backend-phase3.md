# Backend Phase 3 Acceptance

- Date: 2026-07-20
- Decision: **ACCEPTED for tenant authorization and administration; NOT a production release**

## Scope

This report closes database-backed tenant roles/permissions, exact route authorization, transaction-local tenant context, restricted PostgreSQL roles with FORCE RLS, operator provisioning, tenant user/identity/role administration, canonical idempotency, durable audit, and denial recording after rollback. Farm/workforce, inventory/procurement, cost, outbox, protected release CI, and registry publication remain later phases.

## Mechanical Evidence

| Gate | Result |
|---|---|
| Final disk guard | PASS: C `20.354 GB`, D `26.732 GB` free |
| Canonical backend verify | PASS: 134 unit/security/module + 23 PostgreSQL/Flyway integration = 157 tests; 0 failures/errors/skips |
| Review range | `d14353a..99d1712`: 162 files, 10,518 insertions, 347 deletions |
| Database lifecycle | Flyway V1-V4 and repeatable helpers/grants pass fresh install, explicit V1-V3 ownership adoption, migration validation, and unsafe-role/owner refusal |
| Role boundary | Runtime is not owner, superuser, or `BYPASSRLS`; migration and identity-definer roles are separate and runtime cannot assume them |
| Tenant isolation | Missing/invalid context denies; direct SQL proves tenant A/B read/write isolation, matching `USING`/`WITH CHECK`, FORCE RLS, and clean pooled-connection reuse |
| Authorization order | Verified JWT -> exact identity bootstrap -> tenant-scoped principal enrichment -> exact route permission -> tenant business transaction |
| Tenant administration | User create/list/read/deactivate/reactivate, external identity link/unlink, and role grant/revoke pass version, permission, cross-tenant, and last-admin tests |
| Provisioning | Operator command provisions first tenant/admin/identity/role/audit atomically; second tenant works; duplicate and concurrent attempts leave no partial rows |
| Idempotency | Key digest and canonical request hash bind tenant/principal/method/route/path/query/body/contract headers; concurrency, conflict, rollback, and response-loss replay pass |
| Denial audit | Business transaction rolls back and releases its connection before the independent audit transaction; one-connection-pool regression passes and client response remains generic 403 |
| Query efficiency | Tenant user listing uses one bounded query; principal permission enrichment uses one joined query |
| Repository hygiene | `git diff --check` and focused secret/risk scan pass; no raw key, request body, token, credential, private key, or response snapshot stored |
| Runtime cleanup | No Testcontainers/Ryuk/PostgreSQL test container remains; unrelated Docker resources were not pruned |

Canonical backend command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

## Accepted HTTP Boundary

- `GET /api/v1/me`
- `GET /api/v1/users`
- `POST /api/v1/users`
- `GET /api/v1/users/{id}`
- `POST /api/v1/users/{id}/deactivate`
- `POST /api/v1/users/{id}/reactivate`
- `POST /api/v1/users/{id}/external-identities`
- `POST /api/v1/users/{id}/external-identities/{identityId}/unlink`
- `POST /api/v1/users/{id}/roles`
- `POST /api/v1/users/{id}/roles/{roleCode}/revoke`

All ten protected method/template pairs use exact registration. No public first-admin or JWT JIT-provisioning route exists.

## Review Decision

No Critical or High issue remains in Phase 3 scope. Review caught and verified fixes for a one-connection-pool denial-audit deadlock, service-layer authorization denials falling into the generic 500 handler, and order-dependent audit-count assertions. The final full gate passed after those fixes.

## Release Boundary

This acceptance does not enable a public production deployment. Identity remains disabled by default until deployment supplies the exact OIDC contract. Production also needs Phase 4-6 business APIs and Phase 7 protected CI, dependency/image scan, SBOM/provenance, immutable first-party Docker Hub publication, backup/restore drill, and operational alerting.

No image was pushed in Phase 3. `postgres:18.0-alpine` remains an upstream-only integration dependency and must not be mirrored under the AgriInsight namespace.

## Next Sequential Boundary

Implement Phase 4 farm, season, workforce, and activity APIs. Install farm/activity scope resolvers only beside their FK-backed assignment tables, preserving the Phase 3 tenant/RLS/idempotency/audit invariants.

## Unresolved Questions

- Which production OIDC provider and exact token/MFA contract will be used?
- What retention, alerting, and compliance policy applies to tenant authorization audit events?
- Which Docker Hub namespace, visibility policy, and least-privilege release token will own first-party images in Phase 7?
- What production RPO/RTO, encrypted off-host backup destination, retention, and restore owner are approved?
