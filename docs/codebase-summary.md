# Codebase Summary

Verified snapshot: 2026-08-04 (inventory-demand and yield forecast Phases 1–3
are accepted through scoped API/browser/media evidence; the protected
four-image `v0.4.0` release remains the latest published registry evidence;
the 14 hosted CI screenshots and 8 contextual AI visuals are tracked
separately; external production deployment remains owner-gated)

## Repository shape

| Path | Responsibility |
|---|---|
| `src/agriinsight/` | Deterministic Bronze/Silver/Gold pipeline, quality, warehouse, KPI, insight, and report services |
| `dashboard/` | Streamlit analytics dashboard composition and contextual visual catalog |
| `web/` | Next 16 App Router/BFF, opaque session auth, and nine permission-driven product areas including the RAG assistant |
| `tests/` | Python pipeline, KPI, dashboard, export, visual-asset, security-boundary, and disk-guard tests |
| `backend/` | Java 21 Spring Boot operational backend, PostgreSQL migrations, and transactional outbox |
| `deploy/` | Non-root web/analytics Dockerfiles, digest-pinned release overlay, and real-OIDC container demo overlay |
| `scripts/` | C/D disk guard, guarded backend/browser verification, big-data demo runner, and image smoke/SBOM helpers |
| `plans/` | CK phase plans, design contracts, reports, and acceptance evidence |
| `docs/` | Evergreen architecture, operations, standards, contracts, and roadmap |

## Analytics plane

The validated MVP generates synthetic operational sources, preserves Bronze,
normalizes/quarantines into Silver, atomically loads a SQLite star schema,
materializes Gold KPI/alert contracts, and renders Executive, Farm Performance,
Inventory, Cost Analysis, Crop Health, and Data Quality views. Controlled
CSV/PDF and capability-gated XLSX exports use validated Gold data and
deterministic lineage.

The analytics plane owns `artifacts/`, its manifest, Gold CSVs, and the SQLite
warehouse. It does not write PostgreSQL operational state. The CLI keeps a fast
`standard` profile and a guarded `big-data` profile (10 farms, 120 fields,
365 days, 24 readings/day); the manifest stores resolved dimensions and a
configuration-fingerprinted run identity.

The dashboard uses 8 contextual AI visuals in `dashboard/assets/generated/`
while `docs/assets/screens/` holds 14 hosted CI screenshots from Actions run
`30868766788`. The contextual visuals are demo artwork rather than source
facts; Crop Health marks its image as AI-generated demo evidence and never
assigns it an observation ID. The local Streamlit theme follows the Field
Ledger palette from the CK FE design system.

## Inventory demand forecasting

`src/agriinsight/inventory_demand_forecast*.py` adds a Python-only deterministic
30-day inventory-demand baseline over warehouse/material OUT movements. The
contract caps history at 180 days, uses the latest 90 days for the point
forecast, publishes empirical 30-day p10/p90 planning bounds, and reports
weekly rolling-origin MAE/WAPE with explicit `ready`, `no_demand`, and
`insufficient_history` states. Validation fails closed on malformed dates,
identifiers, quantities, or unit-mixing before the forecast can be built.

`src/agriinsight/metrics_inventory_forecast.py` materializes the Gold forecast
CSV and appends forecast evidence to `inventory_status.csv` without rewriting
the current `recommended_order_quantity` policy. The paired contract modules
`metrics_inventory_forecast_contract.py`,
`metrics_inventory_forecast_status_contract.py`, and
`metrics_inventory_forecast_temporal_contract.py` enforce exact schema,
inclusive history spans, deterministic weekly backtest windows, and ULP-safe
derived evidence after CSV round-trip. The analytics snapshot boundary then
loads those exact bytes through `analytics_api/snapshot_cache.py`, while
`analytics_api/domain_read_models.py` preserves the raw extended snapshot
contract.

Phase 3 maps that snapshot into a warehouse-scoped nested `forecast` object,
keeps the two legacy inventory-policy values as siblings, exposes label-free
`forecastHealth` counters, caps ABC/alerts/items at 100, and rejects a final
serialized response above 1 MiB. The Next Inventory contract validates the
exact nested shape and renders Vietnamese status, freshness, range, model and
backtest evidence without browser-side forecast math.

The inventory track does not claim yield, pest-risk, anomaly, what-if, advanced ML SLA,
or Text-to-SQL capability. Phase 2 evidence at `8149ee1` is 317 Python passes
with 3 intentional skips, independent contract review at 96/100, and hosted CI
[`30464080148`](https://github.com/JasonTM17/AgriInsight/actions/runs/30464080148)
passing all 10 jobs. Phase 3 implementation at `ce47ce5` passed hosted CI
[`30469892794`](https://github.com/JasonTM17/AgriInsight/actions/runs/30469892794)
10/10. Final media and image hardening at `aa12c87` passed hosted CI
[`30504951460`](https://github.com/JasonTM17/AgriInsight/actions/runs/30504951460)
10/10, including the real seven-persona browser, verified forecast media
artifact and four candidate-image gates without registry publication.

## Yield forecasting

`yield_forecast.py`, `yield_forecast_contract.py`,
`yield_forecast_backtest.py`, and their numeric/input-validation helpers build
the leakage-safe `crop-median-yield-per-ha-v1` baseline. The contract is one
active season per canonical identity; training labels become eligible only when
their completion time is strictly before the candidate season's forecast
origin. Gold materialization and reconciliation reject malformed, stale,
non-finite, duplicate, relationship-invalid, or area/quantity-inconsistent
rows before `gold/yield_forecast.csv` becomes a checksummed snapshot.

`analytics_api/routers/yield_forecast.py` exposes the additive
`GET /internal/v1/yield-forecast` route through the read model in
`analytics_api/yield_forecast_read_models.py`. It preserves FARMS scope and
authorization order, accepts only canonical farm/field/crop/season filters plus
bounded pagination, fixes ordering to expected harvest then season code, caps a
page at 100 rows and the final response at 1 MiB. The Next exact BFF allowlist,
generated types and `yield-forecast-contract-schema.ts` validate that shape;
the Farm detail panel presents server evidence and can degrade independently of
farm identity and realized performance.

Phase 3 acceptance at `54947ab` passed hosted CI
[`30696001895`](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895)
10/10: real Keycloak/PostgreSQL/Spring/FastAPI/Chrome browser coverage,
visual-reviewed desktop/mobile WebP and two-frame GIF with a verified seven-file
media manifest, and four candidate-image gates. It is documentation/demo and
internal decision support evidence, not external deployment, agronomic ground
truth, a confidence interval, or an accuracy/SLA claim.

The public GitHub Release `v0.4.0` published that accepted yield package set at
`2026-08-01T12:01:05Z` from tag object
`4c27b343eecd32cf7daac462e5f661011e2af0df` to main SHA
`616527dcc7f4a03720fb48e617f9310ab9614873`. Exact-head CI
[`30697294137`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697294137)
passed 10/10 and protected publication
[`30697808763`](https://github.com/JasonTM17/AgriInsight/actions/runs/30697808763)
passed 4/4. Docker Hub/GHCR semantic and full-SHA refs matched the four
immutable digests recorded in the deployment guide.

## Web surface

The web app owns the Next 16 App Router, the opaque session/BFF layer, and the
Phase 5-10 browser surface. Product routes are `/overview`, `/farms`,
`/farms/[farmId]`, `/work`, `/inventory`, `/costs`, `/crop-health`,
`/data-quality`, `/assistant`, and `/admin`; auth/support routes such as `/login`,
`/protected`, and `/api/auth/*` exist as shell plumbing.

The alert-center UI lives in `web/src/features/realtime-alerts/` and is now a
lazy-loaded Field Ledger dialog from the app header. It uses the same-origin
BFF, 30-second open/visible polling, a 90-second stale clock, abort/cleanup,
and acknowledgement terminal states for denied, unavailable, and expired
sessions. The browser bundle passed hosted acceptance in PR `#14` at `e8a02a2`
/ CI `30445148252` and is merged on `main`; that evidence did not publish a new
image or approve external deployment.

Server loaders resolve scoped Spring UUID masters to canonical codes before
calling the typed FastAPI Gold read layer. The browser receives aggregated
view models through a tokenless BFF/opaque PostgreSQL session boundary and
never reconstructs KPI joins. Partial-source failure renders explicit degraded
states; safe lineage and reviewed contextual WebPs remain visible and bounded.
Shared rollout hardening covers exact BFF query allowlists, request-scoped nonce
CSP/custom 404 rendering, and mutex-owned E2E startup/cleanup.

`/work` consumes only frozen Spring activity, assignment, log, and correction
history contracts. Reads use bounded 50-row pages with exact offsets up to the
Spring limit of 10,000. Append and correction commands cross two exact BFF POST
routes with same-origin/CSRF/session checks, a streamed 64 KiB JSON limit,
strict UUID/body validation, and caller-stable `Idempotency-Key` forwarding.
Corrections remain linked append-only rows; the browser never sends a fabricated
`If-Match`, patch request, offline queue, or client-synthesized history.

`/inventory` pairs the Spring operational ledger with the Gold inventory
envelope under one warehouse-scoped route. `src/features/inventory/` holds the
route-state parser, the generated-client adapter, the strict mutation contract,
the view-model loader that isolates per-source failure, and the idempotent
mutation hook. Reads keep server order for balances, lots, and ledger rows and
disclose both `hasMore` and the 10,000 offset ceiling rather than implying a
total. Commands are a receipt/issue transaction and a linked reversal; only the
reversal carries a strong `If-Match`, read from an authenticated transaction
fetch and enforced authoritatively by the backend, which binds the expected
version into the command and into the idempotency fingerprint. An unconfirmed
command keeps its `Idempotency-Key` and source version so retries replay
upstream instead of double-applying. No ABC class, FEFO order, alert, or low-stock threshold is
recomputed in the browser.

`/assistant` uses a dedicated same-origin/CSRF BFF POST and a fixed FastAPI
assistant operation. Spring-derived roles and farm/warehouse catalogs select
the permitted Gold sources before deterministic lexical ranking. DeepSeek V4
Flash receives only bounded retrieved evidence; strict JSON, citation, token,
timeout, response-size, and concurrency contracts fail closed. Conversation
state is ephemeral, and the browser never receives bearer/provider secrets.

`/costs` exposes exactly two lenses. Operating reads/writes use the bounded
Spring ledger and append-only correction routes; procurement stays read-only
and uses the FastAPI Gold snapshot after farm UUID → canonical code mapping.
Runtime Zod schemas validate both sources. Browser mutations require
same-origin CSRF, `COST_MANAGE`, bounded JSON, and stable idempotency keys.
CSV/PDF/XLSX requests use `/api/costs/export`, which forwards only allowlisted
filters and safe file headers; inventory value never becomes a cost lens.

## Operational backend

The backend is a Spring modular monolith under `com.agriinsight.backend`.

| Module | Current responsibility |
|---|---|
| `shared` | API/error contracts, correlation, canonical command hashing, durable idempotency, tenant/profile context, health/readiness |
| `identity` | OIDC validation, exact identity bootstrap, tenant-user lifecycle, external identities, route registry |
| `authorization` | Fixed roles/permissions, scope evaluation, tenant transaction aspect, role lifecycle, audit publishers |
| `farm` | Scoped farm/field/crop/season reads/commands and assignment history |
| `operations` | Employee master/picker, scoped activities/assignments, immutable logs/corrections, harvest facts |
| `inventory/api` | Warehouse, material, supplier, assignment, balance, lot, movement, and reversal HTTP contracts |
| `inventory/application` | Canonical commands, services, pages/queries, stores, reconciliation report |
| `inventory/domain` | Base units, quantity/money precision, transaction and projection records |
| `inventory/infrastructure` | PostgreSQL ledger/projections, deterministic locks/FEFO, reconciliation, warehouse scope SQL |
| `cost` | Append-only operating-cost ledger, correction/reversal commands, bounded hierarchy-derived reads, summaries, and cost route contracts |
| `integration` | Transactional outbox event model, writer port, drain service, and PostgreSQL outbox store |
| `realtime` | Tenant summary read model, metadata-only operational alert evaluator/scanner/DLT observer, and exact tenant/profile-safe alert feed and acknowledgement API |
| `db/migration` | V1-V4 foundation/identity; V5-V11 farm/workforce/activity lifecycle; V12-V15 inventory/warehouse scope; V16-V17 cost ledger/RLS; V18-V19 outbox; V20-V22 realtime summary/immutable alert storage; V23 metadata/cursor hardening; V24-V27 concurrent worker indexes; V28 forward acknowledgement-function repair; V29 open-only acknowledgement locking; V30 concurrent open-feed index (generic expected schema version 30) |
| `backend/ops/postgres` | Idempotent role gate, allowlisted ownership adoption, operator first-admin provisioning |

The backend resolves exact `(issuer, subject)`, loads the active internal
profile and database permissions, then binds `app.tenant_id` and
`app.profile_id` transaction-locally. JWT roles and tenant claims are not
trusted for authorization. Runtime roles are restricted, non-owner, and
subject to PostgreSQL ENABLE/FORCE RLS.

Phase 1 contract freeze adds eight bounded GET reads that stay additive and
non-enumerating:

- `GET /api/v1/activities/{id}/assignments`
- `GET /api/v1/activities/{id}/logs`
- `GET /api/v1/activities/{id}/logs/{logId}/history`
- `GET /api/v1/users/{id}/roles`
- `GET /api/v1/users/{id}/external-identities`
- `GET /api/v1/farm-assignments`
- `GET /api/v1/warehouse-assignments`
- `GET /api/v1/audit-events`

The deterministic backend OpenAPI artifact includes the additive Phase 1 reads
and the exact alert feed/acknowledgement operations. Every operation carries
`X-Correlation-Id`; versioned detail GETs expose `ETag`.

The Phase 2 operational alert contract adds:

- `GET /api/v1/realtime/alerts`: no query parameters; returns at most the latest
  50 open alerts in severity, observation-time, and UUID order.
- `POST /api/v1/realtime/alerts/{id}/acknowledgements`: exact empty JSON body,
  required idempotency key, current-profile acknowledgement, and sanitized
  permission/not-found behavior.

## Inventory contract summary

- `RECEIPT` requires active warehouse/material/supplier, base quantity, VND unit
  cost, batch, and expiry; the server derives finance fields.
- `ISSUE` requires a reason and uses an explicit lot or deterministic FEFO over
  eligible, non-expired lots. It cannot make a lot or balance negative.
- Reversals are immutable linked rows, bounded by the original remaining
  quantity and allocation/lot provenance. Receipt reversal money uses cumulative
  two-decimal rounding so the final total exactly cancels the source.
- `inventory_transactions` is the source ledger; allocations, lots, and
  balances are projections reconciled in a read-only drift report.
- V15 RLS is role-aware: Tenant Admin writes tenant inventory; assigned
  Inventory Manager reads/writes; Executive/Data Analyst read tenant-wide;
  assigned Farm Manager reads; Supplier has no inventory permission.
- All list routes are bounded and stable; mutation routes require idempotency,
  and versioned mutation/reversal routes require strong `If-Match`.

## Current public contracts

- Public health: `GET /actuator/health`, `/actuator/health/liveness`,
  `/actuator/health/readiness`.
- Authenticated `GET /api/v1/me` when identity is enabled.
- Tenant/user/identity/role routes under `/api/v1/users`.
- Farm, field, crop, season, employee, assignment, activity, log, and harvest
  routes under `/api/v1`.
- Inventory masters: `/api/v1/warehouses`, `/api/v1/materials`,
  `/api/v1/suppliers`, `/api/v1/warehouse-assignments`.
- Inventory reads: `/api/v1/inventory/balances`, `/api/v1/inventory/lots`,
  `/api/v1/inventory/transactions` and `/{id}`.
- Inventory writes: `POST /api/v1/inventory/transactions` and
  `POST /api/v1/inventory/transactions/{id}/reversals`.
- Operating-cost writes: `POST /api/v1/cost-entries` and
  `POST /api/v1/cost-entries/{id}/corrections`.
- Operating-cost reads: `GET /api/v1/cost-entries`,
  `GET /api/v1/cost-entries/{id}`, and `GET /api/v1/cost-summaries`.
- Cost summaries identify the `OPERATING_COST` lens and never merge
  procurement spend or inventory value.
- Realtime alerts: `GET /api/v1/realtime/alerts` and
  `POST /api/v1/realtime/alerts/{id}/acknowledgements` under exact
  `REALTIME_ALERT_READ` / `REALTIME_ALERT_ACKNOWLEDGE` permissions.
- OpenAPI/Swagger is disabled by default and only exposed in an explicit
  development profile or authenticated non-development configuration.
- All unregistered business mappings are denied.

## Realtime alert worker and Phase 2 API

`V22` remains immutable. The private `realtime-alert-worker` service uses the
non-web `realtime-worker` profile and a separate no-inheritance
`agriinsight_alert_worker` database login. Local Compose requires
`AGRIINSIGHT_DB_ALERT_WORKER_PASSWORD`; it maps that secret into the worker
datasource only. The service has no HTTP listener. Only this worker disables
the legacy publisher/consumer settings; the established `realtime-worker`
service remains the separate legacy path, and the alert DLT observer is a
distinct path.

The worker's database grants permit only selected tenant/outbox/receipt
metadata plus alert projection/cursor state. It cannot retain raw Kafka values,
outbox payloads, `last_error`, framework headers, or business-table data. The
scanner stores per-policy cursors for fair pages, defaults to 500 candidates
plus a continuation probe and 20-second query budget (configuration cap: 60
seconds), and evaluates in `REPEATABLE_READ` under a policy lock. DLT
attribution validates the bounded envelope, then in a dedicated transaction
looks up `(tenant_id, event_id)` in `outbox_events`, uses the database
`occurred_at`, and only upserts when the source record matches; unmatched DLTs
increment the unverified metric and log a stable event. Receipt recording and
source attribution share a transaction-scoped per-event advisory lock, so that
path is database serialization, not exactly-once or broker ordering. Recovery
rechecks the current condition in the same snapshot, applies hysteresis, and
reports saturation rather than performing unbounded scans.

The worker remains private and metadata-only; it does not define semantic
agriculture alerts and is not an external deployment. Phase 2 separately adds
the public Spring feed/acknowledgement operations and same-origin Next BFF.
Phase 3 has hosted acceptance and is merged with the app-header entry, lazy
panel, bounded polling, stale clock, and guarded acknowledgement states.
The worker startup gate independently pins successful V28 and the latest
repeatable grant, while generic backend readiness now expects schema version
30.

The official upgrade fixture reconstructs V1-V22 plus the historical
repeatable from release commit
`6927eeda70981c2461e85a165834e2464ba793d1`, verifies normalized SHA-256
fingerprints, applies current V23-V30 plus the current repeatable, validates,
and reruns with zero migrations. It preserves representative legacy invalid
rows and the V23 `NOT VALID` constraints; it does not perform the pre-enable
backfill.

The worker hardening split is V23-V30. V23 adds `NOT VALID` source/evidence
checks and cursor/worker isolation without a table-wide legacy-row update.
Before enabling the worker, an operator repeats the V23 backfill in at-most
500-row idempotent batches until both remaining-row checks are false. V24, V25,
V26, and V27 create the backlog, delivery-lag, unrecovered-DLT, and readiness
scan indexes concurrently; V27 is the readiness-only invalid-source-evidence
index and does not replace the backfill. A failed invalid index must be dropped
concurrently before the approved Flyway repair/retry, while a valid existing
index requires history reconciliation instead of retry. Transactional V28
replaces the acknowledgement function without changing its public/security
contract and targets the named observation constraint to avoid PL/pgSQL
identifier ambiguity. The worker startup gate continues to expect V28 plus the
latest repeatable grant. V29 then restricts the locked acknowledgement
function to open alerts, and nontransactional V30 adds the exact concurrent
partial index used by the latest-open feed. These API/read-path migrations
advance generic readiness to 30 without changing the worker's independent V28
startup invariant.

## Verification snapshot

- Realtime alert center Phase 2 (2026-07-29): PR `#13` implementation SHA
  `d781fe49419f2b8ae0508897cc958a1c8cf70124`; hosted run `30425647823` passed
  all 10 Java, Python, web, secret/configuration, real PostgreSQL/Kafka,
  seven-persona browser, and four candidate-image checks.
- Realtime alert center Phase 3 (2026-07-29): PR `#14` feature head `e8a02a2`
  passed hosted run `30445148252`, including the real PostgreSQL/Kafka,
  seven-persona browser, and four candidate-image build validations, then
  rebase-merged on `main` at `bd724503dd3e0864cbd546a6398216fbcd053f31`. The
  run did not publish a new image or approve external deployment.
- Production-web candidate gate (2026-07-27): 202 Python tests, 463 Java
  unit/contract + 100 PostgreSQL integration tests, 308 web tests with 11
  intentional skips, 9/9 web database privilege tests, and 26/26 real Chrome
  journeys. The browser gate covers seven real OIDC personas, all eight product
  areas, five viewport families, axe WCAG, 1,050,000-fact performance budgets,
  cache/CSRF/token boundaries, and complete owned-runtime cleanup.
- Web Phase 8 local acceptance (2026-07-27): full Python suite,
  generated-contract drift, TypeScript, zero-warning ESLint, 246 passed web
  tests with 9 intentional skips, Next 16 production build, 9/9 database
  privilege tests, and 10/10 real Keycloak/PostgreSQL/Spring/FastAPI/Next/
  Chrome journeys. Both Cost Analysis scenarios passed; cleanup completed
  before `WEB_PLATFORM_E2E=PASS`.

- Web Phase 6 local acceptance (2026-07-26): generated-contract drift,
  TypeScript, zero-warning ESLint, Next 16 production build, 127 passed web
  tests with 9 intentional DB-only skips, 31 focused Work contract/security
  tests, 13 demo bootstrap/reconciliation tests, 5 Spring activity HTTP
  contract tests, production dependency audit with 0 vulnerabilities, and 6/6
  real Keycloak/PostgreSQL/Spring/Chrome scenarios. The E2E project and owned
  runtime paths cleaned before `WEB_PLATFORM_E2E=PASS`. A tenant-scoped real
  PostgreSQL seed → revoke → reseed probe also passed with
  `preserved=1 active=0 history=1`, proving demo bootstrap never restores a
  revoked assignment.
- Web Phase 5 local acceptance (2026-07-26): clean `npm ci`, checked-in Spring
  and analytics contract drift, TypeScript, zero-warning ESLint, Next 16
  production build, Maven package with tests skipped by that gate, 82 passed
  web tests with 9 intentional skips, 9/9 PostgreSQL privilege tests, 3/3
  installed-Chrome scenarios, and production dependency audit with 0
  vulnerabilities at the configured threshold.
- Web E2E cleanup audit: zero listeners on 3100, 55443, and 58080-58082; zero
  `agriinsight-web-e2e` Compose containers; `artifacts/_tmp/web-e2e` and
  `_tmp/web-e2e` absent.
- Backend phase-1 contract gate (2026-07-23): 459 surefire tests + 100
  Failsafe/PostgreSQL integration tests; zero failures, errors, and skips.
- Backend guarded `mvn verify` (2026-07-22): 622 tests, including 98 Failsafe
  integration tests; zero failures, errors, and skips.
- Hosted GitHub Actions run `29932250984` passed 5/5 jobs for commit
  `8d8463f9fe576aa98498125ae3dc845d9b432d82`. That run covered Java, Python,
  dependency/configuration/secret scan, and image scan/smoke gates.
- Historical Phase 7 manual registry digests were published for earlier
  evidence only: backend
  `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`
  and Python `sha256:ee4090812a36c48f180ee74aaa16995c79eabfedb6821d9764319643d06ba2f6`.
  They are not a new alert-worker image/tag/digest or release claim.
- Inventory demand forecasting Phases 1–3: deterministic Gold forecast,
  exact joined snapshot validation, authorized nested API contract, strict
  generated web schema, Vietnamese evidence panel and visually reviewed media
  are complete through `aa12c87`; closeout docs/media head `7f669bc` passed
  exact-head CI `30506056691` 10/10. Protected release `v0.3.1` then passed
  publication `30506807548` 4/4; all 16 Docker Hub/GHCR semantic/full-SHA
  references resolved to the expected four immutable digests.
- Yield forecasting public release `v0.4.0`: 16/16 Docker Hub/GHCR
  semantic/full-SHA references resolved to the four immutable digests
  `0c4889671ce010e8d806f949d508c69938d55effa2429e428e71ba5e7ef77420`,
  `c8a21a01b83386d75d4f259103245dbf8f7ffa0730a2ac9ee4e39686c407f3d9`,
  `da49816d51c349391676b7800beffb5270fd27186be3e1d3b9e95aa128fbc345`,
  and `ce0ff7e0d40ad2851355b2274b729059677380d0351b51993582377316928c02`
  after exact-head CI `30697294137` and protected publication `30697808763`.
  The public release is registry evidence only, not external deployment or an
  agronomic SLA claim.
- Cost focused suite: 26/26; fresh PostgreSQL 18 containers validate V1-V17,
  RLS, correction concurrency, query plans, and bounded projections. The
  inventory focused suite remains 32/32.
- OpenAPI contract: `/v3/api-docs` operation summaries and request examples
  verified by the inventory OpenAPI contract checks.
- Current deterministic contract export:
  `backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json`
  regenerated deterministically with SHA-256
  `87bc2c4bc4626c37a2eb8e6b4fd1b286957b1cb2e38e8486a509e02ddd933854`.
- Analytics: Python 76 passed, 3 expected optional-PDF skips; compileall and
  visual/export/dashboard checks pass.
- Big-data: 1,050,003 Bronze sensor rows, 1,050,000 Silver/warehouse facts,
  quality passed, 74 checksum entries with zero mismatch, 388.2 MB on D.
- Disk policy: C warns/fails below 10/8 GB and D below 25/20 GB. Browser, Big
  Data, Testcontainers, and four-image gates use guarded hosted storage whenever
  either local drive is below the relevant heavy-work floor. Recoverable C
  relief moved diagnostics, Node compile cache, and the Maven repository to
  ignored D storage, with a junction preserving Maven behavior. No active
  training process or project artifact was deleted.
- Backup/restore drill: D-local custom dump SHA-256 `934ddd9db020d5a2e4f6860ce977663ec5a28bd68d4dcd7a16cc88a4c9c4162c`,
  Flyway `19`, clean target restore elapsed 11.045s, and role/RLS/runtime
  smoke passed.
- Disposable web-auth spike: `openid-client` 6.8.4 won; Better Auth 1.6.24
  failed the executable refresh-fence harness. Real issuer gate remains proven
  against Keycloak 26.7.0, PostgreSQL 18, Next 16.2.11, and installed Chrome.
  Final auth gate: 16 unit, 7 PostgreSQL integration, 1 installed-Chrome E2E.

## Next boundary

The nine-area production-web implementation, Phase 11 browser gate, and
serialized Phase 12 four-image publication are complete. Alert Center Phases
2–3 have verified API/BFF and hosted browser acceptance evidence; Phase 3 is
merged on `main`. External production deployment remains blocked on the license
decision, production OIDC/broker operations, recovery objectives/ownership,
observability, and host controls.

## Unresolved questions

- Production IdP/token fixtures and MFA policy.
- Production audit retention and backup/restore objectives.
- Release-token rotation owner and GHCR visibility policy.
- Repository license.
