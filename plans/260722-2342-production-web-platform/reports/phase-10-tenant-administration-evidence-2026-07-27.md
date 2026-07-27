# Phase 10 Tenant Administration Evidence

Date: 2026-07-27
Status: completed on hosted real-platform gate

## Scope

- Exact Spring resource families for users, roles, external identities,
  farm/warehouse/activity assignments, and bounded audit events.
- Vietnamese-first `/admin`, `/admin/users/[userKey]`, and `/admin/audit`
  routes with real backend data only.
- User lifecycle, role, OIDC link/unlink, farm, warehouse, and activity
  assignment controls.
- Server-derived permission gates, unconditional Supplier denial, CSRF,
  same-origin, trusted-host, session, idempotency, and optimistic-concurrency
  enforcement.
- One-way OIDC subject handling with secret-like inputs, no display-safe model
  field, no response echo, and SHA-256-only retry fingerprint state.

## Contract Evidence

- Upstream mutations use only:
  `/api/v1/users`, `/api/v1/users/{id}/roles`,
  `/api/v1/users/{id}/external-identities`,
  `/api/v1/farm-assignments`, `/api/v1/warehouse-assignments`, and
  `/api/v1/activities/{id}/assignments`.
- Reads additionally use `/api/v1/audit-events`.
- No Spring `/api/v1/admin/*` route was created.
- Role-code path interpolation accepts only the seven frozen backend role
  values; all other path parameters remain UUID-only.
- Versioned commands require strong numeric `If-Match`; create and
  identity-link/unlink commands reject an unexpected version header.

## Verification

| Gate | Result |
|---|---|
| Web full suite after final hardening | 308 passed, 11 intentional skips |
| Exact admin command dispatcher matrix | 13 passed |
| Focused admin/navigation suite | 34 passed |
| Contract drift | PASS |
| Typecheck | PASS |
| Zero-warning lint | PASS |
| Secret-pattern review | PASS; no credential or private-key material |
| Next production build | PASS on GitHub CI; not run locally because of disk guard |
| Guarded real browser | PASS; tenant-admin lifecycle/assignment, exact seven-persona authorization, and Supplier denial |
| GitHub CI `30267362838` | PASS; Java, Next, Python, secret scan, browser, and four no-push image jobs green |

## Security Review

- Browser receives no bearer or refresh token; mutations use the existing
  opaque-session BFF boundary.
- Raw OIDC subject never enters server-rendered props, read models, audit UI,
  browser history, analytics, or response bodies.
- Upstream error bodies are discarded; browser problems contain bounded
  Vietnamese messages plus optional correlation ID only.
- Backend `400`, `401`, `403`, `404`, and `409` are mapped distinctly.
- Denied server pages use Next 16 `forbidden()` with `authInterrupts`, plus a
  segment-level forbidden boundary intended to preserve the protected shell.
- Real Chrome proves the forbidden shell, exact route matrix, authorized
  lifecycle/assignment journey, and unconditional Supplier denial.

## Disk Observation

- C remained below the 8 GiB local hard floor, while D remained the cache and
  recovery target. No local browser, Docker, or Big Data workload was started.
- The accepted browser/image evidence used guarded ephemeral hosted storage;
  no local threshold was lowered.

## Commits

- `c9de563` — exact admin contract allowlist
- `2918ea4` — safe admin read models
- `9db3f2f` — tenant administration workspace
- `c58a2da` — secured admin mutation BFF
- `8bd2789` — command controls
- `1e6244b` — true forbidden states
- `35557d3` — complete command dispatcher matrix

## Unresolved Questions

- None inside Phase 10. External release controls remain tracked in Phase 12.
