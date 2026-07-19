---
phase: 3
title: "Tenant RBAC and PostgreSQL RLS"
status: pending
priority: P1
effort: "3-4d"
dependencies: [1, 2]
---

# Phase 3: Tenant RBAC and PostgreSQL RLS

## Overview

Implement the defense-in-depth authorization core: effective tenant permissions, transaction-local tenant context, centralized idempotency, durable tenant audit, and PostgreSQL RLS policies that default to no rows. This phase defines extension ports for farm/warehouse/activity scope, but FK-backed assignments are created only beside their parent tables in phases 4 and 5.

## Requirements

- Functional: enrich the bootstrap principal with tenant-scoped roles/permissions; provision/deactivate tenant users and external identities; support tenant role grant/revoke; define fail-closed scope extension contracts; and enforce permission checks at service methods and repository queries.
- Security: every user-facing transaction has a tenant context; missing context denies; runtime DB role cannot bypass RLS; `USING` and `WITH CHECK` prevent cross-tenant reads/writes.
- Reliability: pooled JDBC connections cannot retain a prior request's tenant setting; assignment changes take effect without waiting for a token refresh.

## Architecture

```text
verified principal -> least-privilege identity bootstrap -> tenant id
                   -> tenant context -> active profile -> roles/permissions
                   -> ScopeResolver (tenant now; farm/warehouse/activity extensions later)
                   -> @TenantScoped transaction
                      set_config('app.tenant_id', id, true)
                   -> app predicates + PostgreSQL FORCE RLS
```

RLS is a tenant backstop, not the sole business authorization mechanism. Farm manager, inventory manager, and field-worker assignments remain explicit application predicates added only when their FK targets exist. Use a separate migration owner and a restricted runtime role; never run the application as a table owner, superuser, or `BYPASSRLS` role.

## Related Code Files

- Modify: `D:\AgriInsight\backend\pom.xml` (AOP/transaction/test dependencies if required)
- Modify: `D:\AgriInsight\backend\src\main\resources\application.yml`, `D:\AgriInsight\backend\src\test\resources\application-test.yml` (runtime-only app credential, separate migration command settings)
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\api\CurrentUserController.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\ExternalIdentityService.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\PrincipalMapper.java`
- Modify: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\IdentitySecurityConfig.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\TenantPrincipalLoader.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\application\PermissionEvaluator.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\application\ScopeResolver.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\domain\ScopeContext.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\infrastructure\TenantScoped.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\infrastructure\TenantTransactionAspect.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\infrastructure\AuthorizationAuditPublisher.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\api\TenantRoleAssignmentController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\api\AuthorizationRouteAuthorization.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\application\TenantRoleAssignmentService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\api\TenantUserController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\TenantUserService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\application\CommandExecutionService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\shared\domain\ApiCommandRecord.java`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V4__add_tenant_security_and_idempotency.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\R__tenant_rls_helpers_and_grants.sql`
- Create: `D:\AgriInsight\backend\ops\postgres\bootstrap-roles.sql` (migration/runtime/identity-definer role template with placeholders only; no credentials; applied before Flyway)
- Create: `D:\AgriInsight\backend\ops\postgres\adopt-schema-ownership.sql` (upgrade-only, allowlisted V1-V3 object ownership transition; refuses unexpected owners/objects)
- Create: `D:\AgriInsight\backend\ops\postgres\provision-tenant-admin.sql` (parameterized operator-only command per new tenant; no identity data or credentials committed)
- Create: `D:\AgriInsight\scripts\run-backend-migrations.ps1` (separate Flyway owner command; never starts the app as owner)
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\authorization\ScopeResolverTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\authorization\TenantRlsIntegrationTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\identity\TenantUserLifecycleTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\tenancy\FreshDatabaseBootstrapTests.java`
- Create: `D:\AgriInsight\backend\src\test\resources\sql\rls-fixtures.sql`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — authorization matrix:** encode the role/permission table before implementation. Test executive tenant read, data-analyst allowed analytics read, tenant-admin role grant/revoke, supplier finance denial, and fail-closed behavior for a farm/warehouse/activity scope type that has no installed domain resolver. Phases 4 and 5 own the concrete assignment matrices after their parent tables exist.
2. **Red — RLS leakage tests:** against a real PostgreSQL container, create tenant A/B fixtures and assert that a missing `app.tenant_id`, tenant A context, and tenant B context return/modify only permitted rows. Assert `WITH CHECK` rejects an insert/update whose tenant differs from the context.
3. **Green — tenant users/RBAC/command records:** register exact tenant-user and role-assignment route/permission entries. Implement tenant-admin-only profile create/deactivate, exact external identity link/unlink, and role grant/revoke with optimistic version checks and durable audit. No JIT/self-registration from JWT claims is allowed; a user cannot disable/remove the last active tenant admin. Add one tenant-scoped `api_command_records` table/service for every business mutation, with a generated UUID and unique external binding over tenant, principal, method, route template, and SHA-256 idempotency-key digest. Store canonical schema/hash version, command hash, state, status, target resource/version, and no response body. The hash covers normalized path/query/body plus `If-Match`/other contract headers. Same-hash concurrent requests converge on one committed command; a different hash is 409; rollback frees the key for retry; response-loss replay reconstructs a currently authorized DTO. Records are fixed-size/durable with no MVP purge. Do not create farm/warehouse assignment rows without real FK targets.
4. **Green — authentication ordering and tenant transactions:** after JWT cryptographic/claim validation, `TenantPrincipalLoader` performs the minimum bootstrap lookup, opens a short transaction, sets tenant context, loads the active full profile/tenant code/roles/permissions, closes that transaction, and returns an enriched `Authentication` before Spring's `AuthorizationFilter` evaluates the route registry. Modify the phase-2 principal mapper/security config and `/me` flow accordingly. Each business service then uses an ordered `@TenantScoped` boundary whose outer advice owns a new `TransactionTemplate` (or an equivalently proven transaction advisor order), reapplies `set_config('app.tenant_id', ?, true)` on the transaction-bound data source before any JPA/repository query, invokes the service, and closes at transaction end. Never hold the authentication transaction across the HTTP request. Tests assert filter order, query order, and same-connection behavior for both transactions. Missing tenant is a typed failure. The user-facing runtime has no system-scope fallback; Flyway uses the separate migration command, and phase 7 designs a separate integration role for outbox work.
5. **Green — SQL primitives:** define a null-safe `app_current_tenant_id()` helper that validates the setting before UUID cast; create the tenant audit/idempotency tables; enable and force RLS on existing tenant-owned tables; and add one reviewed `AS PERMISSIVE` tenant-equality policy per command/table with `USING (tenant_id = app_current_tenant_id())` and matching `WITH CHECK`. Do not create a lone `AS RESTRICTIVE` policy, which has no permissive row source. Re-own the pre-context `SECURITY DEFINER` resolver to a dedicated `NOLOGIN`, non-superuser, non-`BYPASSRLS` role created before Flyway; give that role only the required identity/profile/tenant columns plus explicit SELECT policies, pin the function search path, schema-qualify SQL, revoke `PUBLIC`, and grant runtime only `EXECUTE`. The definer role is never granted to runtime. The repeatable migration owns only safe helper/grant definitions/ownership; table policies remain immutable versioned migrations in their owning phase. Runtime role grants only required execute/table/sequence privileges.
6. **Green — application predicates:** every repository method accepts a `ScopeContext` or is package-private behind a scoped service. Unknown/uninstalled scope resolvers deny. Later farm/warehouse/activity resolvers must join FK-backed assignments in SQL, never filter after loading. Use 404 for resources hidden by policy where existence must not leak.
7. **Green — audit:** record tenant role grants/revocations, tenant-resolved denied decisions, and idempotency conflicts with actor, tenant, target, reason, correlation id, and outcome. Pre-tenant authentication failures remain redacted structured logs. Redact identifiers not needed for incident response.
8. **Green — tenant provisioning/migrations:** make `bootstrap-roles.sql` idempotently create or verify the expected migration/runtime/identity-definer roles, exact attributes, and forbidden memberships; unsafe pre-existing attributes fail with an actionable error instead of being silently weakened. The disk-guarded migration wrapper first runs that role gate with a narrowly held operator/`CREATEROLE` credential. For an upgrade database created during phases 1-2, it then opens a separate, explicitly supplied adoption connection as the verified legacy schema owner (or a role permitted to `SET ROLE` to that owner); `CREATEROLE` alone is never assumed to permit object transfer. The upgrade-only adoption script inventories the dedicated AgriInsight schema and transfers only the checked-in V1-V3 table/sequence/function/schema-history allowlist to the migration owner; it refuses a shared database, unexpected object/owner, partial ownership, or broad catalog-driven `REASSIGN OWNED`. Fresh databases skip adoption because the migration owner creates every object. The wrapper closes both privileged connections, applies Flyway with the separate migration owner, and exits. Neither privileged credential reaches Flyway/runtime. The normal app runs with runtime credentials and Flyway disabled. Add a transaction/advisory-lock-protected operator command that accepts a new tenant code/name plus exact issuer/subject at execution time, creates that tenant's first active profile/external identity/TENANT_ADMIN grant and audit event atomically, and refuses an existing tenant code or linked identity. It can provision tenant A then tenant B but cannot elevate users inside an existing tenant. Test fresh/upgrade role bootstrap, allowlisted legacy-owner adoption, insufficient-adoption-privilege failure, unexpected-owner/object refusal, unsafe-role failure, concurrent duplicate provisioning (one success), safe rerun failure, and fresh-database usability. No values are checked in. Subsequent profiles are created through the tenant-admin API.
9. **Refactor:** run Spring Modulith verification, add query-count tests for authorization/user lists, document the transaction ordering invariant, and make RLS SQL idempotent/reviewable.

## Data and security contracts

- Tenant administration route families are the exact method/templates in `authorization-matrix.md`, including `/api/v1/users`, `/api/v1/users/{id}/external-identities`, and `/api/v1/users/{id}/roles`; all require exact route-registry permissions, tenant context, optimistic version/idempotency where applicable, and durable audit. There is no HTTP first-admin or JIT-provisioning route.
- Profile deactivation, identity unlink, and admin-role revoke lock the tenant's active-admin set and cannot remove the final active admin authentication path. The invariant is enforced transactionally, not by a count checked outside the write lock.
- `tenant_id` is mandatory on every later operational table, even where it is derivable from a parent. Composite foreign keys should prevent a child from pointing to a parent in another tenant.
- Farm/activity assignments are created in phase 4 and warehouse assignments in phase 5, each with composite tenant FKs. A generic `(scope_type, scope_id)` table without referential integrity is prohibited.
- `app_current_tenant_id()` returns NULL when the setting is absent/invalid; policies then deny rather than raising an information-leaking error.
- Use transaction-local (`is_local=true`) settings only. Session-level `SET` is prohibited because JDBC pools reuse connections.
- Tenant-scoped services do not open `REQUIRES_NEW` or switch data sources unless the new boundary explicitly reapplies and tests the tenant context; async work never inherits request scope implicitly.
- Route authorization always sees the enriched authentication created before `AuthorizationFilter`; service transactions independently reapply the same DB-verified tenant. A request cannot supply or override that tenant through a header, path, or JWT tenant claim.
- Runtime DB user is not table owner, superuser, or `BYPASSRLS`; migration owner is separate. The role template contains no password and is applied by deployment automation later.
- Spring Flyway and JPA data access use distinct configured credentials; production startup fails closed if both resolve to the same role. Tests inspect `current_user` on each path.
- Production application startup has Flyway disabled and receives no migration-owner credential. Migrations are a separate, auditable command/job that completes before runtime starts; local/test profiles may automate both roles but still connect as distinct users.
- Cluster-role bootstrap runs before every fresh migration, upgrade, and restore because PostgreSQL database dumps do not create cluster-global roles. The operator/`CREATEROLE` credential is never passed to Flyway or runtime and is discarded after the gate.
- Ownership adoption exists only for the controlled phase-1/2 upgrade path. It enumerates the expected V1-V3 objects, records before/after owners, and fails closed; no production script may reassign every object owned by a broad/shared database role.
- Identity bootstrap is the only pre-tenant exception: a hardened function executes as its dedicated `NOLOGIN` definer, whose explicit RLS SELECT policies apply only to the minimum bootstrap columns. Before `app.tenant_id` is set, runtime direct reads return no rows; `external_identities` discovery is function-only in every context. After tenant context is set, runtime receives RLS-constrained SELECT only on the tenant/profile/role/permission tables required to enrich the principal—never unrestricted identity discovery. Runtime cannot assume the definer role. Tests inspect pre/post-context behavior, membership, ownership, grants, policies, and function metadata.
- Flyway migrations apply tenant seeds before enabling FORCE RLS or set an explicit transaction-local tenant for tenant data. Future migrations never disable RLS to insert data, and fresh/app-upgrade paths are both tested.
- Avoid multiple permissive policies for the same role/command whose PostgreSQL `OR` combination widens access. Use one tenant-equality permissive policy per command/table for the runtime role; any bootstrap/migration policy targets a distinct role that runtime cannot inherit. Policy catalog tests assert command, role, membership, mode, `USING`, and `WITH CHECK` definitions.
- Application-level “tenant admin” is not a PostgreSQL superuser. Administrative code still passes through explicit system/tenant scope and audit.
- Scope IDs are UUIDs from the database, not Python `*_key` values or user-supplied SQL fragments.
- Deactivated master codes remain reserved; RLS and uniqueness tests cover deactivate/restore conflicts.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -Dtest='*Scope*Test,*Rls*Test,*Authorization*Test' test`
- Testcontainers PostgreSQL direct SQL test with separate runtime role and pooled connection reuse.
- Flyway `validate` after applying all current migrations; inspect policies, grants, indexes, and role attributes.
- `git diff --check` and a secret scan over `backend/ops/postgres`.

## Success Criteria

- [ ] Every user-facing domain transaction establishes exactly one tenant context or fails closed.
- [ ] JWT validation -> bootstrap -> tenant-scoped principal load -> route authorization ordering is proven; no permission check runs against the phase-2 minimum principal.
- [ ] Cross-tenant reads and writes are blocked both by application tests and direct SQL/RLS tests.
- [ ] Connection pooling cannot leak tenant A's context to tenant B.
- [ ] Identity bootstrap resolves only the verified issuer/subject before RLS and cannot enumerate full identity rows.
- [ ] Tenant role rules match the role matrix; no client can self-grant a role, and unknown domain scope types deny until phases 4/5 install FK-backed resolvers.
- [ ] A fresh database can be migrated and operator-provisioned into a usable first tenant; a second enterprise can be provisioned safely, while duplicate/concurrent tenant or identity provisioning fails without partial rows.
- [ ] Tenant admins can provision/deactivate profiles and link exact issuer/subject identities, but cannot remove the last active tenant admin.
- [ ] Runtime role lacks owner/superuser/BYPASSRLS privileges; migration role is not used by the app.
- [ ] A phase-1/2 database upgrades through the explicit V1-V3 ownership allowlist, while unexpected/shared ownership fails before Flyway or RLS changes.
- [ ] RLS policies include both read (`USING`) and write (`WITH CHECK`) behavior and future-table guidance.
- [ ] Fresh install and upgrade migrations succeed with FORCE RLS left enabled; no applied migration is edited or policy temporarily disabled.
- [ ] No later phase is allowed to add a tenant-owned table without a policy and cross-tenant test.
- [ ] Idempotency replay/hash-conflict behavior is tenant/principal/route bound and safe under concurrent duplicate requests.
- [ ] Tests cover same key/same hash concurrency, same key/different hash, changed `If-Match`/path with reused key, first transaction rollback, response lost after commit, canonical-hash version stability, no raw key/body snapshot, and no duplicate-enabling purge.

## Risk Assessment

- Aspect ordering can set context too late: test SQL call order and forbid direct repository access from controllers; fail CI on a scope-less service.
- RLS owner/definer mistakes can make local tests falsely pass: integration tests connect as the restricted runtime role and explicitly inspect `pg_roles`, memberships, function ownership/search path, policies, and grants.
- Policy joins can become slow: keep tenant equality on each table, add composite indexes beginning with `tenant_id`, and benchmark representative list queries.
- System jobs can overreach: the user-facing runtime has no bypass path; later jobs require a separate least-privilege DB role/data source and audit, never a magic tenant UUID.

## Rollback

Disable new assignment routes and revoke runtime grants before changing policies. Apply forward SQL to correct a policy; do not edit an applied migration or disable RLS as a “temporary” fix. Existing Python services are independent and remain available.
