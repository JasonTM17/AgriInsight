# Deployment Guide

This guide documents the verified runtime contracts through the public
[`v0.4.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0)
release, Backend Phase 7 core, and the merged Phase 3 hosted browser
acceptance. The tag object
`4c27b343eecd32cf7daac462e5f661011e2af0df` peels to main SHA
`616527dcc7f4a03720fb48e617f9310ab9614873`; exact-head CI
[`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
passed 10/10 and protected four-image publication
[`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
passed 4/4. This is not an external production deployment approval:
production OIDC/broker operations, a scheduled recurring recovery drill,
RPO/RTO/retention ownership, hostname/TLS, and host controls remain required.

## Supported execution boundaries

| Component | Current use | Exposure |
|---|---|---|
| Python pipeline/dashboard | Local analytics MVP | Dashboard binds locally; do not expose publicly |
| Internal analytics API | FastAPI read-only aggregate surface | Loopback/internal network only; Spring `/api/v1/me` remains the authorization source |
| Next web platform | Nine-area hosted browser gate and `v0.4.0` image release passed | Digest-pinned private/internal deployment; external hosting remains owner-gated |
| Java backend, identity disabled | Foundation/health verification | Loopback or loopback-published container only |
| Java backend, identity enabled | Locally verified OIDC, tenant RBAC/RLS, and tenant administration | Keep private until production IdP/operations and later domain/release gates pass |
| Isolated alert worker | Disabled by default; internal metadata-only alert slice | Private only; compose overlay binds broker to loopback and runs the alert observer internally; the Phase 2 feed/ack API and Phase 3 browser panel passed hosted CI acceptance (PR #13 / `30425647823`; PR #14 / `30445148252`) but this neither approves production hosting nor publishes a new image |
| Next web + analytics API images | `0.4.0` and full-SHA tags are published after protected approval | Digest-pinned, loopback/private deployment only; publication is not production hosting approval |
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

## Alert-center rollback

Rollback for alert-center changes is ordered: stop the isolated
`realtime-alert-worker` and any evaluator first, then redeploy the previous
BFF/web image digest. Keep the alert rows and migrations in place; rollback is
an image/code change, not a table wipe. Do not rely on flipping a checked-in
Compose environment value when a service hard-codes its behavior.

The dashboard's 8 contextual AI visuals are first-party application assets,
not Docker images and not real customer evidence. Their provenance, hashes,
alt descriptions, and Crop Health disclaimer are maintained in
`dashboard/assets/generated/README.md`. The 14 hosted CI screenshots are kept
separately under `docs/assets/screens/` and trace back to Actions run
`30885890858`. The tracked 1280x640 social-preview source under
`docs/assets/agriinsight-social-preview.jpg` is uploaded to GitHub and its
public metadata object matches the source SHA-256 exactly; see the
[verification report](../plans/260803-2156-portfolio-media-completion/reports/social-preview-verification.md).

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

When the inventory forecast code, movement seed, or Gold schema changes, the
API deployment must use freshly regenerated `gold/inventory_demand_forecast.csv`
and `gold/inventory_status.csv` from the matching run, plus the updated
manifest. The analytics snapshot cache checks the exact manifest fingerprint
and validates the extended inventory forecast contract at load time; stale
forecast artifacts or a mismatched manifest fail closed before the API can
serve readiness.

The public Inventory response keeps authorization and warehouse filtering ahead
of shaping. Forecast evidence is nested per item, status counters are scoped and
label-free, ABC/alerts/items are capped at 100, and the exact serialized UTF-8
body must remain at or below 1 MiB. An oversized body fails with sanitized
`503 analytics_response_too_large`; do not raise the BFF limit to mask the
failure.

Rollback is contract-level: deploy the previous matching analytics/web image
pair and its matching Gold manifest rather than serving a new snapshot through
an old generated client. Keep the legacy run-rate policy fields during rollback.
If forecast evidence cannot be verified, use explicit `unavailable` null
evidence or keep analytics readiness closed; never synthesize browser values.

Yield delivery additionally requires the matching checksummed
`gold/yield_forecast.csv` and manifest. The internal read route is exactly
`GET /internal/v1/yield-forecast`; it accepts only `farm_code`, `field_code`,
`crop_code`, `season_code`, `limit`, and `offset`, applies fixed
`expected_harvest_date ASC, season_code ASC` ordering, caps `limit` at 100 and
the serialized response at 1 MiB. Do not add a browser-selected tenant, model,
date preset, or sort; deploy the matching analytics/web pair and Gold manifest
on rollback.

This is an internal authenticated read boundary, not an external production
promotion. Ingress rate limiting and successful-read audit retention are
deployment-owned controls: they were not proven by this application-source
acceptance and must be configured, observed, and retained before external use.

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

### Local mock latency evaluation

Use the dedicated local harness when you need a quick latency sanity check for
assistant telemetry:

```powershell
python scripts/run-assistant-latency-evaluation.py
```

This command is mock-only. It uses in-memory telemetry, synthetic evidence, and
local aggregate summarization only; it does not call DeepSeek, load a provider
key, read any provider/network secret, or change runtime assistant settings.
The JSON output is aggregate-only (`sample_count`, `p50_ms`, `p95_ms`, and
`outcome_counts`). Any local high-resolution elapsed-time measurements observed
by the service during this run are local execution measurements, not
hosted-provider SLO evidence. Hosted latency, groundedness, and provider
spend-owner gates remain open. Release `v0.3.0` does not change those
provider/production boundaries.

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
from a protected process environment or secret manager. Copy
`deploy/production-promotion-evidence.template.json` to a protected deployment
workspace (never Git), fill only approved non-secret references, and keep the
manifest at `format_version: 3`. v2 promotion manifests are rejected by the
validator, so operators must always start from the v3 template rather than
trying to recycle an older record. Every approval entry must include `owner`,
`approval_ref`, `approved_at_utc`, `due_at_utc`, `unlock_criterion`, and
`rollback_responsibility`; unknown controls or additional approval properties
are rejected. Then run the only supported promotion entrypoint before any
release Compose command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-production-release-compose.ps1 `
  -EvidenceFile 'D:\secure-deployment\production-promotion-evidence.json' `
  -Mode Validate
```

The entrypoint unconditionally rejects a mutable tag, incomplete/expired
approval record, v2 manifest, missing recovery evidence, non-first-party
image, or a mismatch between the evidence record and the four active image
variables. It verifies exact GitHub Actions run metadata before contacting
Docker. It then pulls each selected digest and verifies OCI
source/revision/version labels, provenance/SBOM attestations, paired Docker
Hub/GHCR semantic/full-SHA tag parity, and the release topology. It is a
release-control check, not an external hosting approval.

The evidence record must keep target data non-secret: the approved Docker
context, the lowercase SHA-256 fingerprint of that context's Docker endpoint,
and the fixed Compose deployment identity `agriinsight-release`. Derive the
fingerprint from the exact UTF-8 endpoint returned by
`docker context inspect <context> --format '{{.Endpoints.docker.Host}}'`.
Set `AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT` to the same approved context that
appears in the evidence file before invoking the release entrypoint. The
entrypoint compares the active context and endpoint fingerprint, then scopes
every Compose command to that fixed project. This is target binding, not a
substitute for access authorization or hosting approval.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-production-release-compose.ps1 `
  -EvidenceFile 'D:\secure-deployment\production-promotion-evidence.json' `
  -Mode Deploy -ConfirmProductionChange
```

Do not use a direct `docker compose ... up` invocation as production promotion
evidence: it bypasses the supported preflight and cannot support a GO decision.
For a rollback, the approved manifest must either name a complete earlier
release and four prior digests, or explicitly authorize disable-exposure. The
same entrypoint verifies the prior release before changing state. The
disable-exposure path skips GitHub/registry lookup and is only for an approved
exposure shutdown:

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/start-production-release-compose.ps1 `
  -EvidenceFile 'D:\secure-deployment\production-promotion-evidence.json' `
  -Mode Rollback -ConfirmProductionChange
~~~

Redeploy rollback waits for dashboard, backend, analytics, and web health and
requires the stopped pipeline container to exit `0`. Disable-exposure validates
the local target first, then uses the approved release Compose project only,
stops it without deleting volumes, and verifies that no project containers
remain. If no matching project containers exist, it returns
`status=ALREADY_DISABLED` rather than claiming a new shutdown proof.

The overlay requires digest-pinned values for
`AGRIINSIGHT_PYTHON_IMAGE`, `AGRIINSIGHT_BACKEND_IMAGE`,
`AGRIINSIGHT_WEB_IMAGE`, and `AGRIINSIGHT_ANALYTICS_API_IMAGE`. It orders
backend role bootstrap/migration/readiness, separate web role bootstrap and
migration, analytics readiness, then web liveness. PostgreSQL remains the
digest-pinned upstream image and must not be republished.

The v3 promotion manifest keeps unresolved controls out of the evidence path:
`UNASSIGNED — NO-GO` stays a documentation-only marker in the control record,
not a valid approval value.

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

The generic backend readiness schema is Flyway V30 plus repeatable
least-privilege helpers/grants. The alert-worker startup gate separately pins
successful V28 and the latest repeatable `R__tenant_rls_helpers_and_grants.sql`;
`AGRIINSIGHT_SCHEMA_EXPECTED_VERSION` only drives backend health/readiness and
cannot weaken the worker gate. V7-V11 install fail-closed farm,
field/crop/season, Employee, farm-assignment, and activity-season lifecycle
guards. V12 creates inventory tables, V13 adds tenant RLS, V14 serializes
active profile/warehouse assignments, and V15 adds role-aware inventory
read/write RLS plus tenant-leading indexes. V16 creates the append-only
operating-cost ledger and V17 adds role/farm-aware cost RLS plus indexes. V18
creates the outbox tables, V19 adds outbox RLS/index policies, V20 adds
tenant-scoped realtime read models, and V21 adds the tenant summary index.

The official upgrade proof reconstructs V1-V22 from release commit
`6927eeda70981c2461e85a165834e2464ba793d1` plus the historical repeatable
grant file, then applies current V23-V30 plus repeatable grants. It validates,
reruns zero-op, preserves two representative legacy invalid rows and the V23
`NOT VALID` constraints, and does not perform the backfill.

`V22__create_realtime_operational_alerts.sql` is immutable. `V23` is additive
only: it adds the metadata evidence checks as `NOT VALID`, durable alert scan
cursors, and the restricted worker policies; it deliberately does not run a
table-wide update, validate legacy rows, or make `source_occurred_at` `NOT
NULL`. `V24`, `V25`, `V26`, and `V27` each create exactly one alert scan index
with `CREATE INDEX CONCURRENTLY`, respectively for outbox backlog,
published-without-receipt delivery lag, open unrecovered DLT alerts, and a
readiness-only partial invalid-source-evidence index. Transactional V28 then
replaces the acknowledgement function with the same signature and security
contract while targeting its named unique constraint, avoiding the ambiguous
PL/pgSQL conflict target without rewriting V22. V29 locks acknowledgement to
OPEN alerts and V30 adds the latest-open-feed index. The default
`AGRIINSIGHT_SCHEMA_EXPECTED_VERSION` is `30`; it is a readiness contract
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

V24-V27 run outside a Flyway transaction through their adjacent versioned
`.sql.conf` files (`executeInTransaction=false`); the checked-in migration
workflow also disables PostgreSQL's transactional advisory lock for these
concurrent-index migrations. For each migration, its named index must be absent
first. If a failed build leaves that index invalid, run the matching `DROP
INDEX CONCURRENTLY` below, then repair/retry Flyway in the approved migration
workflow. If the index is already valid, reconcile Flyway history with the
operator; do not retry the migration. Receipt recording and DLT source
attribution share a transaction-scoped per-event PostgreSQL advisory lock, so
the DLT path waits and rechecks receipt in the same database transaction
snapshot; that is serialization, not exactly-once or broker ordering.

| Migration | Index | Invalid-index recovery command |
|---|---|---|
| V24 | `ix_outbox_events_alert_backlog` | `DROP INDEX CONCURRENTLY ix_outbox_events_alert_backlog` |
| V25 | `ix_outbox_events_alert_delivery_lag` | `DROP INDEX CONCURRENTLY ix_outbox_events_alert_delivery_lag` |
| V26 | `ix_realtime_operational_alerts_unrecovered_dlt` | `DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_unrecovered_dlt` |
| V27 | `ix_realtime_operational_alerts_invalid_source_evidence` | `DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_invalid_source_evidence` |
| V30 | `ix_realtime_operational_alerts_tenant_open_feed` | `DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_tenant_open_feed` |

Required deployment inputs:

| Environment variable | Purpose |
|---|---|
| `AGRIINSIGHT_DB_HOST`, `AGRIINSIGHT_DB_PORT`, `AGRIINSIGHT_DB_NAME` | Exact guarded PostgreSQL target |
| `AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE` | Dedicated lowercase `agriinsight_restore_*` target for a local/staging restore drill; it must differ from the backup source database |
| `AGRIINSIGHT_RESTORE_DRILL_HOST`, `AGRIINSIGHT_RESTORE_DRILL_PORT`, `AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS` | Separately configured literal `127.0.0.1` restore-drill endpoint and exact host allowlist; remote staging remains blocked until verified TLS is approved |
| `AGRIINSIGHT_DB_OPERATOR_USERNAME`, `AGRIINSIGHT_DB_OPERATOR_PASSWORD` | Short-lived role bootstrap credential; must not be the migrator |
| `AGRIINSIGHT_FLYWAY_URL`, `AGRIINSIGHT_FLYWAY_USERNAME`, `AGRIINSIGHT_FLYWAY_PASSWORD` | Migration connection; username must be `agriinsight_migrator` |
| `AGRIINSIGHT_DB_ADOPTION_USERNAME`, `AGRIINSIGHT_DB_ADOPTION_PASSWORD` | Required only for the explicit Phase 1/2 legacy-owner adoption path |
| `AGRIINSIGHT_SCHEMA_EXPECTED_VERSION` | Keep at `30` unless a later reviewed migration changes the readiness contract |
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

No registry push is authorized by a successful local build. Release
[`v0.2.3`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.2.3) uses
main commit `3e72ab5226a17d85fc42cb4f0cacb1900a416a1a`. Main CI
[`30413064146`](https://github.com/JasonTM17/AgriInsight/actions/runs/30413064146)
and protected publication
[`30413877863`](https://github.com/JasonTM17/AgriInsight/actions/runs/30413877863)
passed for Python, backend, web, and analytics API. Each semantic `0.2.3` tag
and full-SHA tag resolves to the same exact digest in Docker Hub and GHCR.
There is no automatic `latest`; do not mirror PostgreSQL or other third-party
images.

Release [`v0.3.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.3.0)
at `eabf209` passed all ten main CI jobs in
[`30452477234`](https://github.com/JasonTM17/AgriInsight/actions/runs/30452477234),
then passed protected four-image publication in
[`30453840056`](https://github.com/JasonTM17/AgriInsight/actions/runs/30453840056).
Each image was independently scanned, provenance/SBOM-validated, pulled and
smoke-tested by exact digest, and matched across Docker Hub/GHCR.

Release [`v0.4.0`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.4.0)
at `616527dcc7f4a03720fb48e617f9310ab9614873` passed exact-head CI
[`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
10/10, then protected publication
[`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
4/4. Semantic and full-SHA tags were inspected independently in both
registries and resolved to these immutable digests:

| Image | Digest |
|---|---|
| Python | `sha256:0c4889671ce010e8d806f949d508c69938d55effa2429e428e71ba5e7ef77420` |
| Backend | `sha256:c8a21a01b83386d75d4f259103245dbf8f7ffa0730a2ac9ee4e39686c407f3d9` |
| Web | `sha256:da49816d51c349391676b7800beffb5270fd27186be3e1d3b9e95aa128fbc345` |
| Analytics API | `sha256:ce0ff7e0d40ad2851355b2274b729059677380d0351b51993582377316928c02` |

The public GitHub Release was published at `2026-08-01T12:01:05Z`. This is
registry evidence, not external deployment approval. Docker Hub/GHCR parity is
proven for all 16 semantic/full-SHA references, but do not overclaim
attestation signature/content or Docker Hub referrer parity.

Release [`v0.3.1`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.3.1)
at `7f669bc6907b483a87b27d397ceb3453b3bec01f` passed exact-head CI
[`30506056691`](https://github.com/JasonTM17/AgriInsight/actions/runs/30506056691)
10/10 and protected publication
[`30506807548`](https://github.com/JasonTM17/AgriInsight/actions/runs/30506807548)
4/4. Semantic `0.3.1` and full-SHA tags were inspected independently in both
registries and resolved to these immutable digests:

| Image | Digest |
|---|---|
| Python | `sha256:325c43937580febde3b95fe908eac58b62073a2407d6efb0f471adb79bdab32f` |
| Backend | `sha256:43764cc3d6aafe54cd2d2acde9af4ed195bb4f6b56b744ec8aba5142f98e3909` |
| Web | `sha256:1065ab6b202a04a7712cd15f2b83180f39a961b53c94f0f47c790b003aaaa945` |
| Analytics API | `sha256:9452becc98009e72e7b32407c58619fae7a088b8ca19310d037d74b3838a23f9` |

The tag-triggered workflow covers Python, backend, web, and analytics API
serially (`max-parallel: 1`). It scans and smokes a local candidate before
registry authentication, then publishes both registries with BuildKit
provenance/SBOM and repeats scan/smoke against the returned digest. The
`release-images` environment, reviewer policy, `DOCKERHUB_USERNAME`, and
`DOCKERHUB_TOKEN` are configured and approved per immutable tag. Current
external-deployment owner decisions remain tracked in the
[production-readiness control matrix](production-readiness.md); the dated
2026-07-27 owner handoff is retained only as a historical snapshot.

All four GHCR packages are linked to `JasonTM17/AgriInsight` and remain private.
The configured `GHCR_TOKEN` is an environment-scoped legacy-package
compatibility credential, never a repository secret or image build argument.

## Production blockers

- The current external-deployment verdict is **NO-GO**. Track the owner,
  deadline, approval reference, and unlock criterion in
  [production readiness](production-readiness.md); an unassigned row blocks
  promotion.
- [Production NO-GO issue #22](https://github.com/JasonTM17/AgriInsight/issues/22)
  assigns the decision request to the repository owner through
  `2026-08-10T10:00:00Z`. This is coordination ownership only; it does not
  approve or assign any production control.
- The repository now carries an MIT license; candidate images intentionally
  omit an OCI license label until the next approved release records the legal
  decision and label policy in its promotion evidence
- Production OIDC fixtures, privileged-user MFA policy, exact CORS origins, audit retention/alerting, backup RPO/RTO, and restore ownership
- Encrypted off-host backup destination, retention/key owner, and approved recurring restore-drill schedule
- External host, hostname/TLS, broker operations owner, observability, rollback
  authority, and digest-pinned production deployment approval

## Unresolved Questions

- Production OIDC provider and exact access-token contract
- Audit retention/alerting owner
- GHCR visibility decision plus least-privilege release-token rotation owner
