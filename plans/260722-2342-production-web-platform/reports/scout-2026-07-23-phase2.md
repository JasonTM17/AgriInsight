# Phase 2 edge-case scout synthesis — 2026-07-23

Three read-only scouts inspected runtime, demo bootstrap, and API boundaries
before the final hardening pass.

## Findings and disposition

| Finding | Disposition |
|---|---|
| Windows separator/drive traversal in artifact paths | Fixed with trusted-layer resolution and Windows-specific tests |
| Migration exit could claim success with Flyway disabled | Fixed with non-web/Flyway predicate and fail-fast packaged probe |
| API console entrypoint missing Uvicorn in common install/image path | Fixed in the dev extra and Docker image API extra |
| Mtime-only freshness and future timestamps | Fixed by manifest `generated_at`/`as_of_date` lineage plus future/stale gates |
| Static reconciliation evidence and live aggregate scope drift | Fixed with report age/fingerprint checks and live Spring catalog alignment |
| Self-asserted demo database marker | Fixed with server-side PostgreSQL GUC and wrapper preflight |
| Artifact strings inferred as raw SQL | Fixed with typed SQL-expression primitives |
| Duplicate expected natural keys / bundle-report mismatch | Fixed with duplicate/count checks and fingerprint/run/tenant comparison |
| Tenant-wide payloads and arbitrary Gold columns | Fixed with live scope intersection, closed Pydantic DTOs, exact columns, and row caps |
| Unbounded manifest/report/catalog JSON and duplicate live codes | Fixed in the final review pass with byte caps and active catalog uniqueness checks |
| CSV type/enum/finite-value validation gap | Fixed with DTO validation for every selected CSV row and finite `cost_season` checks |

The deliberate revocation policy is fail-closed: a bootstrap rerun does not
silently restore an authorization assignment that an operator revoked. Spring
active catalogs govern request scope, and the reconciliation gate prevents a
stale report from becoming ready.

## Verified non-issues

No raw bearer, Spring response, filesystem path, stack trace, or credential is
returned in API errors. Request paths load only bounded Gold/quality datasets;
they do not read Silver/Bronze or write artifacts, SQLite, or business tables.
GET retry, redirect rejection, upstream byte caps, correlation propagation,
tenant UUID isolation, and foreign-filter denial are covered by code/tests.

## Unresolved questions

None after the final hardening pass. Production deployment inputs remain
deliberately unresolved outside the local learning/demo scope.
