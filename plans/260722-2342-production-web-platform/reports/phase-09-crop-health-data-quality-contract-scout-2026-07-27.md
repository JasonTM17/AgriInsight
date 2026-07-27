# Phase 9 Contract Scout — crop-health-and-data-quality (2026-07-27)

Read-only reconciliation of `phase-09-crop-health-and-data-quality.md` against
real source, before implementation. Same pass that caught a phantom analytics
contract in Phase 7 and an unregistered Gold dataset in Phase 8.

## Verdict

Phase 9 is the cheapest remaining web phase: both analytics routes exist **and**
are already allowlisted, so no BFF surface has to be added. One naming nuance
needs a decision before the prohibition in the phase file is applied literally.

## Verified claims

| Claim | Status | Evidence |
| --- | --- | --- |
| Crop health analytics route exists | OK | `routers/crop_health.py:22` GET, `operation_id=getAnalyticsCropHealth` |
| Data quality analytics route exists | OK | `routers/data_quality.py:21` GET, `operation_id=getAnalyticsDataQuality` |
| Both consumable through the BFF today | OK | `allowed-operation.ts:92` `analyticsCropHealth` → `/internal/v1/crop-health`; `:98` `analyticsDataQuality` → `/internal/v1/data-quality` |
| `assessmentMethod=rule-based-heuristic` is real | OK | `analytics_api/models.py:139` |
| Phase owns no analytics router | OK | routers already exist; this phase adds only web routes and view models |

## Findings that change the work

1. **No new BFF operation is required.** Unlike Phase 8, which had to allowlist
   five Spring cost operations, Phase 9 consumes two operations that are already
   allowlisted. The work is route tree, loaders, view models, states, and tests.
2. **The "no predictive language" rule cannot be applied retroactively.** The
   phase file forbids probability, causal, predictive, anomaly-detection,
   realtime, and ML language in code, copy, tests and docs. A field named
   `predicted30dNeed` already exists in the **inventory** analytics contract
   (`inventory-analytics-contract-schema.ts:59`) and in the generated analytics
   schema (`schema.d.ts:835`). That is a frozen Gold contract field consumed by
   Phase 7, not Phase 9 copy. Renaming it would break contract parity with the
   analytics plane for no user benefit. Read the prohibition as binding on what
   Phase 9 introduces — its own routes, copy, view models and tests — and leave
   the frozen field name alone. Do not "clean up" a generated contract to satisfy
   a phase rule.
3. The permanent AI-demo warning has an existing home: the dashboard already
   marks its Crop Health image as AI-generated demo evidence and never assigns it
   an observation id, so the web surface should reuse that framing rather than
   invent new wording.

## Carry-over discipline from phases 6 to 8

- Normalize blank query values to absent before schema validation.
- Disclose server limits and truncation instead of implying totals.
- Never recompute a server-owned classification in the browser; render severity,
  status and method verbatim.
- Any dynamic sizing must avoid inline `style` attributes; the nonce-only CSP
  blocks `style-src-attr`, which cost Phase 7 a real defect found only at
  runtime. Prefer element attributes plus a stylesheet.

## Unresolved questions

1. Does the crop-health payload already carry `evidenceRows` and
   `evidenceSignals`, or does the view model derive them from row counts? The
   phase file's interface sketch assumes they exist upstream.
2. Which existing WebP asset keys should the image panel reference, given the
   dashboard asset catalog is the source of truth?
