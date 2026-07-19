# Overview page override

Status: reviewed design fixture and interaction contract. Production implementation waits for stable backend OpenAPI, identity, tenant, and permission contracts.

## User and decision

- Primary role: executive. Farm managers and analysts receive the same structure with narrower scope and permitted measures.
- Primary job: move from revenue, operating cost, profit, yield, and field risk to one evidence-backed next action.
- Primary action: open the highest-impact variance or risk, inspect its source and cutoff, then follow the authorized drill path.
- Invariant: every KPI, ranking, and insight names its lens, unit, period, scope, target basis, cutoff, and source run. The browser never recomputes canonical business metrics.

## Data contract

| Surface | Current source | Required context |
|---|---|---|
| Executive KPI ledger | `executive_summary.csv` | metric definition, value/unit, target/variance, period, cutoff |
| Financial trend | `monthly_financials.csv` | revenue, operating cost, profit, month, VND unit |
| Farm ranking | `farm_performance.csv` | farm scope, hectares, yield, cost/ha, profit/margin |
| Field-risk queue | `field_health_status.csv`, `risk_alerts.csv` | farm/field/crop, severity, evidence age, recommended action |
| Decision narrative | `insights.json`, `inventory_alerts.csv` | category, evidence/source, scope, severity, cutoff |

Current data is a frozen Gold snapshot. The page must not claim realtime freshness, causal inference, forecast confidence, or model probability until a versioned backend contract provides it.

## Structure and interaction

1. Compact scope bar: tenant, farm, season, period, data cutoff, and active-filter summary; filter state is URL-owned.
2. Asymmetric 7/5 field-ledger composition: KPI rails and monthly trend on the left; field-risk context and decision queue on the right.
3. KPI rows use bullet rails for value versus target, exact values, variance direction, and a source link; no equal marketing-card wall.
4. Farm comparison uses ranked horizontal bars plus a sortable table. Selecting a row opens the same scoped farm detail used by the Farms area.
5. Decision rows disclose evidence, scope, cutoff, and safe next action in place before navigation. Export opens a contract summary rather than immediately downloading an ambiguous file.

## State contract

| State | Required treatment |
|---|---|
| Loading | Reserve KPI, trend, ranking, and queue geometry; do not flash zero values or an empty tenant |
| Empty scope | Preserve filters, name the scope and period, and offer reset or the authorized provisioning path |
| Stale run | Persistent cutoff banner attached to every affected measure and export |
| Missing Gold contract | Keep unaffected panels usable and identify the unavailable dataset without fabricating a value |
| Partial farm scope | State that results reflect the viewer's authorized scope; never reveal excluded farm names or counts |
| Permission-denied insight | Generic denial and a safe return path; no hidden evidence in markup, cache, or error text |
| Export failed | Retain normalized request, show correlation ID and retry; never expose paths, stack traces, or partial links |

## URL, responsive, and accessibility

- URL owns farm/season/period, selected KPI, ranking sort, decision row, and filter-dialog state needed for shareable recovery. Back restores the exact scan position.
- Mobile prioritizes the scope/cutoff, first KPI variance, and highest-impact decision. The field panel follows that decision; secondary charts use summary rows and table detail rather than squeezed desktop SVGs.
- Charts expose title, unit, time granularity, exact values, nearby text summary, and semantic table. Interactive disclosure uses real buttons with `aria-expanded`/`aria-controls`; focus returns after dialogs.
- Status, variance, and risk always combine text, icon, and shape/pattern. Tested widths are 375, 768, 1024, and 1440 px with no page-level horizontal overflow.

## Reviewed fixture and acceptance

- Fixture: [`../prototypes/overview-field-ledger-prototype.html`](../prototypes/overview-field-ledger-prototype.html).
- Review evidence: [`../prototypes/overview-prototype-review.md`](../prototypes/overview-prototype-review.md).
- Desktop/mobile happy path, loading, empty, stale, missing-contract, partial-scope, denied-insight, and export-failure fixtures must be approved before production React work starts.
