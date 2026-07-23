# Phase 2 verification evidence — 2026-07-23

Status: green after final hardening; production release/recovery approvals remain outside this phase.

## Python gates

| Gate | Result |
|---|---:|
| `tests/analytics_api` | 50 passed |
| Full `python -m pytest -q` | 125 passed, 3 intentional PDF skips |
| `python -m compileall -q src dashboard tests` | pass |
| `git diff --check` | pass |
| Docs validator | pass with pre-existing reference/config warnings outside this phase |

The API test suite covers tenant UUID gating, role/permission matrix, scoped
catalog reuse, live catalog drift/duplicate rejection, snapshot transitions,
oversized manifest/report controls, typed CSV validation, sanitized failures,
OpenAPI drift, pagination, and GET-only behavior.

## Real PostgreSQL and Big Data evidence

The guarded demo wrapper ran twice against a disposable PostgreSQL 18 server
with the server-side marker `app.agriinsight_demo_database=true`. The verified
snapshot contained 1,050,000 Silver/warehouse sensor facts and the expected
canonical dimensions. The final reconciliation report had seven domains,
`errorCount=0`, status `passed`, run ID
`synthetic-2026-07-18-20260718-big-data-628b12344e35`, and manifest fingerprint
`7023cf7f61e365506a69ee05d68937a5e1037eca2f268c3d50e0e3c3e5a30781`.

Using the same snapshot and Spring-shaped farm/warehouse catalogs, these
requests returned HTTP 200: readiness, catalog, overview, farms, inventory,
crop health, data quality, and costs. The disposable database container was
removed after verification; its D-local bind directory was preserved.

## Backend gates

The guarded package command passed:

```text
Surefire: 463 passed
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

The packaged JAR was then run against the disposable PostgreSQL target. With
non-web mode, Flyway enabled, and the explicit migration-exit flag it exited
with code `0` and left 21 Flyway history rows. With the same exit flag but
Flyway disabled it failed fast with code `1` and the
`spring.flyway.enabled=true` diagnostic.

## Resource guard

The project disk guard passed before the expensive backend/package/bootstrap
gates. The final post-cleanup measurement was C: 12.36 GB free and D: 25.48 GB
free. Maven/temp/cache paths remained on D. No unrelated Docker containers or
images were removed.

## Unresolved questions

None for the local Phase 2 verification boundary. Production OIDC, protected
registry release, backup/recovery ownership, and authenticated frontend remain
later gates.
