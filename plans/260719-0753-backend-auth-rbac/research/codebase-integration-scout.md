# Research: existing codebase and integration boundary

Date: 2026-07-19

## Relevant files verified

- `README.md`: Python 0.2.0 commands, artifact layout, local-only dashboard warning, Docker and test commands.
- `docs/architecture.md`: SQLite analytical MVP, Gold/report contracts, module boundaries, and backend/auth/RLS as the next extension.
- `docs/data-contracts.md`: Bronze/Silver/Gold grains, exact nine Cost frames, canonical codes, units, and no outbound-to-activity COGS allocation.
- `docs/mvp-acceptance.md`: all current analytics/export/operations checks are complete; backend/auth/RLS is backlog.
- `src/agriinsight/pipeline.py`: sole Bronze/Silver/Gold/manifest writer.
- `src/agriinsight/warehouse.py`, `src/agriinsight/sqlite_schema.sql`: SQLite star schema and atomic replacement.
- `src/agriinsight/config.py`: canonical artifact paths.
- `dashboard/app.py`, `dashboard/cost_analysis_snapshot.py`: read-only Gold snapshot and manifest/checksum fence.
- `compose.yaml`, `Dockerfile`: Python-only services; dashboard loopback bind and read-only artifact mount with `_tmp` overlay.
- `.github/workflows/ci.yml`: Python 3.13 + Node 24 job; no Java job yet.
- `tests/test_pipeline.py`, cost/report/security/disk tests: regression gates that must remain untouched/green.

## Existing contracts that must not drift

1. `agriinsight-bronze-silver-gold-v1`, run identity, manifest checksums, and exact Gold frame schemas remain Python-owned.
2. Operating cost, procurement spend, and inventory value are not one combined measure.
3. Business codes are trim/uppercase; Python warehouse surrogate keys are regenerated and unstable across dimension changes.
4. `artifacts/_tmp` is the existing ignored scratch boundary and may hold the D-local Maven repository/temp. Backend runtime/database data belongs under ignored `backend/.runtime`; backend application code must never publish analytics artifacts.
5. `pipeline/dashboard` Compose startup must not require Java/PostgreSQL.

## Planned boundary

```text
Java operational PostgreSQL (write owner)
  -> versioned outbox/business-code export (future consumer)
  -> Python Bronze/Silver/Gold (analytics/artifact owner)
```

Java may later read a complete artifact generation only through a manifest-before/after/checksum-fenced read adapter with a read-only mount. The current milestone does not add that adapter. No Java code writes SQLite, Gold CSVs, `manifest.json`, or report files.

## File ownership for implementation

- `backend/**`: Java team/phase files; never mix Java into `src/`.
- `backend/src/main/resources/db/migration/**`: sequential migration ownership; no parallel edits to adjacent version ordering.
- `compose.yaml`, `.github/workflows/ci.yml`, `README.md`, and architecture/deployment docs: lead-owned integration edits in phase 7 only.
- `src/**`, `dashboard/**`, current Python tests: read-only compatibility surface unless a later explicitly scoped adapter plan changes them.

## Operational constraints

- At initial planning, C free space was about 10.12 GB (warning boundary 10 GB) and D about 28.53 GB (warning 25 GB); C later crossed into warning. Docker daemon was stopped during planning.
- Maven local repository, reports, and temp outputs must be directed to `D:\AgriInsight\artifacts\_tmp`; run the disk guard before/after heavy work.
- Do not pull Docker images or install a JDK/dependency merely to write this plan. If a later integration test is blocked by Docker or disk, report it; do not delete user data.

## Open questions

- Production OIDC provider and deployment secret manager.
- Future event-to-Bronze schedule and contract version.
- PostgreSQL backup/retention and audit retention requirements.
