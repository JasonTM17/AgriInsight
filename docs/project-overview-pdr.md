# AgriInsight — Project Overview and Product Development Requirements

Version: 0.5
Updated: 2026-07-22
Status: backend Phases 1-5 accepted locally; product release not yet claimed

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
3. **Integration boundary** — No direct Gold mutation or shared mutable storage.
   A later versioned ETL/outbox contract may consume operational facts after
   Phase 7 hardening.

See [system architecture](./system-architecture.md), [data contracts](./data-contracts.md),
and [architecture](./architecture.md) for the normative boundaries.

## Functional requirements

- Ingest and validate operational datasets with Bronze/Silver/quarantine gates.
- Materialize stable Gold KPI, alert, cost, procurement, inventory, crop-health,
  and data-quality contracts with deterministic manifests.
- Provide bounded, versioned REST APIs under `/api/v1` for identity, tenants,
  farms, fields, crops, seasons, workforce, activities, harvests, warehouses,
  materials, suppliers, warehouse assignments, balances, lots, movements, and
  linked inventory reversals.
- Require provider-neutral OIDC authentication, database-enriched roles,
  deny-by-default routes, tenant/profile context, PostgreSQL FORCE RLS, and
  safe 403/404 behavior.
- Require idempotency for state-changing commands, strong optimistic versions
  for updates/lifecycle/reversals, immutable operational ledgers, and audited
  correction lineage.
- Expose OpenAPI examples only under an explicit development or authenticated
  configuration; never make Swagger a production data bypass.

## Phase acceptance status

| Phase | Boundary | Status |
|---|---|---|
| 1 | Backend foundation/contracts | Accepted |
| 2 | OIDC identity/security boundary | Accepted |
| 3 | Tenant RBAC/PostgreSQL RLS | Accepted |
| 4 | Farm/season/workforce/activity/harvest | Accepted |
| 5 | Inventory/procurement, V12-V15, role-aware warehouse RLS, OpenAPI | Accepted 2026-07-22 |
| 6 | Operating-cost ledger/reporting boundary, V16-V17 | Planned |
| 7 | Outbox, CI, images, SBOM/provenance, backup/restore, V18-V19 | Planned |

Phase 5 acceptance evidence is recorded in
[`acceptance-2026-07-22-backend-phase5.md`](../plans/260719-0753-backend-auth-rbac/reports/acceptance-2026-07-22-backend-phase5.md):
32/32 focused inventory tests, guarded backend 487 Surefire + 92 Failsafe
tests with zero failures/errors/skips, Python 65 passed/3 skipped, and disk
guards PASS at the end of the gate.

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
what-if analysis, AI Text-to-SQL, browser BFF/session handling, and direct Gold
writes are deferred. Docker Hub/GitHub Packages publication and production
identity/MFA/backup policy are also deferred until Phase 7.

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
- Build Phase 6 cost reporting without merging operating cost, procurement
  spend, or inventory value.
- Use CK FE/Stitch design artifacts as the frontend source of truth, then build
  role-aware screens only after API contracts are frozen.
- Complete Phase 7 before claiming production readiness or pushing first-party
  images to Docker Hub.

Open decisions: production IdP/MFA, audit retention, backup RPO/RTO/off-host
encryption, Docker Hub namespace/token, and GitHub branch-protection policy.
