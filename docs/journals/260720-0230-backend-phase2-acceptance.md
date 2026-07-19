# Backend Phase 2: fail-closed only counts when the boring edges are proven

Date: 2026-07-20

## What happened

The OIDC path looked finished once valid JWTs mapped to `/api/v1/me`, but that was the easy half. The real work was proving every boundary around it: wrong issuer/audience/key/algorithm/time/discriminator, exact case-sensitive `(iss, sub)`, disabled profile/tenant, role-claim bypass attempts, unregistered routes, CORS, startup configuration, least-privilege SQL, response redaction, and container behavior.

Review found two honest gaps. Authentication failures were safe for clients but silent in structured security logs. Relative provider URIs failed closed, yet did so through a `NullPointerException`, which is unstable operational behavior. Both were fixed and committed with focused tests. The final backend result was 57 unit tests plus one fresh PostgreSQL 18 integration test, all green.

Disk pressure was the exhausting side problem. Windows pagefile growth pushed C down while Docker and Maven work were active. We moved recoverable crash/ETL material and the VS Code extension tree to D, preserved the original paths with a junction, compressed the D-side copies, and refused broad cleanup. That recovered safe headroom without gambling on user data. Docker was then shut down after every final gate.

## Decision

Accept Phase 2 as an identity boundary, not as a production release.

- External OIDC owns password, MFA, recovery, lockout, and signing keys.
- Spring Resource Server validates signature, configured asymmetric algorithm, issuer, API audience, expiry/not-before, subject, and an exact access-token discriminator.
- The verified identity resolves through a parameterized `SECURITY DEFINER` function with pinned `search_path`, revoked public execute, and minimum output.
- JWT role claims never become authorities. Exact DB-backed route permissions start in Phase 3; unmatched routes deny now.
- The security context retains an internal principal, not the raw JWT.
- Phase 3 must add the restricted runtime role, transaction tenant context, permissions, and RLS before any production claim.

Rejected shortcuts:

- A local password table would duplicate an identity provider and expand the threat surface.
- Trusting JWT roles/tenant claims would make row scope provider-controlled.
- Making `/api/v1/**` merely `authenticated()` would turn future endpoints into accidental data leaks.
- Mirroring PostgreSQL to Docker Hub would create supply-chain ownership we do not need.
- Broad Docker/cache pruning would risk unrelated AgriCore and TravelAI state.

## Result

- Final Maven verify: 57 unit + 1 PostgreSQL integration, zero failures.
- Flyway V1-V3: fresh apply and validate; 19 permissions, 7 roles, supplier gets zero grants.
- Final local image: `sha256:307f0ef7c9970bf5a2cf3ca4c3b3f6915417181eda5b4359d90b8543b3d21138`, non-root `10001:10001`.
- Runtime smoke: liveness 200, DB-down readiness 503, identity-disabled `/me` 401, incomplete OIDC config exits 1, no sensitive response/log match.
- Analytics regression: 65 pass, 3 expected optional-PDF skips; compileall/Node/Compose/wheel pass.
- Final disk guard after Docker shutdown: C `20.337 GB`, D `28.392 GB`, both PASS.

## Lesson

A green happy-path JWT test proves almost nothing. Authentication is acceptable only when configuration mistakes, token-class confusion, lookup privilege, route inventory, logs, and runtime packaging all fail in predictable ways. Also: disk recovery is infrastructure work. If it is not reversible and target-specific, it does not belong in an autonomous build session.

## Next steps

1. Implement Phase 3 database roles, tenant context, permission enrichment, RLS, provisioning, and pooled-connection isolation tests.
2. Keep identity disabled in production-like environments until that gate passes.
3. Decide production OIDC fixtures and audit retention.
4. Defer Docker Hub publication until Phase 7 scan/SBOM/provenance and immutable-digest gates.

## Unresolved Questions

- Production IdP/token contract and MFA assurance values.
- Audit retention/alerting ownership.
- Docker Hub namespace and release credentials.
