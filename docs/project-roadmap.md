# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Scale/visual checkpoint accepted 2026-07-22 | Bronze/Silver/Gold, reporting, dashboard, exports; Python 76 passed and 3 optional PDF skips; guarded 1.05M-reading profile |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Backend phase 6 | Accepted 2026-07-22 | Operating-cost ledger, correction/reversal lineage, bounded summaries, role/farm-aware V17 RLS, query-plan and OpenAPI contracts |
| Frontend | Streamlit visual polish accepted; production web gated | CK FE/Stitch artifacts plus six generated first-party visuals; production UI follows stable contracts and Phase 6 priorities |

## Next backend phases

| Phase | Goal | Dependency/status |
|---|---|---|
| Phase 6 | Cost management and reporting boundary | Accepted 2026-07-22; V16-V17 and 26 focused tests green |
| Phase 7 | Outbox operations, verified images, CI/release hardening | Core verified 2026-07-22; V18-V19 outbox, image, and recovery evidence is in place, but protected release approval remains open |
| Frontend follow-up | Role-aware dashboard and operational workflows | Backend API/OpenAPI stable; use `frontend-follow-up-brief.md` and design system |

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
- Do not promote the manual `0.1.0-phase7`/commit tags as a production release;
  the future web image remains frontend-owned and unbuilt.
- GitHub `main` is now the default branch. About description/topics, Discussions,
  security scanning, Dependabot, templates, CODEOWNERS, and repository labels
  are configured; social-preview upload remains a one-time web-settings action.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, off-host
  encryption, and restore ownership before calling the system production-ready.

## Future product tracks

- Role-aware production frontend and browser security boundary.
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
