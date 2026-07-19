# Planner report: Backend application, authentication, and row-scoped authorization

Date: 2026-07-19
Mode: CK deep + TDD, HOLD SCOPE
Plan: `plans/260719-0753-backend-auth-rbac/plan.md`

## Scope decision

The current repository has a complete Python analytics MVP, not a backend. The next smallest useful milestone is a separate Java operational application with the requested farm/season/activity/inventory/cost REST APIs and security boundary. Kafka, Redis, microservices, realtime, warehouse migration, ML, AI, and frontend implementation remain later stages. A CK FE design brief is now queued so the web experience has a concrete direction without creating a second source of truth before the operational contract exists.

## Decisions recorded

- Java 21 source/release; Spring Boot 4.1.0; Spring Modulith 2.1.0 BOM; Maven; Spring MVC/JPA; PostgreSQL 18 line; Flyway; OpenAPI; Testcontainers.
- One modular monolith under `backend/`, with package/boundary tests and no artificial service-to-service network calls.
- External provider-neutral OIDC; Spring Resource Server validates bearer JWTs. No local passwords or self-hosted authorization server. Browser BFF/session waits for a UI phase.
- DB-backed permissions and assignments; deny-by-default HTTP + method + scope checks. Tenant is enterprise for MVP; seasons belong to fields.
- Hybrid application scope + PostgreSQL RLS. Transaction-local tenant setting, FORCE RLS, restricted runtime role, separate migration owner.
- Phase 3 owns tenant RBAC/RLS/idempotency only. Farm/activity assignments move to phase 4 and warehouse assignments to phase 5 so every assignment has a real tenant-safe FK when its migration runs.
- Fixed permissions, role grants, route methods/templates, and scope rules are normative in `authorization-matrix.md`; endpoint inventory must match it exactly.
- UUID public/internal IDs; legacy canonical business codes remain integration keys; Python surrogate keys are never shared.
- PostgreSQL is Java's operational source of truth. Python remains the only artifact/Gold/SQLite publisher. Backend runtime never writes analytics artifacts; ignored `artifacts/_tmp` remains available only to build/temp tooling. Domain change + outbox is one transaction; future consumer is a separate plan.
- Cost lenses remain separate and no unallocated COGS is introduced.
- CK FE direction is a single Next.js/React/TypeScript application after the auth/OpenAPI boundary stabilizes, with an agricultural operations aesthetic, role-aware navigation, WCAG 2.2 AA, accessible chart-table fallbacks, and restrained motion. The detailed handoff is `frontend-follow-up-brief.md`.
- Phase 7 owns verified Docker Hub publication for first-party Python/backend images. Pull requests never push; protected releases use immutable version/SHA tags, SBOM/provenance, scan policy, and pull-by-digest smoke tests. The frontend plan later adds the web image through the same contract.

## Phase dependency graph

```text
P1 foundation -> P2 identity -> P3 tenant/RLS
                              -> P4 farm/season/operations -> P6 cost
                              -> P5 inventory/procurement -> P6 cost
P4 + P5 + P6 -> P7 outbox/Compose/images/CI/docs/release
```

Migrations and shared public contracts are sequential. Only phase-local tests/docs may run in parallel after dependencies are green.

## Requirements coverage matrix

| Requirement | Phase | Test evidence |
|---|---|---|
| Farm/field/crop masters | 4 | CRUD, canonical code, tenant parent, scope and optimistic lock |
| Season lifecycle/comparison inputs | 4 | date/status/duplicate tests and bounded list contract |
| Activity/workforce/field entry | 4 | assignment matrix, status/correction, idempotency, RLS |
| Harvest/revenue facts | 4 | quantity/revenue/relationship/correction tests |
| Warehouse/material/supplier | 5 | master lifecycle, assignment and permission tests |
| Inventory movement/balance | 5 | lock/concurrency, no negative stock, ledger reconciliation |
| Cost management | 6 | ledger/reversal/scale/lens separation and finance authorization |
| REST/OpenAPI/ProblemDetail | 1-6 | MockMvc, schema/diff, validation/error tests |
| Authentication | 2 | JWT matrix and active internal identity lookup |
| Authorization/RBAC/row visibility | 2-3 plus each domain | deny/default, scope matrix, direct RLS tests |
| Python compatibility | 7 | existing pytest/compileall/Node/wheel/Compose checks |

## Scenario matrix

### Security

- Missing, malformed, expired, future, wrong issuer/audience, wrong signature JWT.
- Unknown/disabled identity; stale role claim; supplier requests finance; manager guesses another farm; worker guesses another activity.
- Missing tenant setting; tenant A/B direct SQL; runtime role owner/BYPASSRLS check; pooled connection reuse.
- SQL/path/formula-like filters; unknown JSON fields; oversized page/date range; raw token/stack trace in logs/responses.

### Data integrity

- Cross-tenant parent FK, duplicate per-tenant business code, invalid status/date/unit/amount, negative stock, unsupported category.
- Optimistic lock conflict; duplicate idempotency request; reversal of reversal; domain rollback with no outbox.
- Flyway fresh apply, checksum drift, repeatable policy convergence, forward repair documentation.

### Operations

- Docker stopped, disk guard warning/fail, missing OIDC configuration, unavailable PostgreSQL, torn artifact manifest, backend disabled while Python remains available.

## Public interface checklist

1. Versioned `/api/v1` routes, explicit auth requirements, bounded pagination, server-owned sort/filter allowlists.
2. RFC ProblemDetail-compatible errors with correlation id and no sensitive diagnostics.
3. UUID resource IDs plus canonical business codes; no Python surrogate IDs.
4. OIDC issuer/audience config and documented MFA expectation; no committed secrets.
5. Role/permission/assignment matrix documented and tested.
6. Cost responses declare lens and never combine operating/procurement/inventory value.
7. Outbox JSON Schema version, idempotency key, at-least-once semantics, and no artifact write path.

## Key risks and mitigations

| Risk | Level | Mitigation |
|---|---|---|
| RLS bypass through owner/BYPASSRLS | Critical | separate roles, FORCE RLS, restricted-role integration tests |
| Tenant context leaks through pool | Critical | transaction-local `set_config`, aspect-order tests, connection reuse test |
| Assignment migration precedes parent table | Critical | domain-owned assignment migrations in phases 4/5 with composite tenant FKs; no polymorphic orphan scope table |
| JWT claim/issuer misconfiguration | High | issuer/audience validators, fail-closed startup/config, MockJwt matrix |
| N+1/unbounded financial queries | High | projections, indexes, page/date caps, query-count/plan tests |
| Cost/procurement double count | High | distinct types/tables/lenses, no COGS claim, contract tests |
| Outbox duplicate/loss | High | same transaction, unique idempotency, leases, at-least-once consumer contract |
| Python artifact corruption | Critical | separate DB, no artifact mount write, boundary tests, manifest fence for future reads |
| Docker tag drift/token exposure | Critical | protected environment, least-privilege token, immutable version/SHA tags, pinned actions, digest smoke test, SBOM/provenance |
| C-drive exhaustion | High | disk guard, D-local Maven/temp, no pulls on warning, no destructive cleanup |

## Validation outcome expected before cook

- CK plan parser/validator passes with no warnings that hide missing phase metadata.
- Red-team finds no unresolved critical/high issue; any accepted medium issue is recorded with a phase gate.
- Whole-plan sweep finds no stale `backend\\backend` paths, conflicting auth model, or duplicate cost source. Intentional serialized ownership transitions (`pom.xml`, application config, `UserProfile.java`, and named phase-7 command services) are documented; no overlapping parallel ownership remains.
- Release plan names only first-party Docker images, keeps Docker Hub credentials external, and cannot mutate `latest` or publish from an unverified pull request/worktree.
- User approves the plan before implementation. Implementation starts with phase 1 only and commits in the small clusters defined in `plan.md`.

## Unresolved questions

- Production OIDC vendor, issuer claims, and MFA/step-up policy.
- Browser client pattern when UI work starts.
- Docker Hub namespace plus public/private repository policy for the Python, backend, and later web images.
- Audit/event retention plus PostgreSQL RPO/RTO/retention/encrypted off-host backup ownership (production release gate).
- Future Python outbox consumer schedule and schema version.
