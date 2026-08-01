# Phase 3 Yield Forecast Security Audit

Date: 2026-08-01
Scope: `9682be4d83e398851d4d2d55152643b4624ad60a..4d895c473c8fe91e53025ef205ec7ab364e0dc2e`
Method: STRIDE + OWASP Top 10, read-only review.

## Summary

- Files directly inspected: 28, covering API/BFF source, page/components, and focused tests.
- Confirmed findings: 0 critical, 0 high, 0 medium, 0 low, 1 informational.
- Release-blocking security defect: none found in the reviewed Phase 3 surface.
- Dependency audit: not rerun locally. `web/package.json` defines `audit:prod`; CI runs it after `npm ci`. CI also runs pinned Trivy filesystem `vuln,secret,misconfig` scanning. Hosted evidence remains the authority for dependency state.
- Secret scan: the full commit-range added-line scan found **0** matches across API-key, provider-key, AWS, JWT, password, PEM, GitHub-token, bearer-token, and credential-URL patterns. Values were never emitted.

## Findings

| ID | Severity | STRIDE / OWASP | Proof | Recommendation |
| --- | --- | --- | --- | --- |
| I-01 | Info | D / A04, A05 | `get_yield_forecast` has bounded pagination and a serialized-response cap, while the BFF has a 5-second and 2 MiB upstream bound. No route-local request throttle was found in the reviewed FastAPI/BFF files. Existing authorization limits the caller set, but authenticated burst protection must be proven at ingress. | Before exposing the internal analytics service outside its current trusted network path, document and test an ingress/session rate limit for this read route. This is not a confirmed current vulnerability because ingress policy was outside scope. |

## Verified Controls

### Spoofing and elevation of privilege — PASS

- `routers/yield_forecast.py:75` requires a bearer-backed `RequestScopeResolver`; `dependencies.py:43-75` obtains the current identity from Spring, pins the demo tenant, and authorizes `AnalyticsArea.FARMS`.
- `auth_scope.py:66-73` requires `FARM_READ` plus an eligible farm role. The route never accepts tenant or role from query parameters.
- `routers/common.py:34-43` rejects a requested farm outside the authenticated scope before snapshot access. `filter_scope.py:66-90` independently checks field/crop/season membership and relationship consistency.
- `allowed-operation.ts:128-140` fixes service, method, path, and six permitted query names. `upstream-client.ts:102-140` rejects unknown query keys, names, excess entries, and oversized values; it uses a configured base URL rather than a caller-controlled URL.
- Focused tests cover farm-manager exclusion, foreign-farm rejection before artifact access, canonical-code resolution, and allowlist exactness.

### Tampering and data integrity — PASS

- Query identifiers are pattern-constrained and length-capped to 64; `limit` is 1–100 and `offset` is 0–10,000 at `routers/yield_forecast.py:31-72`.
- `snapshot_cache.py` adds `yield_forecast` to immutable checksum-verified aggregate loading and validates active-season relationships before it can serve a response.
- `yield_forecast_read_models.py:70-90` revalidates the Gold forecast contract and converts malformed data to a sanitized 503 before public shaping.
- Pydantic records are `extra="forbid"`, finite-number constrained, and require absent/present evidence to match the forecast status (`record_models.py:252-298`). The server-side Zod boundary repeats strict structural, page, farm, and duplicate-season checks.

### Information disclosure and SSRF — PASS

- The BFF is server-only, injects the server-held bearer only after selecting an allowlisted operation, blocks redirects, and caps upstream bytes/time (`upstream-client.ts`, `bounded-upstream-fetch.ts:3-94`). No caller-supplied host, header, method, or path reaches fetch.
- The API error boundary returns typed, correlation-linked messages without exception text or filesystem paths (`errors.py:42-108`). Yield-specific tests assert invalid snapshots and response caps return sanitized 503 envelopes.
- The page and forecast components are server components; they render only authorized farm/forecast data and contain no raw HTML injection path.

### Availability and concurrency — PASS with I-01

- Request inputs, page cardinality, aggregate max rows (10,000), API serialized response (1 MiB), and BFF buffered response (2 MiB) are bounded.
- The snapshot is checked again after response construction, preventing a stale snapshot from being returned across a manifest transition (`routers/yield_forecast.py:88`; `routers/common.py:27-31`).
- The new read path performs no mutation, external URL fetch, unbounded page traversal, or per-row upstream call. Scope lookups are memoized per request in `RequestScopeResolver`.

### Repudiation — scope note

- Correlation IDs are validated/generated and returned on every response. The reviewed route is read-only, so no mutation audit event applies.
- Central successful-read access logging and retention were not evidenced by the assigned source files. This is an operational verification item, not a source-level finding.

## Validation Evidence

- `python -m pytest tests/analytics_api/test_endpoints.py tests/analytics_api/test_openapi_contract.py tests/analytics_api/test_auth_scope.py tests/analytics_api/test_snapshot_consistency.py -q` — PASS, exit 0, 100%.
- `npm --prefix web run test -- tests/bff/allowed-operation.test.ts tests/bff/upstream-client.test.ts tests/contracts/yield-forecast.test.ts tests/contracts/farm-intelligence.test.ts` — PASS, 4 files / 60 tests.
- Disk before the focused checks: C 11.80 GiB free, D 21.19 GiB free. No Docker, image build, or large artifact generation was run.
- `git diff --check` identified one trailing-space line in an existing Phase 1 evidence Markdown file. It is non-security formatting debt and outside this audit's owned report file.

## Plan Follow-up

Security review does not block Phase 3 implementation. Do not call hosted acceptance complete until the dedicated browser/media CI evidence is green and the deployment ingress boundary has been verified for the environment used.

## Unresolved Questions

- What authenticated rate-limit and successful-read audit-retention policy protects `/internal/v1/yield-forecast` at deployment ingress? This cannot be proven from the assigned application files.
