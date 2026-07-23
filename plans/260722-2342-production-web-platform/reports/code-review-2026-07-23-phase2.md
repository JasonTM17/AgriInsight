# CK adversarial code review — Phase 2 — 2026-07-23

## Scope

Reviewed the uncommitted Phase 2 shared snapshot loader, FastAPI analytics
routers/models/cache/upstream boundary, demo bootstrap/reconciliation SQL and
wrapper, Compose overlay, migration-exit guard, tests, OpenAPI, Docker/API
dependency path, and documentation updates. Runtime, demo, and API edge-case
scouts were used before the final review pass.

## Overall assessment

No Critical or High issue remains after the final hardening pass. The API is
read-only, demo-tenant UUID-gated through Spring `/api/v1/me`, bounded to
verified aggregate artifacts, and closed on scope/reconciliation drift.

## Findings resolved during review

1. `analytics_snapshot.py` now rejects Windows separators, drive-qualified
   paths, trusted-layer escapes, oversized datasets, and unstable manifests.
2. `snapshot_cache.py` rejects oversized manifests, exact-column drift,
   oversized datasets, malformed CSV rows, non-finite `cost_season` numbers,
   malformed quality DTOs, future snapshots, and oversized insight/check arrays.
3. `reconciliation_gate.py` and `demo_tenant_reconciliation.py` bound control
   plane JSON reads to 8 MiB.
4. `dependencies.py` rejects ambiguous active Spring farm/warehouse codes or
   IDs before scope conversion.
5. The migration runner requires non-web mode, explicit exit intent, and
   `spring.flyway.enabled=true`; invalid configuration closes the context and
   fails non-zero.
6. `compose.demo.yaml` uses the isolated `agriinsight-demo` project, a
   server-side PostgreSQL marker, and a separate D-local bind. The wrapper
   validates the marker and compares generated bundle/report
   tenant/run/fingerprint values.
7. Typed Pydantic response records use `extra="forbid"` and exact snapshot
   columns; tenant-wide aggregates align to the live Spring catalogs.

## Deliberate design adjudications

- The cache is keyed by the verified manifest bytes and revalidated before a
  response. Source mutation without a manifest publication change is outside
  the lineage contract; rehashing every aggregate per request would defeat the
  documented cache boundary.
- Revoked/deactivated demo assignments are not silently reactivated by an
  unchanged bootstrap contract. Request authorization uses Spring's active
  catalogs and fails closed when a required scoped catalog is empty; a changed
  operational state must be explicitly reconciled.
- Data Quality has no farm/warehouse output, so it intentionally does not call
  live farm/warehouse alignment.

## Verification

Python: 125 passed, 3 intentional PDF skips, compileall and diff-check pass.
Analytics API tests: 50 passed. Backend package: 463 passed, zero failures,
errors, or skips. Real PostgreSQL/Big Data bootstrap: seven domains,
`errorCount=0`, eight API routes HTTP 200. Packaged migration: Flyway-on exit
0 with 21 history rows; Flyway-off exit 1.

## Residual medium/low notes

The process-local cache intentionally shares verified DataFrames between
requests; current shaping helpers copy/filter rather than mutate them. The
frontend is not yet an authenticated production web boundary, and release,
OIDC/MFA, backup/recovery, and registry approvals remain later gates.

## Unresolved questions

None for Phase 2's local learning/demo acceptance boundary.
