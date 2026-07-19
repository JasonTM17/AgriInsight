# Backend Phase 2 Acceptance

- Date: 2026-07-20
- Decision: **ACCEPTED for the OIDC identity boundary; NOT a production release**

## Scope

This report closes provider-neutral OIDC JWT validation, exact external-identity bootstrap, deny-by-default route registration, safe security errors/audit events, deterministic identity/RBAC seed data, and the minimum `GET /api/v1/me` contract. Tenant permission enrichment, restricted runtime role grants, transaction-local tenant context, RLS, business CRUD, release CI, and registry publication remain later phases.

## Mechanical Evidence

| Gate | Result |
|---|---|
| Final disk guard after Docker shutdown | PASS: C `20.337 GB`, D `28.392 GB` free |
| Guarded backend unit gate | PASS: 57 tests, 0 failures/errors/skips |
| Guarded backend full gate | PASS: 57 unit + 1 integration test; Maven `BUILD SUCCESS` |
| Fresh database | PostgreSQL `18.0-alpine`; Flyway V1-V3 applied from empty schema and checksum validation passed |
| Identity catalog | 19 permissions, 7 fixed roles, 19 tenant-admin grants, 0 supplier grants |
| Bootstrap isolation | Exact `(issuer, subject)` match; SQL-injection-shaped subject rejected; function inaccessible before explicit `USAGE`/`EXECUTE`; direct table read remained denied |
| Token boundary | Issuer, audience, signature, algorithm, `exp`, `nbf`, subject, and claim/header discriminator tests passed; unsigned, wrong-key, wrong-algorithm, ID-token-shaped, unknown, and disabled identities failed closed |
| Route boundary | Exact public health allowlist; exact registered `GET /api/v1/me`; endpoint inventory and deny-all fallback passed; JWT role claim could not open an unregistered route |
| Security output | Generic 401/403 Problem Details; correlation ID propagated; structured auth rejection events contained no token/provider diagnostics |
| Analytics regression | 65 passed, 3 expected optional-PDF skips |
| Repository regression | compileall PASS; Node syntax PASS; Compose config PASS; Python wheel PASS (`623,774` bytes) |
| Generated output | Maven/temp/wheel paths remained on D; `git diff --check` and secret scan passed |

Canonical backend command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

## Image Evidence

- Local-only tag: `agriinsight-backend:phase2-verify`
- Local manifest digest: `sha256:307f0ef7c9970bf5a2cf3ca4c3b3f6915417181eda5b4359d90b8543b3d21138`
- Size: `178,596,910` bytes; architecture: `amd64`
- Runtime identity: UID/GID `10001:10001`
- Runtime filesystem probe: one file under `/app`, `/app/app.jar`
- Liveness with database unavailable: HTTP `200`, `{"status":"UP"}`, about `0.011 s`
- Readiness with database unavailable: HTTP `503`, `{"status":"DOWN"}`, about `6.042 s`
- Identity-disabled `GET /api/v1/me`: generic HTTP `401`, correlation ID preserved, about `0.026 s`
- Identity-enabled startup with missing provider contract: process exit `1`, configuration error present, no secret pattern matched
- Smoke/Testcontainers resources were removed; Docker Desktop returned to its prior stopped state

No registry push occurred. PostgreSQL remains an upstream-only test dependency and was not removed, mirrored, tagged as AgriInsight, or pushed.

## Review Decision

Two review findings were remediated before acceptance: missing structured events for pre-tenant authentication failures, and relative provider URIs failing with an unstable null dereference. Tests now prove redacted audit output and deterministic fail-closed URI validation. Controller review plus the final mechanical gates found no remaining Critical or High defect in Phase 2 scope.

## Production Gate

Phase 2 is intentionally not independently deployable. Phase 3 must provide the non-owner runtime database role, function grants, tenant transaction context, effective DB-backed permissions, RLS policies/direct-SQL isolation tests, connection-pool reset proof, and first-admin provisioning workflow. Until then, identity stays disabled by default outside controlled development tests.

## Non-blocking Follow-ups

- Define production issuer, API audience, interactive client ID, access-token discriminator, JWKS/discovery mode, exact CORS origins, and privileged-user MFA policy.
- Set security-log retention/rate controls before exposing the backend publicly.
- Reduce duplicate DB-down readiness latency before setting deployment probe timeouts.
- Run protected Java 21 CI, dependency/image scan, SBOM/provenance, immutable registry push, and pushed-digest smoke test in Phase 7.

## Unresolved Questions

- Which OIDC provider and exact token contract will production use?
- Which audit retention and alerting policy applies to authentication rejection events?
- Which Docker Hub namespace, visibility policy, and least-privilege release token will own first-party images?
