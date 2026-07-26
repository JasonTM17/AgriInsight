# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Scale/visual checkpoint accepted 2026-07-22 | Bronze/Silver/Gold, reporting, dashboard, exports; Python 76 passed and 3 optional PDF skips; guarded 1.05M-reading profile |
| Analytics read API | Phase 2 completed locally; Phase 5 filters accepted 2026-07-26 | Spring `/me` tenant gate, scoped FastAPI GETs, guarded seven-persona demo bootstrap, deterministic OpenAPI, real PostgreSQL reconciliation, and canonical Phase 5 filter extensions |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Backend phase 6 | Accepted 2026-07-22 | Operating-cost ledger, correction/reversal lineage, bounded summaries, role/farm-aware V17 RLS, query-plan and OpenAPI contracts |
| Frontend | Phase 5 accepted locally 2026-07-26; public release gated | Secure Next 16 BFF/session foundation, Vietnamese shell, eight reviewed first-party visuals, and real Overview/Farms routes; container publication and protected release remain later gates |

## Next backend phases

| Phase | Goal | Dependency/status |
|---|---|---|
| Phase 6 | Cost management and reporting boundary | Accepted 2026-07-22; V16-V17 and 26 focused tests green |
| Phase 7 | Outbox operations, verified images, CI/release hardening | Core verified 2026-07-22; V18-V19 outbox, image, and recovery evidence is in place, but protected release approval remains open |
| Analytics Phase 2 | Internal read API and demo-tenant boundary | Completed locally; Phase 5 canonical filter extension and authenticated BFF consumption are accepted |
| Frontend follow-up | Complete Web Phases 6-12 | Phase 6 Work Operations is next; browser quality and protected release remain Phases 11-12 |

## Phase 5 checkpoint

- Accepted locally on `/overview`, `/farms`, and `/farms/[farmId]`; not publicly released.
- Evidence: [plan](../plans/260722-2342-production-web-platform/plan.md), [phase file](../plans/260722-2342-production-web-platform/phase-05-overview-and-farm-intelligence.md), [report](../plans/260722-2342-production-web-platform/reports/phase-05-overview-farm-intelligence-evidence-2026-07-26.md).
- Phase 6 Work Operations is next.

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

- Phase 7 already has hosted CI, dependency/image scanning, SBOM/provenance,
  digest smoke, and identical Docker Hub/GHCR phase-image evidence. Keep the
  protected production release environment and reviewer gates open until the
  release owner approves them.
- Phase 1 contract freeze is verified in the checked-in backend OpenAPI
  artifact. Keep the additive bounded GET reads, deterministic 67-path/94-op
  contract, and current 459+100 backend gate intact before any later phase
  reopens the contract surface.
- Do not promote the manual `0.1.0-phase7`/commit tags as a production release;
  the future web image remains frontend-owned and unbuilt.
- GitHub `main` is now the default branch. About description/topics, Discussions,
  security scanning, Dependabot, templates, CODEOWNERS, and repository labels
  are configured; social-preview upload remains a one-time web-settings action.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, off-host
  encryption, and restore ownership before calling the system production-ready.

## Future product tracks

- Complete the remaining production-web route phases and protected release
  work; the local `openid-client` OIDC boundary is implemented, while
  production OIDC configuration and approval remain open.
- Outbox consumer, realtime Kafka analytics, alerts, and mobile field workflows.
- Yield/inventory/pest-risk forecasting, anomaly detection, what-if analysis,
  and model monitoring.
- Guardrailed AI Assistant/Text-to-SQL with scoped metadata and auditable queries.

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
