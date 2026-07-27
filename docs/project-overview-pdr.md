# AgriInsight — Project Overview and Product Development Requirements

Version: 0.9
Updated: 2026-07-27
Status: backend core, Analytics Phase 2, and eight-area web internal candidate verified; protected production release/recovery approvals remain open

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
3. **Browser plane** — Next 16 owns the Vietnamese-first eight-area product
   shell, opaque PostgreSQL sessions, OIDC authorization-code/PKCE flow, exact
   Spring/FastAPI BFF allowlists, and server-rendered permission boundaries.
4. **Integration boundary** — No direct Gold mutation or shared mutable storage.
   Phase 7 provides the versioned transactional outbox and fenced drain boundary;
   a real consumer/Kafka/Gold ingestion adapter remains future work.

See [system architecture](./system-architecture.md), [data contracts](./data-contracts.md),
and [architecture](./architecture.md) for the normative boundaries.

## Functional requirements

- Ingest and validate operational datasets with Bronze/Silver/quarantine gates.
- Materialize stable Gold KPI, alert, cost, procurement, inventory, crop-health,
  and data-quality contracts with deterministic manifests.
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
- Provide all eight product areas through the tokenless Next BFF with real
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
| 7 | Outbox, CI, images, SBOM/provenance, backup/restore, V18-V19 | Core verified; production release gated |
| Web 5–10 | Eight product areas over tokenless BFF and real upstream contracts | Accepted 2026-07-27 |
| Web 11 | Seven-persona real-OIDC browser, accessibility, security, responsive, and Big Data performance gate | Accepted on hosted CI 2026-07-27 |
| Web 12 | Four-image release contract, overlays, docs, and repository metadata | Internal candidate complete; external promotion blocked |
| Analytics 2 | Internal read API, typed contracts, guarded demo tenant, cross-store reconciliation | Accepted and consumed by the web platform |

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
and Python `75 passed, 3 skipped` unchanged at that checkpoint. Phase 7
evidence now includes 622 backend tests (98 Failsafe), hosted CI run
`29932250984` passing 5/5, and published backend/Python digests used as
non-production evidence; protected release and recovery approvals remain open.

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
  representation.
- **Operability:** C/D disk guard before heavy work; Maven/temp/cache on D;
  readiness includes database/schema; local binds remain loopback-only.
- **Maintainability:** focused modules, conventional commits, tests at the
  invariant boundary, documented migration ownership, and no speculative
  broker/cache/microservice layer.

## Explicit non-goals for the current release

Kafka/realtime alerts, ClickHouse/dbt/Airflow, mobile, ML forecasting,
what-if analysis, AI Text-to-SQL, and direct Gold writes are deferred. Public
promotion of the new web/analytics images, production identity/MFA, hostname/
TLS, observability, and backup policy remain owner-gated.

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
