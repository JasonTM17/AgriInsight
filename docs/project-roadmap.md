# Project Roadmap

This roadmap reflects verified repository state, not an assumed production
release. Each backend phase is accepted only after its focused tests, guarded
full gate, documentation, and rollback boundary are recorded in `plans/`.

## Current state

| Track | Status | Evidence |
|---|---|---|
| Analytics MVP | Scale/visual checkpoint accepted 2026-07-22 | Bronze/Silver/Gold, reporting, dashboard, exports; Python 76 passed and 3 optional PDF skips; guarded 1.05M-reading profile |
| Analytics read API | Phase 2 completed locally; Phase 5 filters accepted 2026-07-26 | Spring `/me` tenant gate, scoped FastAPI GETs, guarded seven-persona demo bootstrap, deterministic OpenAPI, real PostgreSQL reconciliation, and canonical Phase 5 filter extensions |
| DeepSeek RAG assistant | Local implementation complete; protected release gate open | Scoped Gold retrieval, V4 Flash adapter, strict citations/refusal, tokenless BFF, Vietnamese UI, security tests, and versioned retrieval evaluation |
| Backend phases 1-4 | Accepted | Foundation, OIDC/RBAC/RLS, farm/workforce/activity/harvest contracts |
| Backend phase 5 | Accepted 2026-07-22 | Inventory masters, warehouse assignments, immutable ledger/projections, reversals, reconciliation, role-aware V15 RLS, OpenAPI examples |
| Backend phase 6 | Accepted 2026-07-22 | Operating-cost ledger, correction/reversal lineage, bounded summaries, role/farm-aware V17 RLS, query-plan and OpenAPI contracts |
| Frontend | Internal release candidate extended 2026-07-27 | Nine permission-driven areas including `/assistant`, tokenless BFF, seven-persona real-OIDC browser baseline, responsive/a11y contracts, and four no-push image candidates |

## Next backend phases

| Phase | Goal | Dependency/status |
|---|---|---|
| Phase 6 | Cost management and reporting boundary | Accepted 2026-07-22; V16-V17 and 26 focused tests green |
| Phase 7 | Outbox operations, realtime read-model foundation, verified images, CI/release hardening | Core verified 2026-07-22 for V18-V19 outbox/image/recovery evidence; source now includes guarded realtime runner, hosted CI job, V20-V21 realtime read models, and summary API, but hosted acceptance and protected release approval remain open |
| Analytics Phase 2 | Internal read API and demo-tenant boundary | Completed locally; Phase 5 canonical filter extension and authenticated BFF consumption are accepted |
| Frontend follow-up | Protected external promotion | Phases 9–11 are accepted; Phase 12 internal candidate is complete, while registry environment/reviewers/secrets and production operations remain owner-gated |

## Phase 5 checkpoint

- Accepted locally on `/overview`, `/farms`, and `/farms/[farmId]`; not publicly released.
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

- Phase 7 already has hosted CI, dependency/image scanning, SBOM/provenance,
  digest smoke, and identical Docker Hub/GHCR phase-image evidence. Keep the
  protected production release environment and reviewer gates open until the
  release owner approves them.
- The realtime `realtime-e2e` job and `scripts/run-realtime-e2e-tests.ps1` are wired, but the first hosted green run is still pending; keep that slice labeled pending.
- Phase 1 contract freeze is verified in the checked-in backend OpenAPI
  artifact. Keep the additive bounded GET reads, deterministic 67-path/94-op
  contract, and current 459+100 backend gate intact before any later phase
  reopens the contract surface.
- Do not promote the manual `0.1.0-phase7`/commit tags as a production release;
  non-root web and analytics API image candidates now exist, but protected
  Docker Hub/GHCR publication remains an external release-owner action.
- GitHub `main` is now the default branch. About description/topics, Discussions,
  security scanning, Dependabot, templates, CODEOWNERS, and repository labels
  are configured; social-preview upload remains a one-time web-settings action.
- Define production OIDC/MFA, audit retention, backup/restore RPO/RTO, off-host
  encryption, and restore ownership before calling the system production-ready.

## Future product tracks

- Keep the completed eight-area production-web route set behind the protected
  release boundary; the `openid-client` OIDC boundary is implemented, while
  production OIDC configuration and approval remain open.
- Realtime alerts, advanced Kafka analytics, and mobile field workflows.
- Yield/inventory/pest-risk forecasting, anomaly detection, what-if analysis,
  and model monitoring.
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
