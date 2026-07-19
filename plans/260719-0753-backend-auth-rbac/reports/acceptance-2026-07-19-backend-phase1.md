# Backend Phase 1 Acceptance

- Date: 2026-07-19
- Decision: **ACCEPTED for Phase 1 foundation; NOT a production release**

## Scope

This report closes the foundation phase only: Java/Spring bootstrap, safe HTTP conventions, module boundaries, tenant anchor migration, database-aware probes, guarded Maven execution, and local container delivery. OIDC, tenant RBAC/RLS, business APIs, CI publication, and public deployment remain later phases.

## Mechanical Evidence

| Gate | Result |
|---|---|
| Disk guard before final verify | PASS: C `12.250 GB`, D `33.765 GB` free |
| Guarded backend unit gate | PASS: 24 tests, 0 failures/errors/skips |
| Guarded backend full gate | PASS: 24 unit + 1 integration test; Maven `BUILD SUCCESS` |
| Fresh database | PostgreSQL `18.0-alpine`; Flyway V1 applied from empty schema and checksum validation passed |
| Module/security/error contracts | Spring Modulith, route exposure, Problem Detail, correlation ID, config safety, tenant invariants all green |
| Analytics regression | `65 passed, 3 skipped`; skips require optional PDF report extras |
| Repository regression | compileall PASS; Node syntax PASS; Compose config PASS; Python wheel PASS |
| Generated output | Maven/temp/wheel paths ignored and kept on D; `git diff --check` PASS |

Canonical backend command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

The runner rejected hidden Maven configuration and test-masking overrides, checked C/D before Maven, and routed repository/temp/user-home output to D.

## Image Evidence

- Local-only tag: `agriinsight-backend:phase1-verify`
- Local manifest digest: `sha256:11a9730742068e31bb1338be5635f7d05ff84e8ee2014149f76e9d2d300f5530`
- Size: `177,416,482` bytes; architecture: `amd64`
- Builder/runtime: Temurin `21.0.11`; compiler release remains Java 21
- Runtime identity: UID/GID `10001:10001`
- Runtime filesystem probe: only `/app/app.jar` under `/app`
- Liveness with database unavailable: HTTP `200`, `{"status":"UP"}`, about `0.320 s`
- Readiness with database unavailable: HTTP `503`, `{"status":"DOWN"}`, about `6.057 s`
- Readiness body contained no JDBC URL, password, internal path, or Flyway table name
- Smoke container and Testcontainers resources were removed after verification; Docker Desktop was returned to its prior stopped state

No registry push occurred. PostgreSQL remains an upstream-only test dependency and must not be mirrored as an AgriInsight image.

## Review Decision

The earlier scout/reviewer reports are historical snapshots. Their blocking findings were remediated, then rechecked through source review, focused tests, full verification, clean PostgreSQL migration, image build, and runtime smoke. No Critical or High defect remains in the accepted Phase 1 scope. A fresh independent reviewer rerun was not available in this session, so this acceptance relies on the remediated review trail plus controller review and mechanical evidence.

## Non-blocking Follow-ups

- DB-down readiness currently takes about 6 seconds because both database and schema contributors wait on bounded connection attempts. Define deployment probe timeouts and reduce duplicate connection latency before production rollout.
- Run the protected Java 21 CI job, image scan, SBOM/provenance generation, immutable registry push, and pushed-digest smoke test in Phase 7.
- Supply provider-neutral OIDC deployment inputs before Phase 2 can be accepted.

## Unresolved Questions

- Docker Hub namespace, repository visibility, and release token are not yet approved.
- OIDC issuer/audience/JWKS and privileged-identity MFA policy remain deployment inputs.
