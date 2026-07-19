---
phase: 2
title: "OIDC identity and security boundary"
status: pending
priority: P1
effort: "2d"
dependencies: [1]
---

# Phase 2: OIDC identity and security boundary

## Overview

Make every API request authenticated by a provider-neutral external OIDC issuer and map the verified subject to an active internal user profile. This phase owns token verification and the minimum identity bootstrap principal only; tenant-scoped permission enrichment and user administration are the phase-3 production gate, while FK-backed farm/activity and warehouse scope arrives with phases 4 and 5.

## Requirements

- Functional: validate JWT issuer, API audience, signature/JWKS, `exp`, `nbf`, and the provider's access-token discriminator (`typ`, `token_use`, or an equivalent configured contract); map `iss + sub` to an active profile; expose a minimal `/api/v1/me` identity endpoint that phase 3 enriches with effective permissions.
- Security: deny all by default, expose only health/readiness and explicitly configured metadata, never store human passwords, never accept unsigned/self-issued production tokens, and never trust a role claim as a row-scope decision.
- Operations: external IdP supplies MFA, recovery, lockout, and key rotation; issuer/audience/claim settings are environment configuration with no committed secrets.

## Architecture

```text
Bearer token -> Spring Resource Server decoder -> narrow identity bootstrap
             -> external_identity(issuer, subject) -> tenant/profile ids + active flags
             -> transaction-local tenant context (phase 3)
             -> full user profile + permissions + scope resolver
```

This is an API-first stateless bearer boundary. A browser BFF/server-side session and self-hosted Spring Authorization Server are explicitly deferred. The application is a resource server, not an identity provider. A future client may use OIDC Authorization Code + PKCE, but the backend does not handle a password or refresh-token database in this milestone. Phase 2 is not independently deployable: phase 3 must add the restricted runtime role, tenant transaction context, and RLS production gate before any business API ships.

## Related Code Files

- Modify: `D:\AgriInsight\backend\pom.xml` (OAuth2 resource server, security, security-test dependencies)
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\api\CurrentUserController.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\ExternalIdentityService.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\IdentityBootstrapPort.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\application\PrincipalMapper.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\domain\UserProfile.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\domain\ExternalIdentity.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\domain\Role.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\domain\Permission.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\UserProfileRepository.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\PostgresIdentityBootstrapRepository.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\IdentitySecurityConfig.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\JwtPrincipalAuthenticationConverter.java`
- Create: `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\identity\infrastructure\SecuredRouteRegistry.java`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V2__create_identity_tables.sql`
- Create: `D:\AgriInsight\backend\src\main\resources\db\migration\V3__seed_permissions_and_roles.sql`
- Modify: `D:\AgriInsight\backend\src\main\resources\application.yml`, `D:\AgriInsight\backend\src\test\resources\application-test.yml`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\identity\IdentitySecurityTests.java`
- Create: `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\identity\CurrentUserApiTests.java`

## Implementation Steps (TDD: red → green → refactor)

1. **Red — hostile token matrix:** write MockMvc/security tests for no token, malformed token, wrong issuer, scalar/array wrong API audience, bad signature/algorithm, expired/not-yet-valid token with bounded clock skew, an ID-token-shaped JWT (valid issuer/signature/time but client audience or wrong `typ`/`token_use`), disabled profile, disabled tenant, unknown subject, and a valid access token. Assert generic 401/403 ProblemDetail and absence of diagnostic claims.
2. **Red — identity persistence:** write repository/service tests for `(issuer, subject)` uniqueness, profile activation/deactivation, role/permission lookup, and a token subject that maps to a different tenant than a requested resource.
3. **Green — migrations:** add `user_profiles` with tenant UUID, external display metadata, optional employee UUID (nullable until phase 4), `active`, audit/version columns; `external_identities` with tenant UUID and a unique exact `(issuer, subject)` pair; `roles`, `permissions`, `user_roles`, and `role_permissions`. Preserve the verified OIDC issuer and subject exactly—do not case-fold or trim security identifiers. Add the parameterized minimum-column identity bootstrap function in the same versioned migration so later RLS cannot make authentication circular. Seed only fixed role/permission codes. Include `SUPPLIER` as deny-by-default with no finance permission; do not create supplier portal behavior.
4. **Green — resource server:** enable method security and configure `SecurityFilterChain` for stateless bearer JWT, issuer URI, audience validator, explicit CORS allowlist, and safe authentication-entry/denied handlers. Public paths are an exact allowlist (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and dev-only OpenAPI metadata); `/api/v1/me` requires authentication; business modules contribute exact route + minimum-permission entries through `SecuredRouteRegistry`; unmatched requests end at `anyRequest().denyAll()`. Support an explicit JWKS URI without weakening issuer validation so deployments can choose whether startup depends on provider discovery.
5. **Green — principal mapping:** map only verified `iss`, `sub`, `name`/email display claims, and optional MFA assurance claim. Resolve identity through a parameterized least-privilege bootstrap that returns only profile id, tenant id, profile/tenant active flags, and exact subject match before tenant RLS context exists. Phase 3 then sets tenant context and loads the full profile/permissions under RLS. A missing/disabled profile or tenant fails closed. Never derive tenant/farm scope solely from a JWT role claim.
6. **Green — current-user contract:** expose `GET /api/v1/me` with public profile UUID, tenant UUID, and safe identity display data from the bootstrap principal. Exclude raw claims, issuer secrets, token strings, password fields, and internal DB diagnostics. Phase 3 modifies this contract to load tenant code, roles, and effective permissions only after transaction-local tenant context is set.
7. **Green — security audit:** publish structured, redacted security logs for pre-tenant authentication failures and disabled/unknown identities. Tenant-resolved role/profile changes and authorization denials use the durable tenant audit contract added in phase 3; phase 7 documents retention/export operations. Never log `Authorization` headers or tokens.
8. **Refactor:** separate filter/config, principal mapping, identity lookup, and response DTOs; add method-security annotations only to services that exist. Add an endpoint-inventory test that fails when any non-public controller mapping lacks an exact security-registry entry; verify unregistered routes hit the deny-all catch-all.

## Security contracts

- Production requires non-empty issuer URI and audience; a profile with missing values must fail startup or stay disabled, never silently accept any issuer.
- Production requires an API resource audience distinct from the interactive client ID and a provider-specific access-token discriminator when the issuer emits one. If access and ID tokens cannot be distinguished unambiguously, startup fails rather than accepting both token classes.
- JWT algorithm/key validation follows the configured provider/JWKS; do not accept `alg=none`, symmetric fallback, or a hard-coded dev key outside the test profile.
- Access tokens are short-lived and MFA policy is enforced by the selected IdP for privileged users; app code records assurance metadata when present but cannot manufacture MFA.
- CORS origins are exact configured origins, never `*` with credentials. Stateless bearer APIs do not store tokens in browser local storage; CSRF handling is revisited when a cookie-based BFF is introduced.
- 401 means no valid authentication; 403 means authenticated but missing permission; resource existence must not be disclosed when a later scope check would hide it.
- Role names and permissions are DB-backed allowlists. Unknown claims/roles are ignored, not elevated.
- Permission codes, role grants, and route templates must match [`authorization-matrix.md`](./authorization-matrix.md); phase 2 supplies the deny-all registry mechanism, not ad-hoc role checks.
- The runtime DB role gets no unrestricted `SELECT` over identity mappings. If a `SECURITY DEFINER` resolver is used, pin its `search_path`, schema-qualify objects, revoke `PUBLIC` execute, return the minimum columns, and test injection/enumeration behavior.
- `authenticated()` is not a business authorization rule. Every business mapping needs a registered HTTP method + normalized route template + minimum permission plus a service-level permission/scope check; unmatched routes deny. Registry matching uses Spring `PathPattern`, not ad-hoc regex or client-controlled strings.

## Focused validation

- `powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1`
- `backend\mvnw.cmd -Dmaven.repo.local=..\artifacts\_tmp\m2-repository -Dtest='*Identity*Test,*Security*Test' test`
- MockMvc with `spring-security-test` MockJwt fixtures for the complete token matrix.
- `git diff --check` and secret scan over `backend/`.

## Success Criteria

- [ ] No business endpoint is reachable without a valid external OIDC JWT.
- [ ] A controller mapping without an exact route-registry entry fails the endpoint-inventory test and is denied at runtime.
- [ ] Issuer, audience, signature, `exp`, and `nbf` failures produce safe deterministic 401 responses.
- [ ] A validly signed ID token is rejected; only the configured provider access-token contract authenticates.
- [ ] Unknown/disabled external identities and identities in a disabled tenant cannot obtain a tenant or role context.
- [ ] Identity bootstrap works before tenant context without granting broad access or trusting a JWT tenant claim as the sole proof.
- [ ] `/api/v1/me` exposes only the documented minimum identity DTO; no pre-RLS query loads the full profile or effective permissions.
- [ ] No password, refresh token, signing private key, or raw JWT is stored or logged.
- [ ] Role/permission seed is deterministic and migrations pass Flyway validation on a fresh PostgreSQL database.
- [ ] Security tests prove `SUPPLIER` has no finance permission and JWT claims cannot bypass DB authorization.

## Risk Assessment

- Provider claim variation: configure a narrow mapper and contract fixtures per deployment; unknown claims fail closed.
- JWKS/network failure: cache only provider public keys through Spring's decoder behavior; do not replace verification with a local secret. An explicit JWKS URI may remove discovery-time startup coupling while issuer validation remains mandatory. Validate identity configuration at startup, but do not make readiness call live discovery/JWKS when cached signature validation can continue.
- Token revocation lag: check internal profile `active` on each request; keep IdP access-token TTL short and document deactivation behavior.
- Overbroad public paths: test exact allowlist and run an endpoint inventory; a new controller must inherit the authenticated catch-all.

## Rollback

Disable the backend security profile/route or revert only phase-2 Java changes. Existing Python services remain local-only. Do not delete identity records from a shared database; deactivate profiles or apply a forward migration.
