# AgriInsight — Project Overview and Product Development Requirements

Version: 0.14
Updated: 2026-08-01
Status: backend core, scoped inventory-demand and yield forecasting Phases 1–3, nine-area web, alert-worker hardening, hosted CI, and protected four-image release `v0.4.0` are verified; external production deployment and recovery ownership remain open

## Product goal

AgriInsight is an enterprise agriculture analytics platform for turning farm,
operations, inventory, cost, IoT, weather, and quality data into trusted
decisions. It combines a reproducible Python Bronze/Silver/Gold analytics plane
with a Java/PostgreSQL operational plane. The product must make ownership,
tenant visibility, data quality, and metric definitions explicit instead of
hiding them in dashboards.

## Users and decisions

| User | Primary decisions |
|---|---|
| Executive | performance, margin, production, risk, and inventory direction |
| Farm/operations manager | season, field, task, harvest, and resource execution |
| Inventory manager | receipts, issues, lots, stock value, expiry, and replenishment evidence |
| Data analyst | tenant-wide read analysis, data quality, reconciliation, and reporting |
| Farm manager | assigned-farm and explicitly assigned warehouse operational views |
| Platform/security operator | identity, tenant provisioning, RLS, migrations, backups, and release controls |

## Product planes and ownership

1. **Analytics plane** — Python owns the current synthetic/source inputs,
   Bronze/Silver/quarantine validation, SQLite warehouse, Gold contracts,
   dashboards, reports, and manifest/checksum lineage.
2. **Operational plane** — Java/Spring owns authenticated operational commands,
   tenant/profile authorization, PostgreSQL source facts, inventory ledgers,
   assignments, audit/idempotency, and health/readiness.
3. **Browser plane** — Next 16 owns the Vietnamese-first nine-area product
   shell, opaque PostgreSQL sessions, OIDC authorization-code/PKCE flow, exact
   Spring/FastAPI BFF allowlists, and server-rendered permission boundaries.
4. **Integration boundary** — No direct Gold mutation or shared mutable storage.
   Phase 7 provides the versioned transactional outbox, fenced drain, and an
   opt-in Kafka consumer that materializes PostgreSQL realtime summaries.
   `V22` alert storage is immutable; the metadata-only isolated alert-worker
   hardening is V23-V28 with expected schema version 28 and is merged on `main`
   with focused contract coverage and released in `v0.2.3`. V23 requires its
   bounded source-evidence backfill before worker enablement, and V27 is a
   readiness-only invalid-source-evidence index. V28 is the forward
   acknowledgement-function repair that leaves V22 unchanged.
   It is not a public alert center or Gold ingestion.

See [system architecture](./system-architecture.md), [data contracts](./data-contracts.md),
and [architecture](./architecture.md) for the normative boundaries.

## Functional requirements

- Ingest and validate operational datasets with Bronze/Silver/quarantine gates.
- Materialize stable Gold KPI, alert, cost, procurement, inventory, crop-health,
  and data-quality contracts with deterministic manifests.
- Materialize a deterministic, versioned 30-day inventory-demand baseline with
  explicit coverage state, empirical planning range and rolling-origin
  backtest; expose the same scoped server evidence without browser math or
  automatic procurement.
- Materialize a leakage-safe, versioned yield baseline for active seasons and
  expose it only through `GET /internal/v1/yield-forecast`: canonical
  farm/field/crop/season filters, fixed ordering, maximum 100 items and 1 MiB,
  FARMS scope, server-only evidence rendering, no browser-selected model/sort
  and no operational mutation.
- Provide bounded, versioned REST APIs under `/api/v1` for identity, tenants,
  farms, fields, crops, seasons, workforce, activities, harvests, warehouses,
  materials, suppliers, warehouse assignments, balances, lots, movements, and
  linked inventory reversals, operating-cost entries, corrections, and
  hierarchy-derived summaries.
- Keep operating cost, procurement spend, and inventory value as separate
  labeled lenses; operating-cost corrections append a reversal and replacement
  instead of deleting a financial fact.
- Require provider-neutral OIDC authentication, database-enriched roles,
  deny-by-default routes, tenant/profile context, PostgreSQL FORCE RLS, and
  safe 403/404 behavior.
- Require idempotency for state-changing commands, strong optimistic versions
  for updates/lifecycle/reversals, immutable operational ledgers, and audited
  correction lineage.
- Expose OpenAPI examples only under an explicit development or authenticated
  configuration; never make Swagger a production data bypass.
- Provide a named, deterministic `big-data` profile for demonstrations and
  local performance exploration without changing the fast standard profile.
- Provide an internal, GET-only FastAPI analytics read boundary over
  checksum-verified Gold/quality aggregates, gated by Spring `/api/v1/me`, a
  configured demo tenant UUID, live canonical farm/warehouse scope, and a
  fresh cross-store reconciliation report.
- Keep the explicit local demo bootstrap transactional, loopback-only,
  server-marker-attested, credential-free in source, and isolated under the
  `agriinsight-demo` Compose project; never run it implicitly during normal
  application startup.
- Use contextual first-party visuals with provenance/alt descriptions; label
  generated Crop Health imagery as demo evidence and never treat it as a source
  observation.
- Provide all nine product areas through the tokenless Next BFF with real
  loading, empty, degraded, conflict, and forbidden states; Supplier is denied.
- Package Python, backend, web, and analytics API as independently scanned
  non-root candidates, with protected serialized immutable publication and no
  `latest` tag.

## Phase acceptance status

| Phase | Boundary | Status |
|---|---|---|
| 1 | Backend foundation/contracts | Accepted |
| 2 | OIDC identity/security boundary | Accepted |
| 3 | Tenant RBAC/PostgreSQL RLS | Accepted |
| 4 | Farm/season/workforce/activity/harvest | Accepted |
| 5 | Inventory/procurement, V12-V15, role-aware warehouse RLS, OpenAPI | Accepted 2026-07-22 |
| 6 | Operating-cost ledger/reporting boundary, V16-V17 | Accepted 2026-07-22 |
| 7 | Outbox, realtime read-model foundation, isolated alert-worker hardening, CI/images, SBOM/provenance, backup/restore | Alert-worker hardening is merged on `main`; main CI `30413064146`, protected publication `30413877863`, and release `v0.2.3` are complete. V27 is the readiness-only invalid-source-evidence index, and V28 is the forward acknowledgement-function repair. External deployment and recovery-policy ownership remain open. |
| Web 5–10 | Eight product areas over tokenless BFF and real upstream contracts | Accepted 2026-07-27 |
| Web 11 | Seven-persona real-OIDC browser, accessibility, security, responsive, and Big Data performance gate | Accepted on hosted CI 2026-07-27 |
| Web 12 | Four-image release contract, overlays, docs, and repository metadata | Released as `v0.2.3`; all four Docker Hub/GHCR digests verified and GHCR packages linked to the repository |
| Analytics 2 | Internal read API, typed contracts, guarded demo tenant, cross-store reconciliation | Accepted and consumed by the web platform |
| Inventory forecast 1–3 | Baseline/backtest, checksummed Gold integration, scoped nested API, Vietnamese evidence UI and verified media | Accepted 2026-07-30; exact-head CI `30506056691` passed 10/10 and protected `v0.3.1` publication `30506807548` passed 4/4 |
| Yield forecast 1–3 | Leakage-safe seasonal baseline/backtest, checksummed Gold snapshot, scoped internal API, Farm detail evidence UI and verified media | Accepted 2026-08-01; hosted CI [`30696001895`](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895) passed 10/10 including real browser/media and four candidate-image gates; no external deployment or model SLA is implied |
| Release v0.4.0 | Protected package publication for the accepted yield feature set | Public GitHub Release published 2026-08-01T12:01:05Z; tag object `4c27b343eecd32cf7daac462e5f661011e2af0df` peels to main SHA `616527dcc7f4a03720fb48e617f9310ab9614873`; exact-head CI `30697294137` passed 10/10 before tagging and protected publication `30697808763` passed 4/4; 16 Docker Hub/GHCR semantic/full-SHA references matched four immutable digests; no external deployment is implied |

Phase 5 acceptance evidence is recorded in
[`acceptance-2026-07-22-backend-phase5.md`](../plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase5.md):
32/32 focused inventory tests, guarded backend 487 Surefire + 92 Failsafe
tests with zero failures/errors/skips, Python 65 passed/3 skipped, and disk
guards PASS. The later
[`visual-data-scale` checkpoint](../plans/260722-visual-data-scale/plan.md)
records the prior Python 75 passed/3 skipped result and a verified
1,050,000-row big-data warehouse sensor fact.

Phase 6 acceptance evidence is recorded in
[`acceptance-2026-07-22-backend-phase6.md`](../plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase6.md):
26 focused cost tests, guarded backend `442 Surefire + 96 Failsafe` with zero
failures/errors/skips, fresh PostgreSQL V17/RLS/concurrency/query-plan checks,
and Python `75 passed, 3 skipped` unchanged at that checkpoint. Historical
Phase 7 foundation evidence is recorded in
[`acceptance-2026-07-28-realtime-foundation.md`](../plans/260727-2026-realtime-analytics-foundation/reports/acceptance-2026-07-28-realtime-foundation.md):
622 backend tests (98 Failsafe) at the prior core checkpoint, hosted CI run
`29932250984` passing 5/5 for the image path, and successful full workflow
`30337950699` with realtime job `90207600976` logging
`REALTIME_E2E result=PASS freshness_seconds=0 recovery_millis=5094 freshness_p95_millis=130 samples=20`. The current alert-worker slice has a separate local gate of 600 main + 302 test sources compiled and 42 focused tests passed; main CI `30413064146` and protected image release `30413877863` provide its hosted acceptance.

## Non-functional requirements

- **Correctness:** source facts are immutable; projections reconcile; money uses
  explicit VND scale; timestamps are UTC; base units are canonical.
- **Security:** no secrets in source/logs/images; JWT claims do not establish
  row scope; runtime DB role is non-owner/non-superuser/non-BYPASSRLS; RLS and
  application scope both fail closed.
- **Performance:** bounded pages, stable sort, tenant-leading indexes,
  deterministic locks, and no unbounded per-row database loop in public paths.
- **Reliability:** command reservation, domain write, projection update, and
  future outbox event share one transaction; replay reconstructs a safe current
  representation. The in-progress alert worker uses bounded metadata scans,
  durable cursors, current-condition recovery, hysteresis, and saturation
  signals; it is released as a private backend-image capability, not an
  externally deployed service.
- **Operability:** C/D disk guard before heavy work; Maven/temp/cache on D;
  readiness includes database/schema; local binds remain loopback-only.
- **Maintainability:** focused modules, conventional commits, tests at the
  invariant boundary, documented migration ownership, and no speculative
  broker/cache/microservice layer.

## Explicit non-goals for the current release

Broad semantic agriculture alerts, a public API/UI alert center, ClickHouse/dbt/
Airflow, mobile, forecasting beyond the accepted deterministic inventory-demand
and yield baselines, model monitoring, what-if analysis, AI Text-to-SQL, and direct Gold
writes are deferred. The metadata-only alert-worker hardening has hosted
and registry release evidence through `v0.2.3`, but no external deployment
claim. Production identity/MFA, hostname/TLS, observability, broker operations,
and backup policy remain owner-gated.

## Success metrics

- Every accepted phase has a reproducible focused and full verification gate.
- Zero cross-tenant rows in application and direct-SQL/RLS tests.
- Zero negative inventory balances or unbounded reversals under concurrency.
- Zero analytics Gold contract regressions while backend phases evolve.
- Every release image is immutable, scanned, provenance-attested, and smoke-
  tested by digest before publication.

## Decision log and next steps

- Keep PostgreSQL inventory/procurement facts separate from current Gold until a
  versioned ETL/outbox contract is accepted.
- Keep Web Cost Analysis on exactly two lenses: Spring operating ledger and
  FastAPI procurement Gold. The browser never calls either upstream service
  directly; all reads, commands, and exports cross the opaque-session BFF.
- Configure the protected four-image release environment and recovery-policy
  approvals without merging operating cost, procurement spend, or inventory
  value.
- Keep CK FE/Stitch design artifacts and the Field Ledger tokens as the
  frontend source of truth for later route changes.
- Complete branch/release controls, license, production identity, and
  operations approvals before claiming production readiness.

Open decisions: production IdP/MFA, audit retention, backup RPO/RTO/off-host
encryption/restore ownership, protected release secrets/reviewers, credential
rotation ownership, repository license/registry visibility, and GitHub
branch-protection policy.
