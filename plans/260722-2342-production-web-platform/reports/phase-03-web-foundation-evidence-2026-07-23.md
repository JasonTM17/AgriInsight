# Phase 3 web foundation evidence

Date: 2026-07-23  
Status: completed locally  
Scope: Next 16 BFF, OIDC session boundary, generated clients, database roles,
and real platform E2E

## Delivered

- One Node 24/npm Next 16 App Router application under `web/`.
- Authorization Code + PKCE through pinned `openid-client`.
- Opaque `__Host-` browser session and CSRF cookies.
- AES-256-GCM provider-token vault with session-bound AAD and key rotation.
- PostgreSQL refresh fencing, revoke behavior, stale-row retention cleanup, and
  bounded pre-auth state.
- Dedicated web owner, migrator, and runtime roles with exact privilege tests.
- Exact backend/analytics operation allowlist derived from checked-in generated
  OpenAPI path contracts.
- Bounded upstream transport: fixed bases, no redirects, five-second timeout,
  two-megabyte response cap, and sanitized upstream errors.
- Fresh Spring `/api/v1/me` authorization on protected requests.
- Nonce CSP, host/origin/forwarded-proxy policy, CSRF enforcement, and login
  rate limiting.
- Production web CI job with lockfile install, contract drift, type, unit,
  lint, build, and production dependency audit gates.
- Disposable `web-auth-spike/` implementation removed after parity was proven.

## Full local acceptance command

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-web-e2e-tests.ps1
```

Result: `WEB_PLATFORM_E2E=PASS`

The command executed:

- disk guard before, during, and after runtime work;
- exact npm dependency installation with zero audit vulnerabilities;
- generated OpenAPI drift check;
- TypeScript typecheck;
- Vitest unit/security suite;
- ESLint;
- optimized Next production build;
- backend package build;
- pinned Keycloak and PostgreSQL 18 startup;
- guarded seven-persona demo configuration and Big Data reconciliation;
- web role bootstrap, migration, runtime schema validation, and privilege suite;
- host Spring backend readiness;
- Playwright through installed Chrome;
- deterministic service, process, and runtime-directory cleanup.

## Mechanical evidence

| Gate | Result |
|---|---|
| Web contracts | PASS |
| Web typecheck | PASS |
| Web unit/security tests | 41 passed; DB suite skipped only in generic unit run |
| Web database privilege tests | 9/9 passed |
| Web lint | PASS |
| Next production build | PASS |
| Production dependency audit | 0 vulnerabilities |
| Real Chrome platform scenario | 1/1 passed |
| Big Data demo bootstrap | PASS |
| Big Data reconciliation | 0 errors |
| Residual E2E containers | 0 |
| Residual AgriInsight host JVMs | 0 |

The browser scenario proves:

- real Keycloak login and callback;
- secure opaque cookie attributes;
- no access/refresh token in cookies, HTML, JSON, local storage, session
  storage, IndexedDB, or Cache Storage;
- encrypted provider tokens in PostgreSQL;
- expected `aud=agriinsight-api` and `token_use=access` claims after controlled
  server-side evidence decryption;
- callback replay rejection and nonce tamper rejection;
- concurrent refresh fencing;
- immediate deny after external identity disable and recovery after re-enable;
- CSRF-protected logout and server-side session revocation.

## Big Data evidence

- Run ID:
  `synthetic-2026-07-18-20260718-big-data-628b12344e35`
- Manifest fingerprint:
  `7023cf7f61e365506a69ee05d68937a5e1037eca2f268c3d50e0e3c3e5a30781`
- Reconciliation errors: `0`
- Verified Silver fact rows remain `1,050,000`.

## Backend runtime correction

The real host boot exposed one production-only incompatibility:
`PostgresOutboxWriter` requested the legacy Jackson 2 `ObjectMapper` while
Spring Boot 4 provides Jackson 3. The writer and its atomicity integration test
now use `tools.jackson.databind.json.JsonMapper`. The backend verify run and
real E2E boot both pass.

## Disk and cleanup evidence

- Full gate reached a temporary D-drive warning at `24.986 GB` free but stayed
  above the `20 GB` failure threshold.
- Obsolete auth-spike `.next` and `node_modules` caches were removed.
- Subsequent workstation pressure moved both disks back into the warning band.
  The latest measured free space on 2026-07-26 is C `8.909 GB`, D `21.107 GB`;
  both remain above the hard-stop thresholds, so heavy builds stay paused.
- Obsolete runtime caches were removed. Codex runtimes plus Cursor and VS Code
  user data were losslessly relocated to D and reattached through verified
  directory junctions. Hibernation and Docker storage were left unchanged.
- Big Data, eight generated WebPs, project source, `tmp/`, and Docker images
  were preserved.

## Review disposition

Resolved findings:

- ciphertext transplant and key-rotation binding;
- pre-auth retention/capacity and request-rate bounds;
- trusted proxy attestation and framework-forwarded-header compatibility;
- rejected schema/discovery cache recovery;
- nullable `/me` fields;
- session-bound E2E database assertions;
- exact database role attributes and memberships;
- generated contract operation binding;
- bounded upstream error/timeout/redirect/size behavior.

No open Phase 3 Critical or High finding remains.

## Unresolved questions

- Production hostname, TLS termination, and trusted proxy attestation owner.
- Production/staging OIDC registration and secret owner.
- Protected release approval remains Phase 11/12 scope.
