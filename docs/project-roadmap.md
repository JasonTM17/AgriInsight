# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Scale/visual checkpoint accepted 2026-07-22 | Bronze/Silver/Gold, reporting, dashboard, exports; Python 76 passed and 3 optional PDF skips; guarded 1.05M-reading profile |
| Analytics read API | Phase 2 completed locally; Phase 5 filters accepted 2026-07-26 | Spring `/me` tenant gate, scoped FastAPI GETs, guarded seven-persona demo bootstrap, deterministic OpenAPI, real PostgreSQL reconciliation, and canonical Phase 5 filter extensions |
| Inventory demand forecasting | Phases 1–3 accepted and `v0.3.1` released 2026-07-30 | Deterministic 30-day warehouse/material forecast, 180-day history cap, 90-day baseline, empirical p10/p90 planning range, rolling-origin MAE/WAPE, checksummed Gold, nested scoped API evidence, strict generated web contract, Vietnamese browser panel and verified media; exact-head CI `30506056691` passed 10/10 and protected publication `30506807548` passed 4/4 |
| Yield forecasting | Phases 1–3 hosted-accepted 2026-08-01 | Leakage-safe same-crop median baseline, active-season Gold contract and reconciliation, FARMS-scoped fixed-order GET, exact BFF/runtime schema, Vietnamese Farm detail evidence UI, hosted still/GIF, and four candidate-image gates; CI [`30696001895`](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895) passed 10/10. This is decision support, not external deployment or an agronomic accuracy/SLA claim. |
| DeepSeek RAG assistant | Implemented; `v0.3.0` released | Scoped Gold retrieval, V4 Flash adapter, strict citations/refusal, tokenless BFF, Vietnamese UI, security tests, versioned retrieval evaluation, and mock-only telemetry evaluator; full CI `30452477234` and protected image publication `30453840056` passed, while hosted provider SLO/groundedness/spend ownership remain open |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Backend phase 6 | Accepted 2026-07-22 | Operating-cost ledger, correction/reversal lineage, bounded summaries, role/farm-aware V17 RLS, query-plan and OpenAPI contracts |
| Realtime alert center | Phases 2–3 hosted-accepted and merged | Exact latest-50 open-alert feed, idempotent acknowledgement, same-origin BFF, V29/V30, generated OpenAPI/web contract, and the Field Ledger browser panel verified in PR #13 / CI `30425647823` and PR #14 / CI `30445148252` (feature head `e8a02a2`, rebase-merged at `bd724503`) |
| Frontend | Hosted gate and `v0.3.1` release accepted | Nine permission-driven areas including `/assistant`, tokenless BFF, seven-persona real-OIDC browser baseline, responsive/a11y contracts, forecast evidence UX, and protected `agriinsight-web:0.3.1` publication verified by CI `30506056691` and release workflow `30506807548` |

## Next backend phases

| Phase | Goal | Dependency/status |
|---|---|---|
| Phase 6 | Cost management and reporting boundary | Accepted 2026-07-22; V16-V17 and 26 focused tests green |
| Phase 7 | Outbox operations, realtime read-model foundation, isolated alert-worker hardening, verified images, CI/release hardening | Alert-worker hardening is merged on `main` and released in `v0.2.3`; main CI and protected four-image publication are green. V27 adds the readiness-only invalid-source-evidence index, and V28 repairs the acknowledgement function without rewriting V22. External deployment, broker ownership, and recovery objectives remain open. |
| Realtime alert center Phase 3 | Browser alert UX and acceptance | Hosted acceptance passed in PR #14 / CI `30445148252` and the PR is merged; no new image publication or external deployment is implied |
| Analytics Phase 2 | Internal read API and demo-tenant boundary | Completed locally; Phase 5 canonical filter extension and authenticated BFF consumption are accepted |
| Frontend follow-up | Protected external promotion | Phases 9–11 are accepted; Phase 12 published four verified `v0.2.3` images. Production hosting, OIDC/operations, package visibility, and credential rotation remain owner-gated. |

## Phase 5 checkpoint

- Accepted on `/overview`, `/farms`, and `/farms/[farmId]`; included in the released web image but not publicly deployed.
- Evidence: [plan](../plans/260722-2342-production-web-platform/plan.md), [phase file](../plans/260722-2342-production-web-platform/phase-05-overview-and-farm-intelligence.md), [report](../plans/260722-2342-production-web-platform/reports/phase-05-overview-farm-intelligence-evidence-2026-07-26.md).
- Work Operations, Inventory Control, Cost Analysis, Crop Health/Data Quality,
  and Tenant Administration are accepted through the hosted real-platform gate.

## Web quality checkpoint

- Crop Health/Data Quality preserve the Phase 2 taxonomy and permanent
  demo-evidence warning; Tenant Administration preserves true `403`, conflict,
  one-way identity handling, and unconditional Supplier denial.
- Hosted CI proves 308 web tests plus 26 real-browser journeys over seven OIDC
  personas, reconciled 1.05M-fact artifacts, five responsive viewports,
  WCAG axe checks, lab LCP/INP/CLS budgets, and security boundaries.
- Workstation C remains below the local heavy-work floor, so the accepted
  browser/image evidence comes from the guarded hosted runner. No local
  threshold was lowered.

## Web Cost Analysis checkpoint

- Full local gate: accepted 2026-07-27 through commit `186412e`.
- Scope: exactly `operating` and `procurement`; Spring ledger reads/writes stay separate from FastAPI procurement Gold; export is server-mediated and format-specific.
- Evidence: Python full suite green, Web 246 tests (9 intentional skips), typecheck, lint, contracts, Next production build, 9/9 database privilege tests, and 10/10 real Chrome journeys green. Both Cost Analysis journeys passed; cleanup completed before `WEB_PLATFORM_E2E=PASS`.
- Environment note: the accepted run used the default C/D thresholds with no override.

## Phase 5 boundary

Backend PostgreSQL inventory facts are operational source data, separate from
the Python SQLite/Gold `fact_inventory_transaction` contract. Inventory API
quantities use canonical material base units (`KG`, `LITER`, `PIECE`); a future
import adapter must convert tonnes and unit price together. Procurement spend,
inventory value, and operating cost are deliberately separate measures.

## Phase 6 boundary

Operating cost is now a separate PostgreSQL ledger lens. Clients submit one
canonical target and positive VND amount; the backend derives hierarchy
dimensions, appends correction reversals, and exposes bounded list/detail and
summary reads. Tenant Admin writes, Executive/Data Analyst reads tenant-wide,
and assigned Farm Manager reads assigned farms. Inventory Manager and Supplier
are denied. No inventory transaction, procurement spend, or Gold/SQLite fact is
implicitly converted into operating cost.

## Release and platform backlog

- Educational release [`v0.3.1`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.3.1)
  passed exact-head CI 10/10 in
  [`30506056691`](https://github.com/JasonTM17/AgriInsight/actions/runs/30506056691)
  at `7f669bc`, then protected Docker Hub/GHCR publication 4/4 in
  [`30506807548`](https://github.com/JasonTM17/AgriInsight/actions/runs/30506807548).
  All 16 semantic/full-SHA references match four immutable digests. This is
  registry evidence, not external deployment or an advanced-model SLA.
- Educational release [`v0.3.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.3.0)
  passed all ten hosted CI jobs in
  [`30452477234`](https://github.com/JasonTM17/AgriInsight/actions/runs/30452477234)
  at `eabf209`, then passed protected Docker Hub/GHCR publication in
  [`30453840056`](https://github.com/JasonTM17/AgriInsight/actions/runs/30453840056)
  for all four images. This is not external deployment or DeepSeek-provider SLO
  evidence.
- Release `v0.2.3` completed hosted CI, dependency/image scanning,
  SBOM/provenance, exact-digest smoke, and identical Docker Hub/GHCR tags for
  all four first-party images. Preserve the protected environment and reviewer
  policy for later immutable tags.
- `V22` alert storage is immutable. The isolated realtime alert worker is a
  private operational slice: V23-V30 establish its independent startup
  invariant at successful V28 plus the latest repeatable grant, V23 needs
  bounded source-evidence backfill before enablement, and V24-V27 use one
  concurrent scan index each. V27 is the readiness-only invalid-source-evidence
  index and does not replace the backfill. Transactional V28 repairs the
  acknowledgement function through its named unique constraint without
  rewriting V22; V29 locks acknowledgement to open alerts and V30 adds the
  latest-open feed index. It has a non-web restricted login, metadata-only
  scans, durable cursors, bounded pages, recovery hysteresis, and a separate
  DLT observer. Do not promote it as a public alert product, a new REST/UI
  surface, a semantic agriculture-alert policy, or an external production
  deployment.
- Existing `realtime-e2e` runner/workflow artifacts remain foundation evidence.
  Migration, focused tests, review, main merge, and protected Docker Hub/GHCR
  publication are complete for the worker slice. Phase 2 API/BFF and Phase 3
  browser acceptance are verified through PR #13 / CI `30425647823` and PR #14
  / CI `30445148252`; production Kafka ownership and external deployment remain
  owner-gated. The Phase 3 run built candidate images without publishing one.
- Phase 1 contract freeze is verified in the checked-in backend OpenAPI
  artifact. Keep its additive bounded GET reads, deterministic export, and
  current backend gate intact when later phases extend the contract surface.
- Do not promote the manual `0.1.0-phase7`/commit tags as current evidence;
  use the semantic `0.2.3` and full-SHA tags from protected run `30413877863`.
  Registry publication does not by itself approve an external deployment.
- GitHub `main` is now the default branch. About description/topics, Discussions,
  security scanning, Dependabot, templates, CODEOWNERS, and repository labels
  are configured; social-preview upload remains a one-time web-settings action.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, off-host
  encryption, and restore ownership before calling the system production-ready.

## Future product tracks

- Inventory-demand forecasting Phases 1–3 are accepted through the scoped
  API/browser boundary. The deterministic baseline is decision support, not an
  advanced-ML accuracy/SLA claim, and never mutates procurement.
- The verified SVG/PNG architecture diagram is checked in. The UI screenshot
  and GIF must remain derived from a passing hosted real-platform capture; they
  are documentation evidence, not production deployment evidence.
- Keep the completed nine-area production-web route set behind the protected
  release boundary; the `openid-client` OIDC boundary is implemented, while
  production OIDC configuration and approval remain open.
- Broad semantic agriculture alerts beyond the current exact alert API/BFF,
  advanced Kafka analytics, and mobile field workflows.
- Advanced yield forecasting beyond the accepted deterministic baseline,
  pest-risk forecasting, anomaly detection, what-if analysis, and model
  monitoring.
- Guardrailed RAG assistant is implemented locally; keep Text-to-SQL,
  embeddings, model monitoring, and auditable SQL execution as separately
  approved future work.

## Roadmap rule

When status changes, update this roadmap, the relevant phase plan, acceptance
report, and deployment guidance together. Do not mark a phase complete when an
integration gate or unresolved security decision remains.

## Scale and visual checkpoint

The reproducible `big-data` profile and dashboard visual catalog are accepted
for local demonstration. Evidence and rollback notes live in
[`plans/260722-visual-data-scale/plan.md`](../plans/260722-visual-data-scale/plan.md).
This does not claim production evidence capture, ML training data, authenticated
web UI, or protected production registry publication.
