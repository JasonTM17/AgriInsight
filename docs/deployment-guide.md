# Deployment Guide

This guide documents the verified local/runtime contracts through Backend Phase 7 core. It is not a production deployment approval: protected registry release, environment review, a scheduled recovery drill, and production release/recovery approvals remain required before production.

## Supported execution boundaries

| Component | Current use | Exposure |
|---|---|---|
| Python pipeline/dashboard | Local analytics MVP | Dashboard binds locally; do not expose publicly |
| Internal analytics API | FastAPI read-only aggregate surface | Loopback/internal network only; Spring `/api/v1/me` remains the authorization source |
| Next web platform | Locally verified overview, farm, and Work Operations browser surface | Loopback/private only until Phase 11 quality and Phase 12 protected release gates pass |
| Java backend, identity disabled | Foundation/health verification | Loopback or loopback-published container only |
| Java backend, identity enabled | Locally verified OIDC, tenant RBAC/RLS, and tenant administration | Keep private until production IdP/operations and later domain/release gates pass |
| PostgreSQL 18 | Upstream Testcontainers dependency | Never mirror/push as an AgriInsight image |

## Preflight

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

`verify` requires Docker and runs the mandatory PostgreSQL 18 integration gate. Maven repository, temp, and user-home paths must resolve to D. Do not pass test-skip/fail-masking flags.

## Big-data local demonstration

The default analytics run is intentionally small for CI. To demonstrate a
production-like workload, run the guarded profile from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-big-data-demo.ps1
```

The runner sets Python temp/cache to D, checks C/D before and after execution,
and writes only to `artifacts/big-data`. The verified profile produced
1,050,003 Bronze sensor rows, 1,050,000 Silver/warehouse sensor facts, a
passed quality report, 74 checksums, and a 388.2 MB artifact set. Generated
artifacts are local demo state and must not be committed or exposed as a public
download.

The dashboard's six generated WebP visuals are first-party application assets,
not Docker images and not real customer evidence. Their provenance, hashes,
alt descriptions, and Crop Health disclaimer are maintained in
`dashboard/assets/generated/README.md`. The social-preview source is kept under
`docs/assets/`; uploading it to GitHub Settings is a separate account-level
action.

## Phase 2 analytics API and demo tenant

The internal analytics API serves only checksum-verified, already-aggregated
Gold/quality datasets. It never reads Bronze/Silver or writes artifacts,
SQLite, or PostgreSQL business tables during a request. Every request forwards
the caller bearer to Spring `/api/v1/me`, compares the returned tenant UUID to
the configured demo UUID, and intersects farm/warehouse filters with the live
Spring catalogs.

Install the API extra and provide the non-secret runtime contract:

```powershell
python -m pip install -e ".[api]"
$env:AGRIINSIGHT_ANALYTICS_ARTIFACT_ROOT = (Resolve-Path "artifacts/big-data").Path
$env:AGRIINSIGHT_ANALYTICS_DEMO_TENANT_ID = "20000000-0000-4000-8000-000000000001"
$env:AGRIINSIGHT_ANALYTICS_RECONCILIATION_REPORT = (Resolve-Path "_tmp/demo-bootstrap/reconciliation.json").Path
$env:AGRIINSIGHT_ANALYTICS_SPRING_BASE_URL = "http://127.0.0.1:8080"
$env:AGRIINSIGHT_ANALYTICS_BIND_HOST = "127.0.0.1"
$env:AGRIINSIGHT_ANALYTICS_PORT = "8081"
agriinsight-analytics
```

`AGRIINSIGHT_ANALYTICS_MAX_ARTIFACT_AGE_HOURS` defaults to 48 and
`AGRIINSIGHT_ANALYTICS_MAX_RECONCILIATION_AGE_HOURS` defaults to 24. The
reconciliation report must be regenerated after any demo master, assignment,
or artifact change; a stale, mismatched, or future report keeps readiness
closed. `/health/live` is process-only, `/health/ready` is the reconciliation
gate, and `/internal/v1/*` is GET-only.

The explicit local demo overlay isolates its Compose project as
`agriinsight-demo`, uses `backend/.runtime/postgres-demo`, and starts PostgreSQL
with the server-side marker `app.agriinsight_demo_database=true`. The wrapper
also requires `-ConfirmLocalDemo`, loopback, the exact `agriinsight_demo`
database, a process-only `PGPASSWORD`, and a D-local `_tmp` output directory:

```powershell
$env:PGPASSWORD = "<set in the process; never write it to a file>"
docker compose -f compose.yaml -f compose.backend.yaml -f compose.demo.yaml `
  --profile backend up -d postgres backend-role-bootstrap backend-migrate
powershell -ExecutionPolicy Bypass -File scripts/bootstrap-demo-environment.ps1 `
  -ConfirmLocalDemo -DatabasePort 5432 -OutputDirectory _tmp/demo-bootstrap
```

The generated bundle and reconciliation report must carry the same
`demoTenantId`, `runId`, and `manifestFingerprint`. Keep the report under
ignored `_tmp`; do not commit credentials or generated Big Data files.

## Local web platform verification

The canonical local browser gate starts isolated PostgreSQL/Keycloak
infrastructure, reconciles the demo tenant, runs Spring and FastAPI on owned
loopback ports, starts the production Next server, exercises installed Chrome,
and removes every owned process/container/runtime directory:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-web-e2e-tests.ps1
```

The runner generates database, OIDC, and seven persona passwords in the process
environment only. Work scenarios use the deterministic `field-worker` persona;
the denied-scope scenario uses `supplier`. Do not copy those generated values
to `.env`, source, logs, or test fixtures. A successful run ends with both
`PLAYWRIGHT_E2E=PASS` and `WEB_PLATFORM_E2E=PASS`.

For a narrow local E2E iteration after static gates already passed, use
`-SkipStaticGates`. This is not a release substitute. The full gate remains
contract drift, TypeScript, tests, zero-warning lint, production build, backend
package, database privilege checks, and real-browser scenarios. Stop heavy work
when the disk guard fails below 8 GiB on C or 20 GiB on D.

## Backend database settings

| Environment variable | Purpose | Checked-in default |
|---|---|---|
| `AGRIINSIGHT_DB_URL` | Runtime JDBC URL | local PostgreSQL URL |
| `AGRIINSIGHT_DB_RUNTIME_USERNAME` | Restricted runtime login | `agriinsight_runtime` placeholder |
| `AGRIINSIGHT_DB_RUNTIME_PASSWORD` | Runtime password | empty |
| `AGRIINSIGHT_FLYWAY_ENABLED` | Enable application-driven migration | `false` |
| `AGRIINSIGHT_FLYWAY_URL` | Migration JDBC URL | runtime URL fallback |
| `AGRIINSIGHT_FLYWAY_USERNAME` | Migration owner login | `agriinsight_migrator` placeholder |
| `AGRIINSIGHT_FLYWAY_PASSWORD` | Migration owner password | empty |

Never run the application with the Flyway owner as its runtime identity. The checked-in Phase 3 role gate creates or verifies `agriinsight_migrator`, `agriinsight_runtime`, and the non-login `agriinsight_identity_definer`; it refuses unsafe attributes or forbidden memberships.

## Database role and migration gate

`scripts/run-backend-migrations.ps1` is the only checked-in migration workflow. It runs the disk guard, verifies the exact target, applies the cluster-role gate with a narrowly held operator credential, optionally adopts only the known V1-V3 objects, and then runs Flyway migrate plus validate as `agriinsight_migrator`.

The current schema is Flyway V1-V19 plus repeatable least-privilege helpers/grants; application readiness expects schema version `19`. V7-V11 install fail-closed farm, field/crop/season, Employee, farm-assignment, and activity-season lifecycle guards. V12 creates inventory tables, V13 adds tenant RLS, V14 serializes active profile/warehouse assignments, and V15 adds role-aware inventory read/write RLS plus tenant-leading indexes. V16 creates the append-only operating-cost ledger and V17 adds role/farm-aware cost RLS plus indexes. V18 creates the outbox tables and V19 adds outbox RLS/index policies. Inconsistent upgrade data aborts migration, and rollback preserves ENABLE/FORCE ROW LEVEL SECURITY on affected tables.

Required deployment inputs:

| Environment variable | Purpose |
|---|---|
| `AGRIINSIGHT_DB_HOST`, `AGRIINSIGHT_DB_PORT`, `AGRIINSIGHT_DB_NAME` | Exact guarded PostgreSQL target |
| `AGRIINSIGHT_DB_OPERATOR_USERNAME`, `AGRIINSIGHT_DB_OPERATOR_PASSWORD` | Short-lived role bootstrap credential; must not be the migrator |
| `AGRIINSIGHT_FLYWAY_URL`, `AGRIINSIGHT_FLYWAY_USERNAME`, `AGRIINSIGHT_FLYWAY_PASSWORD` | Migration connection; username must be `agriinsight_migrator` |
| `AGRIINSIGHT_DB_ADOPTION_USERNAME`, `AGRIINSIGHT_DB_ADOPTION_PASSWORD` | Required only for the explicit Phase 1/2 legacy-owner adoption path |

Fresh database:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-migrations.ps1
```

Controlled Phase 1/2 upgrade:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-migrations.ps1 `
  -AdoptLegacyOwnership -LegacyOwner '<verified-legacy-owner>'
```

Do not set `AGRIINSIGHT_FLYWAY_ENABLED=true` in the normal application. Do not replace the adoption allowlist with `REASSIGN OWNED`, and do not pass the operator/adoption credential to Flyway or runtime.

## First tenant provisioning

After migrations, a verified operator runs `backend/ops/postgres/provision-tenant-admin.sql` as the migration owner with psql variables for `tenant_code`, `tenant_display_name`, `admin_display_name`, optional `admin_email`, exact OIDC `issuer`/`subject`, and `correlation_id`. Supply values through deployment automation or a protected operator session, not checked-in files or shell history.

The script takes advisory transaction locks and atomically creates the tenant, first active profile, exact external identity, `TENANT_ADMIN` assignment, and audit event. Duplicate tenant code or identity fails without partial rows. There is intentionally no public first-admin or JWT JIT-provisioning route. Subsequent users are managed through the tenant-admin API.

## OIDC settings

Identity is disabled by default. Enabling it requires the complete provider contract.

| Environment variable | Required when enabled | Contract |
|---|---|---|
| `AGRIINSIGHT_IDENTITY_ENABLED` | yes | `true` enables the resource-server boundary |
| `AGRIINSIGHT_OIDC_ISSUER_URI` | yes | Exact issuer; HTTPS except loopback development |
| `AGRIINSIGHT_OIDC_JWK_SET_URI` | optional | Explicit JWKS endpoint; issuer validation remains mandatory |
| `AGRIINSIGHT_OIDC_API_AUDIENCE` | yes | API resource audience |
| `AGRIINSIGHT_OIDC_INTERACTIVE_CLIENT_ID` | yes | Browser/client ID; must differ from API audience |
| `AGRIINSIGHT_OIDC_CLOCK_SKEW` | no | `0s` to `2m`; default `60s` |
| `AGRIINSIGHT_OIDC_JWS_ALGORITHM` | no | Configured asymmetric algorithm; default `RS256` |
| `AGRIINSIGHT_OIDC_DISCRIMINATOR_LOCATION` | defaulted; verify | `CLAIM` or `HEADER`; default `CLAIM` |
| `AGRIINSIGHT_OIDC_DISCRIMINATOR_NAME` | defaulted; verify | Example: `token_use` or `typ`; default `token_use` |
| `AGRIINSIGHT_OIDC_DISCRIMINATOR_VALUE` | defaulted; verify | Example: `access` or `at+jwt`; default `access` |
| `AGRIINSIGHT_OIDC_DISPLAY_NAME_CLAIM` | no | Default `name` |
| `AGRIINSIGHT_OIDC_EMAIL_CLAIM` | no | Default `email` |
| `AGRIINSIGHT_OIDC_ASSURANCE_CLAIM` | no | Default `acr` |
| `AGRIINSIGHT_CORS_ALLOWED_ORIGINS` | no | Exact comma-delimited HTTP(S) origins; no wildcard/path |

Missing or unsafe enabled settings stop application startup. Tokens must pass signature, configured algorithm, issuer, audience, expiration/not-before, subject, and discriminator checks before the database lookup. Provider roles/scopes do not grant AgriInsight permissions.

## Local backend run

Identity-disabled foundation run:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The default bind is `127.0.0.1`. Expected behavior without PostgreSQL:

- `GET /actuator/health/liveness` -> 200
- `GET /actuator/health/readiness` -> 503
- `/api/v1/**` -> deny by default

Build and smoke the local backend image only after tests pass:

```powershell
docker build --tag agriinsight-backend:local backend
docker run --rm --publish 127.0.0.1:8080:8080 agriinsight-backend:local
```

The image runs as `10001:10001`. A local tag is not release evidence.

## API docs and browser access

`AGRIINSIGHT_API_DOCS_ENABLED=true` exposes OpenAPI/Swagger only under `dev` or `local` profiles. Non-development profiles keep docs private. CORS never uses credentials and accepts only configured exact origins.

The inventory contract is included in `/v3/api-docs`: warehouse/material/supplier
masters, warehouse assignments, balances, lots, transactions, and linked
reversals. `InventoryOpenApiContractTest` verifies the receipt/issue and
reversal operation descriptions and base-unit examples. Do not expose the docs
endpoint publicly in a production profile.

The cost contract is also included when API docs are enabled: bounded
`/api/v1/cost-entries` list/detail, correction, and `/api/v1/cost-summaries`.
Cost responses use the explicit operating-cost lens. A cost correction appends
one reversal and one replacement; there is no delete route and no implicit
inventory/procurement allocation.

## Health and logs

- Liveness measures process state only.
- Readiness includes database reachability and expected Flyway schema version.
- Public health responses use `show-details=never`.
- Security responses are generic Problem Details with correlation IDs.
- Authentication logs contain correlation ID, method, path, reason/fingerprint where available; never Authorization headers, tokens, private keys, or provider diagnostics.
- Tenant-resolved route/service denials persist bounded actor, tenant, target, reason, correlation, and outcome metadata. A service denial is audited only after its rejected business transaction releases the connection.

## Docker Hub release policy

No production registry push is authorized by a successful local build. Hosted run [`29932250984`](https://github.com/JasonTM17/AgriInsight/actions/runs/29932250984) passed 5/5 at commit `8d8463f`; the Temurin 21.0.11 JRE Noble backend image passed Trivy 0.70.0 with zero HIGH/CRITICAL and pull-by-digest smoke. Docker Hub/GHCR phase tags `0.1.0-phase7` and `sha-8d8463f` resolve to `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`. These tags are evidence only: production must still use protected CI, immutable semantic-version/Git-SHA tags, SBOM/provenance, exact-digest scan/smoke, and no automatic `latest`. Do not mirror PostgreSQL or other third-party images.

## Production blockers

- Protected tag-triggered production release environment, secrets, reviewers, and promotion approval
- Production OIDC fixtures, privileged-user MFA policy, exact CORS origins, audit retention/alerting, backup RPO/RTO, and restore ownership
- Encrypted off-host backup destination, retention/key owner, and approved recurring restore-drill schedule

## Unresolved Questions

- Production OIDC provider and exact access-token contract
- Audit retention/alerting owner
- Production Docker Hub namespace/visibility plus least-privilege release-token rotation owner
