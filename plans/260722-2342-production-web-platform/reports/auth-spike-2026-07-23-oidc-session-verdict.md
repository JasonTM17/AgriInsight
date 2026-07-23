# OIDC session spike verdict — 2026-07-23

## Decision

- Winner: `openid-client` 6.8.4
- Rejected: Better Auth 1.6.24
- Real issuer gate: **PROVEN** on 2026-07-23 against pinned Keycloak 26.7.0, PostgreSQL 18, Next 16.2.11, and installed Chrome
- Scope: disposable `web-auth-spike/`; no production `web/` code

Better Auth's documented/API contract fit satisfied discovery/PKCE/state+nonce capability, encrypted OAuth-token configuration, database ownership, opaque-cookie, and local-revoke requirements. Its exact 1.6.24 package then failed an executable must-pass refresh-fencing harness: two concurrent consumers of the same expired Generic OAuth account caused two provider refresh calls. The candidate API exposes no transactional hook for leasing one provider refresh against an expected database session version. Implementing the fence would replace that lifecycle rather than validate it, so the rest of the runtime matrix moved to the fallback. The final application package/lockfile contains only `openid-client`; the rejected package is installed only into the ignored D-local evaluator cache.

## Implemented boundary

- Strict exact demo issuer and callback `/api/auth/callback/keycloak`
- Authorization Code + PKCE S256, state, nonce, confidential client
- One-use PostgreSQL pre-auth state with expiring state/binding hashes and encrypted verifier/nonce
- `__Host-` opaque session token; only SHA-256 hash persists
- AES-256-GCM encrypted access/refresh/ID tokens with key ID
- Session, refresh, lease, retry, expiry, revoke, and version timestamps/columns
- Atomic refresh lease against session version; atomic ciphertext rotation
- `invalid_grant` local revoke; transient refresh returns 503 and never expired access
- Local logout/revoke and cookie clearing remain successful when issuer revocation fails
- Exact direct Host/base URL trust; forwarded headers ignored
- `src/proxy.ts` optimistic redirect only; page/API route handlers revalidate against PostgreSQL
- No browser bearer, arbitrary proxy, tenant role, or tenant authorization

## Checked issuer fixture

The credential-free realm has seven stable persona IDs, a confidential standard-code client, exact callback/logout URLs, PKCE S256, disabled implicit/direct/service-account grants, short access lifetime, refresh rotation, `agriinsight-api` audience, and `token_use=access`.

Runtime configuration injected admin/client/persona inputs from process environment without printing values. Verification passed:

```text
OIDC_DEMO_CONFIGURED issuer=exact pkce=S256 personas=7 claims=aud+token_use credentials=environment-only
```

Discovery advertised authorization, token, JWKS, revocation, and end-session endpoints. Live encrypted access-token evidence contained `aud=agriinsight-api` and `token_use=access`. The browser gate observed the real provider end-session request with an ID-token hint and the exact allowlisted post-logout redirect. Local logout remains authoritative; remote post-logout token state was not separately asserted.

## Test evidence

Local gates:

- Disk guard: PASS at C 13.35 GB and D 25.89 GB free, both above fail thresholds
- Better Auth 1.6.24 exact-package refresh-race harness: expected rejection, 2 consumers -> 2 provider calls
- Node: 24.12.0
- npm: 11.6.2
- `npm ci --ignore-scripts`: PASS
- TypeScript 5.9.3 `tsc --noEmit`: PASS
- Unit: 16/16 PASS
- Next 16 production build: PASS; real route-handler and Proxy inventory emitted

PostgreSQL integration against the pinned container:

- 7/7 PASS
- opaque hash/ciphertext persistence
- no tenant/role authority columns
- 16 concurrent refresh consumers -> one provider call, `refresh_version=1`, `session_version=2`
- encrypted refresh-token replacement
- invalid-grant revoke
- transient fail-closed expiry
- issuer-unavailable local logout
- logout wins a concurrent refresh and revokes the discarded rotated provider token
- refresh may finish first, but atomic local revoke returns and revokes the final rotated token
- persisted issuer drift revokes the session before it can return authority

Real installed-Chrome Playwright against pinned Keycloak:

- 1/1 PASS in 10.2 seconds
- real code+PKCE login through Next routes/proxy
- a real Keycloak-signed callback with a tampered nonce -> 400 and no additional session
- callback replay -> 400 and one active session
- `__Host-`, Secure, HttpOnly, SameSite Lax, Path `/`
- empty Local Storage, Session Storage, Cache Storage, and IndexedDB
- no access/refresh/bearer markers in browser HTML/session JSON
- access/refresh ciphertext has no JWT plaintext marker
- live audience/token-use claim shape
- two pages in one browser context race six protected requests each; one refresh version and one rotated ciphertext
- provider end-session request carries `id_token_hint` and the exact post-logout redirect
- local logout clears cookie and stamps database revoke

Containers, bind-mounted PostgreSQL data, and the Compose network were removed after the proof. Unrelated containers were not stopped. The final proven run used loopback ports 55439/58080 and process-only random credentials.

## Security/dependency note

`npm audit` reports three current advisories on the required Next 16.2.11 dependency tree: one moderate PostCSS and two high Next/Sharp findings. The offered Next 9.3.3 downgrade is not a compatible remediation. This does not invalidate the session-lifecycle decision, but it blocks treating the disposable spike as production dependency approval.

## Phase 3 handoff

Port the `openid-client` protocol boundary plus application-owned schema/store/fencing behavior. Do not port spike credentials, demo persona passwords, H2 state, or process-local encryption material. Phase 3 still must decide production issuer/MFA, secret management, key rotation/key ring, deployment origin/cookie policy, retention, and operational monitoring.

## Unresolved questions

- Which production issuer and MFA/step-up contract replaces disposable Keycloak?
- Which protected secret manager and encryption-key rotation policy own provider-token ciphertext?
- Which compatible patched Next dependency set resolves the current audit findings before production?
- Should production additionally prove remote revocation token state, or is local revoke plus provider best-effort the accepted contract?
