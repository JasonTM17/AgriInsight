# Phase 8 Contract Scout — cost-analysis (2026-07-26)

Read-only reconciliation of `phase-08-cost-analysis.md` against real source,
done before implementation. Phase 7 taught that phase files can name contracts
that do not exist; this pass checks every claim first.

## Verdict

Phase 8's context is sound. No phantom contract found, unlike Phase 7's
"trends" for inventory analytics. One line-number drift, and one real gap the
phase file already anticipates (the export router does not exist yet).

## Verified claims

| Claim | Status | Evidence |
| --- | --- | --- |
| Spring operating-cost reads at `/api/v1/cost-entries` | OK | `OperatingCostReadController.java:25` `@RequestMapping`, `:38` GET list, `:60` GET `/{id}` |
| Spring mutations POST + corrections | OK | `OperatingCostMutationController.java:34` mapping, `:54` POST, `:75` POST `/{id}/corrections` |
| Summaries at `/api/v1/cost-summaries` | OK | `CostSummaryController.java:19` mapping, `:33` GET |
| No `PATCH` and no `operating-costs` route | OK | only GET/POST mappings exist in `cost/api/` |
| Procurement analytics route exists | OK | `analytics_api/routers/costs.py:21` `GET /costs`, `operation_id=getAnalyticsCosts`, area `COSTS` |
| Eager all-format bundle to avoid per-request | OK | `cost_report_service.py:82` `build_cost_report_bundle`, with `_validate_bundle_size` (`:49`) and `_optional_xlsx_artifact` (`:58`) |
| Existing single-format renderers | OK | `cost_report_csv.py`, `cost_report_pdf.py`, `cost_report_xlsx.py` |
| Focused test target exists | OK | `tests/test_cost_report_exports.py` |

## Findings that change the work

1. **The analytics read path is already wired.** `web/src/server/bff/allowed-operation.ts:58`
   already allowlists `analyticsCosts` → `/internal/v1/costs`. Phase 8 does not
   need to add the procurement read operation, only consume it.
2. **No Spring cost operation is allowlisted yet.** Grep for
   `cost-entries|cost-summaries|costEntr|costSummar` in the BFF allowlist returns
   nothing. Phase 8 must add five exact operations: GET `cost-entries`,
   GET `cost-entries/{id}`, GET `cost-summaries`, POST `cost-entries`,
   POST `cost-entries/{id}/corrections`. The two POSTs need the same trust
   boundary layering and `Idempotency-Key` discipline as work and inventory,
   and corrections are append-only so neither carries `If-Match`.
3. **Trend panels are backed by real data**, unlike Phase 7. `CostsPayload`
   (`analytics_api/models.py:145`) carries `breakdown`, `capabilities`, `farms`,
   `monthly`, `summary`. `monthly` is the trend series and `capabilities`
   is the natural source for XLSX availability rather than a browser guess.
4. **`cost_exports.py` does not exist.** `analytics_api/routers/` holds
   `catalog, common, costs, crop_health, data_quality, farms, inventory,
   overview`. The normalized single-format export endpoint is genuinely new
   work, and its router registration must be serialized as the phase file says.
5. **The generated schema already carries every cost path**, so the typed
   allowlist entries compile without regenerating the contract:
   `src/server/generated/backend/schema.d.ts:151` `/api/v1/cost-entries`,
   `:175` `/api/v1/cost-entries/{id}`, `:192` `.../corrections`,
   `:212` `/api/v1/cost-summaries`.
6. Context line drift: the phase file cites `cost_report_service.py:30` for
   CSV/PDF/XLSX assembly; the bundle entry point is actually `:82` (`:33` is the
   private `_artifact` helper). Harmless, but the line reference is stale.

## Carry-over discipline from phases 6 and 7

- Keep the idempotency key on any ambiguous mutation answer; only a parsed
  success may clear it. This was a High defect in the inventory hook.
- Normalize blank query values to absent before schema validation, or a partial
  filter apply breaks the whole route. Also a High defect in inventory.
- Disclose server limits (row/byte caps, offset ceilings, truncation) rather
  than implying totals the browser cannot see.

## Unresolved questions

1. Does `CostsPayload.capabilities` already express XLSX availability, or does
   the export endpoint need its own capability probe?
2. Should the over-limit rejection estimate rows server-side from Gold, or reuse
   the existing bundle size validator's accounting?
