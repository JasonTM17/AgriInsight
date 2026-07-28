# Deployment Guide

This guide documents the verified runtime contracts through the production-web
internal release candidate and Backend Phase 7 core. It is not a production
deployment approval: protected registry release, environment review, a
scheduled recovery drill, and production release/recovery approvals remain
required before production.

## Supported execution boundaries

| Component | Current use | Exposure |
|---|---|---|
| Python pipeline/dashboard | Local analytics MVP | Dashboard binds locally; do not expose publicly |
| Internal analytics API | FastAPI read-only aggregate surface | Loopback/internal network only; Spring `/api/v1/me` remains the authorization source |
| Next web platform | Eight-area hosted browser gate passed | Loopback/private internal candidate; external promotion remains protected-gated |
| Java backend, identity disabled | Foundation/health verification | Loopback or loopback-published container only |
| Java backend, identity enabled | Locally verified OIDC, tenant RBAC/RLS, and tenant administration | Keep private until production IdP/operations and later domain/release gates pass |
| Isolated alert worker | Disabled by default; internal metadata-only alert slice | Private only; compose overlay binds broker to loopback, runs the alert observer internally, and exposes no broker/public worker API; production broker ownership remains gated |
| Next web + analytics API images | Hosted-CI release candidate | Digest-pinned, loopback-published deployment only; registry publication remains protected-gated |
| PostgreSQL 18 | Upstream Testcontainers dependency | Never mirror/push as an AgriInsight image |

## Preflight

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
```

`verify` requires Docker and runs the mandatory PostgreSQL 18 integration gate. Maven repository, temp, and user-home paths must resolve to D. Do not pass test-skip/fail-masking flags.
Local heavy verification starts only when the script prints `DISK_GUARD overall=PASS`; WARN still exits `0` and is not sufficient for backend/realtime Docker work.

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

The dashboard's eight generated WebP visuals are first-party application assets,
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
closed. `/health/live` is process-only and `/health/ready` is the
reconciliation gate. Analytics reads remain GET-only; the optional assistant
adds one authenticated `POST /internal/v1/assistant/query`.

## DeepSeek RAG assistant

The assistant is disabled by default. Enable it only on the internal analytics
service after the snapshot/reconciliation and Spring scope gates are healthy:

```powershell
$env:AGRIINSIGHT_ASSISTANT_ENABLED = "true"
$env:AGRIINSIGHT_LLM_API_KEY = "<read from a protected secret source>"
agriinsight-analytics
```

The provider contract is intentionally fixed to
`https://api.deepseek.com` + `deepseek-v4-flash`, with thinking disabled.
`AGRIINSIGHT_LLM_PROVIDER`, `AGRIINSIGHT_LLM_BASE_URL`,
`AGRIINSIGHT_LLM_MODEL`, and `AGRIINSIGHT_LLM_THINKING_ENABLED` fail closed if
changed. Runtime budgets are documented in `.env.example`: 3-second connect,
25-second provider read, 2-second queue wait, 1,200 output tokens, 8 evidence
items, 12,000 evidence characters, and 8 concurrent provider calls by default.
The process-local tenant guard defaults to 30 requests per minute and
1,000,000 tokens per UTC day, reserving 10,000 tokens for each in-flight
provider call so concurrent requests cannot oversubscribe the budget. This
guard is defense in depth, not distributed billing: every multi-replica
deployment must also configure a provider-account spend limit and an
environment-owned alert receiver.

Never pass the key as a Docker build argument, checked-in Compose literal,
browser environment variable, screenshot, test fixture, log, or CI artifact.
Inject it into the runtime environment from the deployment secret manager.
Rotate it immediately if it
appears in Git history or external logs. The telemetry event contains only
correlation ID, outcome/refusal/provider code, latency, retrieval count, and
token counters; it intentionally excludes question, history, evidence, answer,
tenant UUID, user identity, and key.

Rollback is one environment change:

```powershell
$env:AGRIINSIGHT_ASSISTANT_ENABLED = "false"
```

Restart the analytics service after changing the flag. The route then returns
404 because it is not registered; no conversation data requires cleanup.
Process-local quota counters reset on restart, so do not treat them as the
authoritative cross-replica spend ledger.

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

## Web and analytics release candidate

The first-party web and analytics API Dockerfiles are
`deploy/docker/web.Dockerfile` and
`deploy/docker/analytics-api.Dockerfile`. Both configure UID/GID `10001`,
support a read-only root filesystem with an explicit `/tmp` tmpfs, and expose
process-only liveness probes. CI builds and scans them without registry
credentials after the complete browser gate.

Deploy only immutable `image@sha256:...` coordinates. Set all required values
from a protected process environment or secret manager, then validate the
release topology:

```powershell
docker compose -f compose.yaml -f compose.backend.yaml `
  -f deploy/compose.release-overlay.yaml --profile backend config --quiet
```

The overlay requires digest-pinned values for
`AGRIINSIGHT_PYTHON_IMAGE`, `AGRIINSIGHT_BACKEND_IMAGE`,
`AGRIINSIGHT_WEB_IMAGE`, and `AGRIINSIGHT_ANALYTICS_API_IMAGE`. It orders
backend role bootstrap/migration/readiness, separate web role bootstrap and
migration, analytics readiness, then web liveness. PostgreSQL remains the
digest-pinned upstream image and must not be republished.

For the opt-in learning demo on Docker Desktop, include the demo database
marker and real Keycloak issuer overlays:

```powershell
docker compose -f compose.yaml -f compose.backend.yaml -f compose.demo.yaml `
  -f compose.web-e2e.yaml -f deploy/compose.release-overlay.yaml `
  -f deploy/compose.web-demo-overlay.yaml --profile backend config --quiet
```

The demo path runs the named `big-data` profile, guarded SQL bundle, database
seed, one-to-one reconciliation, seven environment-only OIDC persona
passwords, and service health ordering. `host.docker.internal` is intentional
for the browser-visible Docker Desktop issuer. Generated artifacts and
bootstrap reports stay ignored on D; no customer data or committed credential
is involved.

Do not start either topology until the disk guard passes. On this workstation,
heavy image/demo execution stays on hosted CI while C or D is below the
documented warning floor.

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

The current expected schema is Flyway V26 plus repeatable least-privilege
helpers/grants. V7-V11 install fail-closed farm, field/crop/season, Employee,
farm-assignment, and activity-season lifecycle guards. V12 creates inventory
tables, V13 adds tenant RLS, V14 serializes active profile/warehouse
assignments, and V15 adds role-aware inventory read/write RLS plus
tenant-leading indexes. V16 creates the append-only operating-cost ledger and
V17 adds role/farm-aware cost RLS plus indexes. V18 creates the outbox tables,
V19 adds outbox RLS/index policies, V20 adds tenant-scoped realtime read
models, and V21 adds the tenant summary index.

`V22__create_realtime_operational_alerts.sql` is immutable. `V23` is additive
only: it adds the metadata evidence checks as `NOT VALID`, durable alert scan
cursors, and the restricted worker policies; it deliberately does not run a
table-wide update, validate legacy rows, or make `source_occurred_at` `NOT
NULL`. `V24`, `V25`, `V26`, and `V27` each create exactly one alert scan index
with `CREATE INDEX CONCURRENTLY`, respectively for outbox backlog,
published-without-receipt delivery lag, open unrecovered DLT alerts, and a
readiness-only partial invalid-source-evidence index. The default
`AGRIINSIGHT_SCHEMA_EXPECTED_VERSION` is `27`; it is a readiness contract
check, never a bypass for unmigrated databases.

### Alert-worker pre-enable and concurrent-index recovery

After V23 is applied and before enabling `realtime-alert-worker`, an operator
must run `backend/ops/postgres/backfill-realtime-alert-source-evidence.sql` as
`agriinsight_migrator` through a protected `psql` session. Each idempotent
invocation locks at most 500 valid legacy rows with `SKIP LOCKED`, sets only a
missing `source_occurred_at` from `opened_at`, and reports `rows_backfilled`.
Run it until no rows are backfilled and both final checks are `false`:
`legacy_source_occurred_at_rows_remain` and
`invalid_source_evidence_shape_rows_remain`. A true invalid-shape result needs
operator correction or retirement; the script intentionally never rewrites
`source_event_id`. Do not enable the worker earlier. V27 does not replace this
backfill or validate the constraints; it only adds the readiness index over
invalid source-evidence rows.

V24-V27 run outside a Flyway transaction. For each migration, its named index
must be absent first. If a failed build leaves that index invalid, run the
matching `DROP INDEX CONCURRENTLY` below, then repair/retry Flyway in the
approved migration workflow. If the index is already valid, reconcile Flyway
history with the operator; do not retry the migration.

| Migration | Index | Invalid-index recovery command |
|---|---|---|
| V24 | `ix_outbox_events_alert_backlog` | `DROP INDEX CONCURRENTLY ix_outbox_events_alert_backlog` |
| V25 | `ix_outbox_events_alert_delivery_lag` | `DROP INDEX CONCURRENTLY ix_outbox_events_alert_delivery_lag` |
| V26 | `ix_realtime_operational_alerts_unrecovered_dlt` | `DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_unrecovered_dlt` |
| V27 | `ix_realtime_operational_alerts_invalid_source_evidence` | `DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_invalid_source_evidence` |

Required deployment inputs:

| Environment variable | Purpose |
|---|---|
| `AGRIINSIGHT_DB_HOST`, `AGRIINSIGHT_DB_PORT`, `AGRIINSIGHT_DB_NAME` | Exact guarded PostgreSQL target |
| `AGRIINSIGHT_DB_OPERATOR_USERNAME`, `AGRIINSIGHT_DB_OPERATOR_PASSWORD` | Short-lived role bootstrap credential; must not be the migrator |
| `AGRIINSIGHT_FLYWAY_URL`, `AGRIINSIGHT_FLYWAY_USERNAME`, `AGRIINSIGHT_FLYWAY_PASSWORD` | Migration connection; username must be `agriinsight_migrator` |
| `AGRIINSIGHT_DB_ADOPTION_USERNAME`, `AGRIINSIGHT_DB_ADOPTION_PASSWORD` | Required only for the explicit Phase 1/2 legacy-owner adoption path |
| `AGRIINSIGHT_SCHEMA_EXPECTED_VERSION` | Keep at `27` unless a later reviewed migration changes the readiness contract |
| `AGRIINSIGHT_DB_ALERT_WORKER_PASSWORD` | Compose-only password input for the separate `agriinsight_alert_worker` login; never commit or expose it |

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
reversals. Inventory OpenAPI contract coverage verifies the receipt/issue and
reversal operation descriptions plus base-unit examples. Do not expose the docs
endpoint publicly in a production profile.

The cost contract is also included when API docs are enabled: bounded
`/api/v1/cost-entries` list/detail, correction, and `/api/v1/cost-summaries`.
Cost responses use the explicit operating-cost lens. A cost correction appends
one reversal and one replacement; there is no delete route and no implicit
inventory/procurement allocation.

The realtime contract is also included when API docs are enabled: `GET /api/v1/realtime/summary` is registered only when identity is enabled, requires `REALTIME_READ`, stays tenant-scoped, and returns at most 100 payload-free metric groups plus freshness metadata. It does not expose raw Kafka payloads, broker coordinates, checksums, or cross-tenant data.

## Health and logs

- Liveness measures process state only.
- Readiness includes database reachability and the expected Flyway schema boundary.
- Public health responses use `show-details=never`.
- Security responses are generic Problem Details with correlation IDs.
- Authentication logs contain correlation ID, method, path, reason/fingerprint where available; never Authorization headers, tokens, private keys, or provider diagnostics.
- Tenant-resolved route/service denials persist bounded actor, tenant, target, reason, correlation, and outcome metadata. A service denial is audited only after its rejected business transaction releases the connection.

## Docker Hub release policy

No production registry push is authorized by a successful local build. Hosted run [`29932250984`](https://github.com/JasonTM17/AgriInsight/actions/runs/29932250984) passed 5/5 at commit `8d8463f`; the Temurin 21.0.11 JRE Noble backend image passed Trivy 0.70.0 with zero HIGH/CRITICAL and pull-by-digest smoke. Docker Hub/GHCR phase tags `0.1.0-phase7` and `sha-8d8463f` resolve to `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`. These tags are evidence only: production must still use protected CI, immutable semantic-version/Git-SHA tags, SBOM/provenance, exact-digest scan/smoke, and no automatic `latest`. Do not mirror PostgreSQL or other third-party images.

The same tag-triggered workflow now covers Python, backend, web, and analytics
API serially (`max-parallel: 1`). It scans and smokes a local candidate before
registry authentication, then publishes both registries with BuildKit
provenance/SBOM and repeats scan/smoke against the returned digest. New web and
analytics packages are release targets only until the `release-images`
environment, reviewers, `DOCKERHUB_USERNAME`, and `DOCKERHUB_TOKEN` are
configured and an exact tag is approved. See the
[repository-owner handoff](../plans/260722-2342-production-web-platform/reports/github-social-preview-owner-handoff.md).

The default GHCR path uses the workflow `GITHUB_TOKEN` and requires each
container package to be linked to this repository. If a legacy user-scoped
package is intentionally retained but not linked, provide a narrowly scoped
`GHCR_TOKEN` as an environment secret; it is a compatibility fallback, never a
repository secret or image build argument.

## Production blockers

- Protected tag-triggered production release environment, secrets, reviewers, and promotion approval
- Docker Hub/GHCR visibility and protected publication evidence for the new web and analytics API packages
- Repository license decision; candidate images intentionally omit a license
  label until a root license is selected
- Production OIDC fixtures, privileged-user MFA policy, exact CORS origins, audit retention/alerting, backup RPO/RTO, and restore ownership
- Encrypted off-host backup destination, retention/key owner, and approved recurring restore-drill schedule

## Unresolved Questions

- Production OIDC provider and exact access-token contract
- Audit retention/alerting owner
- Production Docker Hub namespace/visibility plus least-privilege release-token rotation owner
