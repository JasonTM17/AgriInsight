# Costs page override

Status: design contract plus browser-gate-approved read-only prototype. Current Gold data remains immutable; operational ledger mutations depend on backend Phase 6.

Reviewed fixture: [`cost-analysis-prototype.html`](../prototypes/cost-analysis-prototype.html), with source, interaction, responsive, zoom, offline, print, and review evidence recorded in [`cost-analysis-review.md`](../prototypes/cost-analysis-review.md). This approval covers the frozen design fixture only; it does not approve a production frontend, backend API, or file-generating export.

## User and decision

- Primary roles: executive and finance analyst; farm manager receives scoped comparison only.
- Primary job: explain spend without double counting operating cost, procurement spend, or inventory value.
- Primary action: drill from a variance to the exact farm/season/activity evidence or export the normalized scoped result.
- Invariant: the selected lens is written in the page title, filter summary, chart labels, table headers, and export metadata.

## Data contract

| Lens/surface | Source | Never combine with |
|---|---|---|
| Operating activity cost | `cost_summary`, `cost_monthly`, `cost_activity`, `cost_farm`, `cost_season` Gold contracts | procurement cash flow or inventory valuation |
| Procurement view | `procurement_summary/detail` | operating total |
| Inventory value | inventory Gold contracts | cost of operations |
| Reconciliation | `cost_reconciliation` | an unlabeled total |
| Future ledger mutation | Phase 6 operational API | direct writes to analytics artifacts |

Every measure carries VND/unit/date granularity and data cutoff. Browser code formats values; it never recalculates canonical allocation or profit.

## Structure and interaction

1. Lens selector with plain definitions and a visible non-combinable warning.
2. Summary strip: total for selected lens, comparison period, variance, reconciliation status.
3. Monthly trend + target context; category and farm/season comparison; text summary and table fallbacks.
4. Detail ledger with allow-listed sort/filter fields, stable row IDs, source/run evidence, and bounded pagination.
5. Export action shows row estimate, scope, cutoff, lens, and final file contract before generation.

## State contract

| State | Required treatment |
|---|---|
| Loading | Reserve summary/chart/table geometry; no zero-value flash |
| Empty result | Preserve filters and explain which scope/date produced no rows |
| Stale snapshot | Persistent cutoff banner; allow read/export only when policy permits |
| Partial reconciliation | Show exact unmatched category/count without creating a false total |
| Permission denied | Hide finance measures and return a generic scoped denial |
| Conflict | Future ledger edit keeps draft and shows the newer server version |
| Export failed | Retry with normalized request/correlation ID; no path, stack, or partial link |

## URL, responsive, and accessibility

- URL owns lens, tenant/farm/season scope, period, category, sort, and page. Drill/back restores the exact comparison state.
- Mobile shows lens and scope before any KPI; comparison rows replace dense bars; the ledger uses a deliberate horizontal wrapper only for audit columns.
- Negative values include sign and text, not red alone. Charts expose units, time granularity, summary, and sortable table.

## Acceptance

- Prototype gate passed for separate operating and procurement lenses, Gold reconciliation, bounded data, URL/history state, keyboard navigation, export preflight, 375/768/1024/1440 responsive layouts, landscape, reduced motion, 200% browser zoom, offline reload, and print.
- Inventory value remains a separate journey and is never merged into either Cost lens.
- Reconciliation mismatch, stale, denied, loading, and export-failure fixtures still must exist before production code.
