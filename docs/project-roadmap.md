# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Active and regression-verified | Bronze/Silver/Gold, reporting, dashboard, exports; Python 65 passed and 3 optional PDF skips |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Frontend | Design/prototype ready; implementation gated | CK FE/Stitch artifacts exist; production UI follows stable contracts and Phase 6 priorities |

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
- Set the GitHub default branch after the first `main` push (the remote is empty
  until then). About metadata, topics, Discussions, security scanning,
  Dependabot, templates, CODEOWNERS, and repository labels are configured.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, and
  off-host encryption before calling the system production-ready.

## Roadmap rule

When status changes, update this roadmap, the relevant phase plan, acceptance
report, and deployment guidance together. Do not mark a phase complete when an
integration gate or unresolved security decision remains.
