# Backend Phase 2 Code Review

## Scope

- Baseline: `b220839`
- Reviewed: 43 changed backend source, migration, configuration, and test files through `c771fa7`
- Focus: OIDC/JWT trust boundary, identity bootstrap, authn/authz ordering, exact routes, data exposure, migration privileges, concurrency/error behavior, and delivery regressions

## Overall Assessment

Phase 2 is acceptable as a local identity/security boundary. It is not a production authorization system until Phase 3 installs the restricted runtime role, transaction tenant context, permissions, and RLS. Final source and mechanical evidence agree on that boundary.

## Critical Issues

None remaining.

## High Priority

None remaining.

## Remediated Findings

1. Authentication failures initially returned safe Problem Details but emitted no structured pre-tenant security event. `SecurityProblemWriter` now logs only correlation ID, method, and request path; capture tests prove token values and provider diagnostics are absent.
2. A relative issuer/JWKS URI initially failed through a null scheme dereference. Configuration validation now rejects it deterministically as a non-absolute provider URI, with a regression test.

## Informational Findings

- Database failure during principal bootstrap is not converted into a false invalid-token result. Deployment handling/telemetry for that availability path belongs with the Phase 3 runtime transaction boundary.
- DB-down readiness takes about six seconds because database and schema contributors use bounded connection attempts. Probe timing needs an operations decision before deployment.
- Local image evidence is not registry provenance. Scanning, SBOM, signing/provenance, immutable tags, and pulled-digest smoke remain Phase 7.
- Authentication rejection logging needs production retention, aggregation, and anti-flood controls before Internet exposure.

## Checklist Result

| Area | Result |
|---|---|
| Concurrency/state | One-statement bootstrap snapshot; no shared mutable application state found |
| Error boundaries | Identity rejection and malformed claim paths become generic 401; unexpected DB faults are not mislabeled |
| API contracts | `/api/v1/me` exposes only profile/tenant UUID plus bounded display fields; raw JWT/issuer/roles absent |
| Compatibility | Analytics suite and build gates remain green; no existing public backend business contract broken |
| Input validation | Provider URIs, clock skew, algorithm, audience/client distinction, claim names/values, origins, subject/display bounds validated |
| Auth/authz | Signature + issuer + audience + time + discriminator precede exact DB identity lookup; route registry and deny-all fallback verified |
| Query efficiency | One parameterized bootstrap query; uniqueness enforced; no N+1 path introduced |
| Data leakage | Problem responses, logs, image smoke, and secret scan found no token, credential, SQL, stack, or internal path leak |

## Verification

- 57 unit/security/module tests PASS.
- 1 PostgreSQL 18/Flyway integration test PASS on a fresh schema.
- Final image runtime and fail-closed startup smoke PASS.
- 65 Python tests PASS; 3 optional-PDF skips expected.
- compileall, Node syntax, Compose config, wheel, `git diff --check`, secret scan, and C/D guard PASS.

## Recommended Next Action

Begin Phase 3 with role bootstrap and RLS threat-model tests before adding business endpoints. Do not enable identity in a production environment on the Phase 2 schema alone.

## Unresolved Questions

- Production OIDC provider/token fixture not yet selected.
- Production audit retention and Docker Hub release inputs remain undecided.
