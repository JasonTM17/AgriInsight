# AgriInsight OIDC session spike

This disposable Next 16 application proves the browser/BFF authentication boundary before production `web/` work starts. It is not a production login service.

## Verdict

`openid-client` 6.8.4 wins. Better Auth 1.6.24 was evaluated first through an exact-package refresh-race harness plus API-contract analysis:

| Must-pass invariant | Better Auth 1.6.24 | `openid-client` 6.8.4 |
|---|---:|---:|
| OIDC discovery, code flow, PKCE S256, state, nonce | yes | yes |
| encrypted provider tokens | yes | yes |
| application-owned PostgreSQL records | yes | yes |
| opaque browser cookie and local revoke | yes | yes |
| atomic one-refresh-per-session-version lease | **no fit hook** | application-owned and proven |

The isolated harness installs the exact candidate after verifying its npm integrity, creates an expired Generic OAuth account, and runs two concurrent access-token consumers against a delayed provider. Both consumers call the provider (`provider_calls=2`), proving no one-refresh-per-version fence. Better Auth's public configuration also exposes no transactional refresh hook for adding that fence without replacing the lifecycle. The final application package and lockfile therefore contain no Better Auth dependency or dead adapter; the rejected candidate remains only in the ignored D-local evaluation cache.

`openid-client` owns only OIDC protocol validation. This spike owns the PostgreSQL schema, pre-auth records, AES-256-GCM token encryption, session hashes, local revocation, and refresh fencing.

## Frozen behavior

- Callback: `http://localhost:3100/api/auth/callback/keycloak`
- Direct request trust: exact `Host` and configured absolute URL. Forwarded headers are ignored, never trusted.
- Authorization: OIDC Authorization Code with PKCE S256, strict discovered issuer, state, and nonce.
- Pre-auth: state hash, browser-binding hash, encrypted verifier/nonce, five-minute expiry, one-use consume.
- Browser: one high-entropy `__Host-agriinsight-auth-spike` cookie with `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`; no provider token in browser storage.
- Database: session token SHA-256 only; provider tokens encrypted with AES-256-GCM and a stored key ID.
- Refresh: PostgreSQL lease bound to the expected session version; rotation replaces ciphertext atomically. `invalid_grant` revokes locally. Transient issuer failures never return an expired access token.
- Logout: local database revoke and cookie clear are authoritative even when issuer revocation is unavailable; when advertised, provider end-session receives the encrypted ID-token hint and exact allowlisted post-logout redirect.
- Authorization data: the session schema stores no tenant or role authority. Spring remains the authorization boundary.
- Next boundary: real App Router route handlers plus `src/proxy.ts`; protected page/API handlers still perform authoritative database validation.

## Demo issuer

`compose.auth-spike.yaml` is standalone and binds PostgreSQL and Keycloak to loopback only. It pins:

- PostgreSQL 18 to the repository's verified upstream digest
- Keycloak 26.7.0 to the supplied upstream digest

The checked-in realm contains no password or client secret. It defines a confidential standard-code client, exact callback/logout URLs, PKCE S256, disabled implicit/direct/service-account grants, refresh rotation, a short access-token lifetime, `agriinsight-api` audience and `token_use=access` mappers, and seven stable persona IDs.

`scripts/configure-demo-oidc.ps1` injects client/admin/persona secrets from the process environment without printing them. It verifies exact discovery, PKCE, endpoint capabilities, client grants, and mapper shape.

## Run

Requirements:

- Node 24.12.0 and npm 11.6.2
- Docker Desktop for the real issuer gate
- installed Google Chrome; Playwright does not download a browser
- process environment values matching `.env.auth-spike.example`

Use unique local values. The runner deliberately does not parse or echo dotenv files; export secrets into the current protected shell/session or its secret manager.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-web-auth-spike-tests.ps1
```

The full runner first executes `scripts/evaluate-better-auth-fit.ps1`. Its expected rejection marker is:

```text
BETTER_AUTH_REFRESH_FENCE=FAILED version=1.6.24 provider_calls=2 concurrent_consumers=2
```

The runner places npm cache and temporary output under `D:\AgriInsight\artifacts\_tmp`, runs the disk guard, `npm ci`, typecheck, unit tests, and the Next build. When Docker is available it then starts the standalone services, configures the issuer, runs PostgreSQL integration tests, and launches Playwright with the installed Chrome channel.

If Docker is unavailable, PostgreSQL tests show explicit skips, the runner prints `AUTH_SPIKE_REAL_ISSUER_GATE=NOT_PROVEN`, and exits 2. A mocked or library-only run cannot satisfy the real issuer gate.

Focused commands:

```powershell
npm --prefix web-auth-spike run typecheck
npm --prefix web-auth-spike test
npm --prefix web-auth-spike run build
npm --prefix web-auth-spike run test:integration
npm --prefix web-auth-spike run test:e2e
```

## Evidence matrix

Unit tests cover missing/mismatched/expired/replayed state, nonce rejection, callback/host/open-redirect rules, cookie policy, encryption, proxy behavior, no browser persistence source path, one-provider-call concurrency, a refresh waiter beyond the old 1.5-second window, rotation, `invalid_grant`, transient failure, issuer-unavailable local logout, and persisted issuer drift.

PostgreSQL tests cover one-use persistence, token hash/ciphertext storage, absence of local tenant/role authority, concurrent refresh fencing, atomic refresh-token replacement, invalid-grant revocation, transient fail-closed behavior, local logout during issuer failure, both logout-first and refresh-first race orders with final rotated-token revocation, and issuer-drift revocation.

The real Chrome/Keycloak test covers code+PKCE login through Next route handlers/proxy, a Keycloak-signed response with a tampered nonce, callback replay rejection without a second active session, cookie flags, empty Local/Session/Cache/IndexedDB storage, no token markers in HTML/session JSON, encrypted database tokens, live `aud`/`token_use` claims, two-tab concurrent refresh with one version/rotation, the actual provider end-session request, and local logout/revocation.

## Known limits

- Keycloak advertised revocation and end-session endpoints in the proven run. The browser gate asserted the end-session request, ID-token hint, and exact redirect. It does not separately assert the remote token's post-logout state. Local revoke remains the mandatory result.
- Production issuer, MFA/step-up, key rotation/key ring, secret manager, cookie deployment origin, session lifetime, and operational retention remain Phase 3/deployment decisions.
- `npm audit` on the required Next 16.2.11 pin currently reports one moderate PostCSS and two high Next/Sharp advisories. The registry's suggested downgrade is not a valid compatibility fix. This disposable spike does not claim a production dependency-security approval.
