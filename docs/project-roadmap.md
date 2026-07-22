# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Scale/visual checkpoint accepted 2026-07-22 | Bronze/Silver/Gold, reporting, dashboard, exports; Python 75 passed and 3 optional PDF skips; guarded 1.05M-reading profile |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Frontend | Streamlit visual polish accepted; production web gated | CK FE/Stitch artifacts plus six generated first-party visuals; production UI follows stable contracts and Phase 6 priorities |

## Next backend phases

| Phase | Goal | Dependency/status |
|---|---|---|
| Phase 6 | Cost management and reporting boundary | Phase 5 accepted; plan migrations are reserved as V16-V17 |
| Phase 7 | Outbox operations, verified images, CI/release hardening | Phase 6 accepted; plan migrations are reserved as V18-V19 |
| Frontend follow-up | Role-aware dashboard and operational workflows | Backend API/OpenAPI stable; use `frontend-follow-up-brief.md` and design system |

## Phase 5 boundary

Backend PostgreSQL inventory facts are operational source data, separate from
the Python SQLite/Gold `fact_inventory_transaction` contract. Inventory API
quantities use canonical material base units (`KG`, `LITER`, `PIECE`); a future
import adapter must convert tonnes and unit price together. Procurement spend,
inventory value, and operating cost are deliberately separate measures.

## Release and platform backlog

- Add protected CI, dependency/image scanning, SBOM/provenance, and digest smoke
  tests in Phase 7.
- Publish first-party Python/backend images only after a disk-guarded build and
  an approved Docker Hub namespace/token; the future web image is frontend-owned.
- GitHub `main` is now the default branch. About description/topics, Discussions,
  security scanning, Dependabot, templates, CODEOWNERS, and repository labels
  are configured; social-preview upload remains a one-time web-settings action.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, and
  off-host encryption before calling the system production-ready.

## Roadmap rule

When status changes, update this roadmap, the relevant phase plan, acceptance
report, and deployment guidance together. Do not mark a phase complete when an
integration gate or unresolved security decision remains.

## Scale and visual checkpoint

The reproducible `big-data` profile and dashboard visual catalog are accepted
for local demonstration. Evidence and rollback notes live in
[`plans/260722-visual-data-scale/plan.md`](../plans/260722-visual-data-scale/plan.md).
This does not claim production evidence capture, ML training data, authenticated
web UI, or registry image publication.
