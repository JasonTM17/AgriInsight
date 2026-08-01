---
phase: 3
title: "Scoped API farm dashboard and hosted acceptance"
status: completed
priority: P1
effort: "1.5d"
dependencies:
  - 2
---

# Phase 3: Scoped API farm dashboard and hosted acceptance

## Overview

Expose the verified Gold evidence through a separate FARMS-authorized endpoint
and render it as an independently degradable Vietnamese-first panel on the
existing farm-detail page. Complete exact OpenAPI/codegen, runtime validation,
responsive/a11y/browser, media, docs, and hosted candidate-image acceptance.
Registry publication is isolated in Phase 4.

## Requirements

- Add `GET /internal/v1/yield-forecast`; do not widen the existing `/farms`
  observed-performance payload.
- Accept only bounded canonical farm/field/crop/season filters plus pagination.
  Do not accept tenant, model, arbitrary horizon, SQL, or client-selected
  authorization scope.
- Preserve current FARMS semantics: `require_farm_filter` rejects a requested
  farm outside the authorized Spring scope but does not invent a new
  explicit-farm requirement for tenant-wide principals.
- Request order: authorize FARMS → validate any requested farm against scope →
  verify snapshot/reconciliation/live catalog → resolve canonical filters →
  scope rows → shape strict public records → current-snapshot assertion →
  serialized-response size gate.
- Response: exact season-grain items, scoped status-health counters, bounded
  page metadata, finite/nullability validation, and sanitized fail-closed
  errors. Maximum 100 items and 1 MiB serialized envelope.
- Fixed order is `(expected_harvest_date ASC, season_code ASC)` before
  pagination. V1 exposes no caller-controlled sort.
- Web BFF allowlists only the exact operation. Generated TypeScript comes from
  checked-in OpenAPI; a runtime schema rejects duplicates and foreign farm
  codes.
- Farm detail keeps Spring farm identity and observed performance usable when
  the forecast source fails. Browser displays server values verbatim, including
  as-of, expected harvest, point/historical span, target context, model,
  history, backtest origin/season counts, and coverage wording.
- Forecast status is not communicated by color alone; mobile, keyboard,
  200%-zoom, reduced-motion, loading/empty/stale/error and seven-persona
  authorization paths remain accepted.

## Related code files

- Create: `src/agriinsight/analytics_api/routers/yield_forecast.py`
- Modify: `src/agriinsight/analytics_api/app.py`
- Modify: `src/agriinsight/analytics_api/record_models.py`
- Modify: `src/agriinsight/analytics_api/models.py`
- Create or modify the narrow yield read-model module discovered during work
- Modify: `docs/contracts/agriinsight-analytics-v1.openapi.json`
- Modify: `tests/analytics_api/test_endpoints.py`
- Modify: `tests/analytics_api/test_auth_scope.py`
- Modify: `tests/analytics_api/test_openapi_contract.py`
- Modify: `web/src/server/bff/allowed-operation.ts`
- Regenerate: `web/src/server/generated/analytics/schema.d.ts`
- Create: `web/src/features/farms/yield-forecast-contract-schema.ts`
- Modify: `web/src/features/farms/load-farm-intelligence-view-model.ts`
- Create: `web/src/features/farms/components/yield-forecast-panel.tsx`
- Modify: `web/src/features/farms/components/farm-detail.tsx`
- Modify: `web/tests/bff/allowed-operation.test.ts`
- Modify: `web/tests/bff/upstream-client.test.ts`
- Modify: `web/tests/generated/client-contract-types.test.ts`
- Modify: `web/tests/generated/contract-drift.test.ts`
- Create: `web/tests/contracts/yield-forecast.test.ts`
- Modify: `web/tests/contracts/farm-intelligence.test.ts`
- Modify: `web/tests/e2e/overview-farm-intelligence.spec.ts`
- Modify: `web/tests/e2e/accessibility-and-responsive.spec.ts`
- Modify/add the yield-specific hosted media-capture spec/config discovered
  beside the accepted inventory capture
- Modify: `.github/workflows/ci.yml` and media scripts only for accepted
  yield-specific capture/upload paths
- Create: `docs/assets/yield-forecast-architecture.svg` and rendered PNG
- Create from accepted hosted journey:
  `assets/generated/agriinsight-yield-forecast-loop.gif` and a compact WebP
- Update after verification: `README.md`, `assets/generated/README.md`,
  `docs/system-architecture.md`, `docs/deployment-guide.md`,
  `docs/project-overview-pdr.md`, `docs/project-roadmap.md`, and
  `docs/codebase-summary.md`
- Create after acceptance:
  `reports/phase-03-api-dashboard-hosted-acceptance.md`

## Implementation Steps

1. Add RED API tests for authorization, cross-farm exclusion, canonical filter
   mismatch, duplicate/invalid rows, pagination, scoped health, stale snapshot,
   serialized byte cap, exact OpenAPI, and sanitized errors.
2. Implement strict public models, a scoped read model, and the new router.
   Register the router without altering existing response models.
3. Add RED web tests, exact BFF allowlist, checked-in OpenAPI regeneration,
   runtime response validation, and independent forecast loading for farm
   detail.
4. Implement the dedicated evidence panel with server-provided values only,
   visible disclosures, tabular equivalents, and responsive/a11y states.
5. Run focused Python/web tests, contract drift, typecheck, lint, build, full
   hosted CI, big-data browser matrix, security, and all four candidate image
   build/scan/read-only smoke jobs.
6. Capture media only after the hosted acceptance journey passes. Generate and
   visually inspect SVG/PNG architecture, compact WebP and GIF; enforce size,
   dimensions, frame count, SHA-256 and hosted-run provenance.
7. Sync docs and Phases 1–3, run adversarial code/security review, commit in
   focused conventional groups, push the accepted feature head, and record
   hosted candidate-image evidence. Do not tag or publish registries here.

## Success Criteria

- [x] Unauthorized or cross-farm forecast access fails closed; health counters
  reflect only rows authorized for the request.
- [x] Existing `/farms`, `/overview`, cost, inventory, and assistant public
  contracts remain stable.
- [x] Browser renders exact server evidence and performs no point/range/error
  calculations.
- [x] Forecast failure does not hide verified farm identity or realized
  performance.
- [x] Pagination is deterministic under the fixed expected-harvest/season-code
  ordering and exposes no browser-controlled sort.
- [x] Python, OpenAPI, generated TypeScript, runtime schema, unit, a11y,
  responsive, Playwright, security, build, and candidate-image gates pass.
- [x] README/docs include visually reviewed architecture PNG/SVG, hosted UI
  still/GIF, alt text, captions, hashes, and an explicit synthetic-baseline
  accuracy boundary.
- [x] External VPS deployment and production model SLA remain explicitly open,
  not falsely marked accepted.

## Acceptance evidence

- Feature head `54947ab` passed all 10 hosted CI jobs in
  [`30696001895`](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895).
  Evidence includes the real Keycloak/PostgreSQL/Spring/FastAPI/Next/Chrome
  journey and four no-push candidate-image build/scan gates.
- Artifact `yield-forecast-media-ecfe58ccceee923e43951ce6b3a942581e62a298`
  contains seven SHA-256-verified files for merge SHA
  `ecfe58ccceee923e43951ce6b3a942581e62a298`: two stills, two raw frames, a
  desktop/mobile WebP pair and the two-frame GIF.
- The source architecture SVG and rendered PNG, hosted desktop/mobile WebP,
  and GIF were visually reviewed. External VPS promotion, ingress rate limit,
  successful-read audit retention and any production agronomic SLA remain open.

## Phase 4 release linkage

Phase 4 later packages this accepted Phase 3 behavior in public GitHub Release
[`v0.4.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0) and the
protected four-image registry set. That publication evidence is tracked
separately in
[the Phase 4 report](./reports/phase-04-protected-release-evidence.md); it does
not change the hosted acceptance boundary above.

## Risks and rollback

- High-cardinality evidence could inflate payloads; item and byte caps are
  mandatory before serialization.
- Mixed observed-period and forecast-horizon semantics are avoided with a
  separate endpoint/panel.
- Rollback removes the additive BFF operation/panel and deploys the previous
  analytics/web images with their matching artifact set.

## Security

Existing bearer, origin, CSRF, host, timeout, response-size, error-redaction,
demo-tenant, Spring `/me`, farm-assignment, and snapshot checksum/reconciliation
controls remain mandatory. No browser-selected tenant or model is introduced.
