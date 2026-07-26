# Phase 5 Overview and Farm Intelligence Evidence

Date: 2026-07-26
Scope: local acceptance checkpoint only, not public release

## Summary

Phase 5 completed locally on `/overview`, `/farms`, and `/farms/[farmId]`.
The browser surface stayed server-rendered, scope-checked, and canonical-code
driven. No public or production release was claimed; registry publication,
image publication, and release approvals remain outside this checkpoint.

## Scope And Data Flow

- Product routes are limited to `/overview`, `/farms`, and `/farms/[farmId]`.
- Server-side loaders resolve operational UUIDs to Spring masters before any
  analytics request leaves the browser boundary.
- FastAPI receives canonical codes only and returns already-aggregated Gold
  envelopes; the browser does no KPI math.
- Shared rollout fixes stayed bounded to BFF query allowlists, request-scoped
  nonce CSP/custom 404, provenance-safe direct WebP, CSP-safe trend rendering,
  and owned/mutex-guarded E2E lifecycle.

## Validation

- Clean `npm ci`.
- Spring and analytics contract drift checks.
- TypeScript `--noEmit`.
- ESLint with zero warnings.
- Next 16 production build with all product routes dynamic.
- Maven package gate with tests skipped by design.
- 9/9 PostgreSQL privilege tests.
- 82 web tests passed with 9 intentional skips.
- 3/3 installed-Chrome scenarios passed.
- Production dependency audit: 0 vulnerabilities at the configured threshold.
- Final adversarial closure review: no blocking findings.

## Browser

- Nonce CSP landing/login/custom 404 coverage passed.
- Real Keycloak, Spring `/me`, and PostgreSQL auth coverage passed.
- Period-preserving Overview -> Farms -> detail navigation passed.
- Direct reviewed WebPs returned HTTP 200 with `image/webp`, loaded at their
  canonical `/visuals/<filename>` paths, and introduced no recorded CSP
  violations.

## Cleanup

- No listeners remained on 3100, 55443, or 58080-58082.
- No `agriinsight-web-e2e` Compose containers remained.
- `artifacts/_tmp/web-e2e` and `_tmp/web-e2e` were absent.
- The global `WEB_PLATFORM_E2E=PASS` marker was emitted only after cleanup
  completed without error.

## Disk Checkpoint

- C: 8.71 GB free, WARN under the 10/8 thresholds.
- D: 28.91 GB free, PASS under the 25/20 thresholds.
- WSL swap is configured at `D:\Docker\wsl-swap.vhdx`.

## UI Review

The pre-fix UI/UX review at
[`ui-ux-phase5-review-2026-07-26.md`](./ui-ux-phase5-review-2026-07-26.md)
flagged filter handling, panel context, chart parity, route-local recovery, and
mobile presentation risks. Those blockers were resolved before the final
adversarial closure review returned no blocking findings. The final checkpoint
kept the route tree bounded and shared fixes scoped; it did not claim a public
release.

## Rollback

- Hide `/overview`, `/farms`, and `/farms/[farmId]`.
- Remove the phase-local loaders, adapters, and route exposure.
- Leave backend and analytics state untouched.

## Phase 6

Phase 6 Work Operations is next. This checkpoint does not change that
dependency or imply broader release readiness.

## Unresolved Release Inputs

- Docker Hub visibility and credentials for the web image.
- GHCR owner and whether dual-registry publication is mandatory.
- Public hostname, cookie domain, TLS termination, and `X-Forwarded-*` contract.
- Protected release gate owner and closure signal.
- Observability destination for web, BFF, and FastAPI logs and metrics.
- Demo tenant identifier, seed policy, and expansion rule.
- Production and staging OIDC issuer, client registration, redirect/logout URIs,
  and secret owner.
