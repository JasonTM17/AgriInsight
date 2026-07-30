---
phase: 3
title: "API dashboard and hosted acceptance"
status: completed
priority: P1
effort: "1d"
dependencies: [2]
---

# Phase 3: API dashboard and hosted acceptance

## Overview

Expose server-computed forecast evidence through the existing scoped analytics
and Inventory UI paths, then complete hosted security/browser/image acceptance.

## Requirements

- Reuse Spring-resolved tenant/warehouse scope; never trust client scope.
- Add bounded forecast response models and OpenAPI/codegen drift checks.
- Nest forecast evidence under each scoped inventory item; retain legacy policy
  fields as distinct values and expose only aggregate scoped model health.
- Show point/range/model/status/backtest evidence in Vietnamese-first UI.
- Render insufficient-history and stale states honestly; no client forecast
  computation and no automatic procurement mutation.
- Add aggregate model-health counters without warehouse/material/customer
  labels that create high-cardinality or tenant leakage.
- Cap ABC and serialized API output below the BFF response limit, with a
  sanitized fail-closed error when the response cannot fit safely.

## Architecture

Authorized request → FastAPI snapshot/reconciliation gate → scoped forecast
rows → tokenless Next BFF → Inventory evidence panel. Browser receives only its
authorized warehouse rows.

The public response adopts a nested `forecast` object per inventory item with
explicit nullability for unavailable evidence. `forecastHealth` contains only
scoped status counters. The API serializes and bounds the finished envelope
before it returns it; no browser-side forecast calculation or scope selection is
permitted.

## Related Code Files

- Modify: `src/agriinsight/analytics_api/routers/inventory.py`
- Modify: `src/agriinsight/analytics_api/record_models.py`, response models,
  and scoped inventory read model
- Modify: relevant analytics response models discovered during implementation
- Modify: `tests/analytics_api/test_endpoints.py` and inventory contract tests
- Modify: `web/src/features/inventory/inventory-analytics-contract-schema.ts`
- Modify: `web/src/features/inventory/load-inventory-view-model.ts`
- Modify: `web/src/features/inventory/components/inventory-analytics-panels.tsx`
- Modify: relevant web contract/unit/E2E tests
- Modify: `docs/system-architecture.md`, `docs/project-roadmap.md`, and deployment
  guidance only for verified behavior
- Create: `docs/assets/inventory-demand-forecast-architecture.svg` and `.png`
  from the accepted data flow, with readable labels and source provenance
- Create: `assets/generated/agriinsight-inventory-forecast-loop.gif` from the
  accepted UI journey; documentation/demo media only
- Modify: `README.md` to embed the verified diagram, screenshot/GIF, captions,
  alt text, and links without claiming production accuracy

## Implementation Steps

1. Add failing API scope/size/stale/invalid-contract tests and web runtime-schema
   tests before changing public responses.
2. Extend the analytics response and OpenAPI contract with bounded forecast
   evidence, a response-byte cap, and deterministic ABC cap while preserving
   the current authorization and snapshot gates.
3. Extend the Inventory view model and panel with forecast range, data status,
   model/backtest disclosure, loading/empty/stale/error behavior, and mobile/a11y
   coverage.
4. Add safe aggregate model-health metrics and operational rollback guidance.
5. Generate a publish-grade SVG/PNG architecture diagram and a compact GIF from
   the accepted UI; verify dimensions, size, alt text, captions, and the
   demo-evidence boundary before embedding them in docs.
6. Run focused Python/web tests, full hosted CI, seven-person browser gate,
   candidate image build/scan/smoke, adversarial review, and docs sync.

## Success Criteria

- [x] Cross-tenant/cross-warehouse forecast access fails closed.
- [x] Browser displays exactly server-provided evidence and no forecast math.
- [x] OpenAPI, TypeScript, Python, a11y, responsive, and E2E gates pass.
- [x] Hosted CI and four candidate image gates pass at the accepted commit.
- [x] README and architecture docs render a verified SVG/PNG system diagram,
  relevant product image, and compact GIF with accessible text and no secrets.
- [x] Docs distinguish baseline forecasting evidence from advanced ML/SLA and
  external production deployment.

## Risk Assessment

- Tenant leakage: scope before row shaping; negative persona tests mandatory.
- Misleading UX: label baseline/model/as-of/status/range next to every decision.
- Payload growth: server caps rows and response bytes; client schema is exact.

## Security Considerations

No client-selected tenant/model/query. Existing BFF bearer, origin, CSRF, host,
response-size, and error-redaction controls remain mandatory.
