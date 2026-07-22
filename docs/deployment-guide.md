# Deployment Guide

This guide documents the verified local/runtime contracts through Backend Phase 7 core. It is not a production deployment approval: protected registry release, environment review, and a scheduled recovery drill remain required before production.

## Supported execution boundaries

| Component | Current use | Exposure |
|---|---|---|
| Python pipeline/dashboard | Local analytics MVP | Dashboard binds locally; do not expose publicly |
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

The current schema is Flyway V1-V17 plus repeatable least-privilege helpers/grants; application readiness expects schema version `17`. V7-V11 install fail-closed farm, field/crop/season, Employee, farm-assignment, and activity-season lifecycle guards. V12 creates inventory tables, V13 adds tenant RLS, V14 serializes active profile/warehouse assignments, and V15 adds role-aware inventory read/write RLS plus tenant-leading indexes. V16 creates the append-only operating-cost ledger and V17 adds role/farm-aware cost RLS plus indexes. Inconsistent upgrade data aborts migration, and rollback preserves ENABLE/FORCE ROW LEVEL SECURITY on affected tables.

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

No registry push is authorized by a successful local build. Phase 7 must run protected Java 21 CI, dependency/image scan, SBOM/provenance, immutable semantic-version and Git-SHA tags, and smoke the exact pulled digest. Do not mutate `latest` automatically. Do not mirror PostgreSQL or other third-party images.

## Production blockers

- Phase 7 outbox, protected CI, image supply-chain, backup/restore, and release gates
- Production OIDC fixtures, privileged-user MFA policy, exact CORS origins, audit retention/alerting, backup RPO/RTO, and restore ownership
- Protected Java 21 CI, dependency/image scan, SBOM/provenance, immutable registry publication, and pulled-digest smoke test

## Unresolved Questions

- Production OIDC provider and exact access-token contract
- Audit retention/alerting owner
- Docker Hub namespace, visibility, and least-privilege release token
