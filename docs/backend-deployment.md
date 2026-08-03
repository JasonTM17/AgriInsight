# Backend deployment and recovery

Phase 7 supplies a local/staging delivery contract. The private alert-worker
hardening is merged on `main` and released in `v0.2.3`: hosted CI, protected
publication, exact-digest verification, and repository-linked GHCR packages
are complete. This release does not approve an external production deployment,
production broker ownership, OIDC operations, or recovery objectives.

## Optional local Compose profile

The existing analytics pipeline/dashboard Compose flow is unchanged. Backend services are opt-in:

```powershell
Copy-Item .env.example .env.backend.local
# Edit .env.backend.local with unique local passwords and OIDC values.
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend config --quiet
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend up --build backend
```

The overlay binds PostgreSQL and API ports to `127.0.0.1`, stores PostgreSQL data under the ignored D-local `backend/.runtime/postgres`, runs the idempotent role bootstrap, then runs a one-shot Flyway migration before the restricted runtime. Tenant/first-admin provisioning remains explicit; container startup never creates an administrator. No service mounts `artifacts/`.

Realtime local topology là overlay riêng, không nằm trong lệnh backend mặc định:

```powershell
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml `
  -f compose.realtime.yaml --profile backend --profile realtime config --quiet
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml `
  -f compose.realtime.yaml --profile backend --profile realtime up --build
```

Overlay này thêm Kafka KRaft `apache/kafka:4.3.1`, one-shot realtime password setup, service `realtime-worker`, và service `realtime-alert-worker` non-web. Nó yêu cầu `AGRIINSIGHT_DB_REALTIME_PASSWORD` và `AGRIINSIGHT_DB_ALERT_WORKER_PASSWORD`; biến sau chỉ được map vào datasource của login `agriinsight_alert_worker`, không được commit. Broker bind ra `127.0.0.1:${AGRIINSIGHT_KAFKA_PORT:-9094}` và ghi broker log vào ignored `backend/.runtime/kafka` trên D. Chỉ service `realtime-alert-worker` giữ `AGRIINSIGHT_REALTIME_PUBLISHER_ENABLED=false` và `AGRIINSIGHT_REALTIME_CONSUMER_ENABLED=false`; DLT observer riêng vẫn hoạt động ở worker đó. `realtime-worker` là legacy publisher/consumer path riêng. Không có HTTP listener/public worker API. Cùng backend image được dùng cho source/Compose local và bản phát hành `agriinsight-backend:0.2.3`; không có image `agriinsight-realtime` riêng và chưa có external deployment cho slice này.

The alert-only datasource fixes pgJDBC `socketTimeout=65`, which is larger than the worker's configuration-capped 60-second query bound and leaves the API datasource's default fail-fast timeout unchanged. Its distinct terminal observer topic receives only a compact headerless marker on observer failure; it never forwards the original Kafka key, value, headers, or exception text. Receipt recording and DLT source attribution share a transaction-scoped advisory lock per event, so the DLT transaction waits and rechecks receipt after acquiring the lock; that is serialization, not exactly-once or broker ordering.

The worker must stay disabled until its startup gate finds a successful V28 row, the latest installed repeatable `R__tenant_rls_helpers_and_grants.sql` succeeds, and the V23 source-evidence backfill reports no remaining legacy or invalid-shape rows. Startup also verifies the exact `agriinsight_alert_worker` login topology, no inherited memberships, the narrow metadata-only grants, and the named FORCE-RLS policies. V24-V27 each create one index concurrently; V27 is the readiness-only partial index over invalid source-evidence rows. Transactional V28 repairs the acknowledgement function through its named unique constraint without rewriting V22. Use the exact invalid-index recovery procedure in the [deployment guide](deployment-guide.md#alert-worker-pre-enable-and-concurrent-index-recovery), not an ad hoc retry.

Compose role passwords are environment inputs only. Do not put real values in `.env.example`, images, command history or logs. The backend image is read-only with a `/tmp` tmpfs, drops Linux capabilities and runs as UID/GID `10001:10001`.

## First-party images

The protected release workflow publishes four first-party images:
`agriinsight-python`, `agriinsight-backend`, `agriinsight-web`, and
`agriinsight-analytics-api`. Pull-request CI builds the same four images with
`push: false`. The isolated alert worker reuses `agriinsight-backend`; there is
no separate `agriinsight-realtime` image. Release `v0.2.3` establishes the
versioned backend tag and exact digest shown below; GHCR packages remain private
and are linked to `JasonTM17/AgriInsight`. Dockerfiles pin base-image manifest digests, use allowlisted build
contexts, add OCI source/revision/version labels and expose deterministic smoke
checks. The backend runtime is Temurin 21.0.11 JRE Noble at
`sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64` and
runs as UID/GID 10001. PostgreSQL and Apache Kafka remain upstream dependencies
and are never republished.

Pull-request CI builds all four images without registry login or push. The protected `release-images` workflow runs only for a semantic-version tag (`vMAJOR.MINOR.PATCH`) and requires:

- repository variable `DOCKERHUB_NAMESPACE`;
- environment secrets `DOCKERHUB_USERNAME` and least-privilege, rotatable `DOCKERHUB_TOKEN`;
- environment reviewers/branch/tag protection configured by the repository owner.

It publishes only immutable semantic-version and `sha-<full-commit>` tags to:

```text
<DOCKERHUB_NAMESPACE>/agriinsight-python
<DOCKERHUB_NAMESPACE>/agriinsight-backend
<DOCKERHUB_NAMESPACE>/agriinsight-web
<DOCKERHUB_NAMESPACE>/agriinsight-analytics-api
ghcr.io/<github-owner>/agriinsight-python
ghcr.io/<github-owner>/agriinsight-backend
ghcr.io/<github-owner>/agriinsight-web
ghcr.io/<github-owner>/agriinsight-analytics-api
```

There is intentionally no automatic `latest`. BuildKit emits SBOM/provenance; Trivy scans the exact returned digest; both registry tags are resolved back to that digest; and a non-root smoke command runs against the digest. A failed post-publish evidence step fails the release and requires an audited new tag/republish rather than tag mutation.

Release [`v0.2.3`](https://github.com/JasonTM17/AgriInsight/releases/tag/v0.2.3)
was published from commit `3e72ab5226a17d85fc42cb4f0cacb1900a416a1a`.
Main CI [`30413064146`](https://github.com/JasonTM17/AgriInsight/actions/runs/30413064146)
and protected publication
[`30413877863`](https://github.com/JasonTM17/AgriInsight/actions/runs/30413877863)
passed. Docker Hub and GHCR returned the same digest for each `0.2.3` tag:

| Image | Exact digest |
|---|---|
| `agriinsight-python` | `sha256:ea8dc5d97b493833e526a7d01a76b76c13a70f2c94af30cd1f7f3ba2ce7829ec` |
| `agriinsight-backend` | `sha256:8c17efc5371cc45efa65f23b1ca964784286ed692e2014d81e9a763b080cd418` |
| `agriinsight-web` | `sha256:7930dd4468bf7664b7196288421e1d79737e36c9bba6759597e53bf513aecad2` |
| `agriinsight-analytics-api` | `sha256:c4e56c3f8084c6d505f50dc1e38848c9cd1b4ca77b6d44b4b285d8cd6e3c4677` |

Phase evidence at commit `8d8463f9fe576aa98498125ae3dc845d9b432d82`: hosted CI run [`29932250984`](https://github.com/JasonTM17/AgriInsight/actions/runs/29932250984) passed 5/5; Trivy 0.70.0 reported zero HIGH/CRITICAL findings; Docker Hub and GHCR tags `0.1.0-phase7` and `sha-8d8463f` resolve to backend digest `sha256:2fb346c3b85f03022866e74ae321a8a952b224fc23e43cb0560a440730019a5d`. This remains historical evidence for the earlier Phase 7 image path; the current four-image release is documented in the [deployment guide](deployment-guide.md#docker-hub-release-policy).

## Backup

`backup-backend-postgres.ps1` requires an explicit D-drive target and refuses overwrite:

```powershell
$env:AGRIINSIGHT_DB_HOST='127.0.0.1'
$env:AGRIINSIGHT_DB_PORT='5432'
$env:AGRIINSIGHT_DB_NAME='agriinsight'
$env:AGRIINSIGHT_DB_OPERATOR_USERNAME='postgres'
$env:AGRIINSIGHT_DB_OPERATOR_PASSWORD='use-secret-store'
powershell -ExecutionPolicy Bypass -File scripts/backup-backend-postgres.ps1 `
  -BackupFile 'D:\AgriInsight\artifacts\_tmp\backups\agriinsight-20260722.dump'
```

The script runs `pg_dump --format=custom`, keeps ACLs, writes a sidecar JSON containing PostgreSQL/Flyway/checksum/size metadata, and uses `PGPASSWORD` rather than command-line credentials. It never deletes a target or cleans user files. Disk guard must be PASS first.

The dump and its sidecar are first written to reserved adjacent temporary files.
They are published only by an atomic no-overwrite move, so an existing backup
or metadata file is never replaced by a concurrent run. The D-drive path must
not traverse a symbolic link or junction.

## Restore drill

Restore is forward-safe: it requires the checksum sidecar, a pre-created empty target database, the operator role, migration role, and runtime role credentials. It refuses to drop a non-empty database and never runs `pg_restore --clean`.

```powershell
$env:AGRIINSIGHT_RESTORE_DRILL_HOST='127.0.0.1'
$env:AGRIINSIGHT_RESTORE_DRILL_PORT='5432'
$env:AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS='127.0.0.1'
$env:AGRIINSIGHT_RESTORE_DRILL_TARGET_DATABASE='agriinsight_restore_v30'
powershell -ExecutionPolicy Bypass -File scripts/restore-backend-postgres.ps1 `
  -BackupFile 'D:\AgriInsight\artifacts\_tmp\backups\agriinsight-20260722.dump' `
  -RestoreDrillScope local-or-staging
```

Order is: disk guard → checksum → empty-target check → idempotent role bootstrap → `pg_restore --no-owner --single-transaction` (ACLs retained) → Flyway validate → integration-role/outbox-RLS gate → runtime schema-history/count smoke → measured restore report. A failed restore is retained for diagnosis; repair is an audited forward migration or a verified clean restore, never deletion of applied migrations.

For a current-schema drill, first validate the backup sidecar and then require an
explicit run confirmation:

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/run-backend-restore-drill.ps1 -BackupFile 'D:\AgriInsight\artifacts\_tmp\backups\agriinsight-20260722.dump' -MinimumSchemaVersion 30 -Mode Validate

powershell -ExecutionPolicy Bypass -File scripts/run-backend-restore-drill.ps1 -BackupFile 'D:\AgriInsight\artifacts\_tmp\backups\agriinsight-20260722.dump' -MinimumSchemaVersion 30 -Mode Run -RestoreDrillScope local-or-staging -ConfirmRestoreDrill
~~~

The wrapper rechecks the checksum, requires V30-or-newer source and restored
schema evidence, and ties the new report to that exact backup. It does not
establish a production RPO/RTO: production remains blocked until its off-host
encryption, retention, owner, schedule, and objectives are approved.

The V30 minimum may only be increased by an operator; a lower value is rejected.
Before role bootstrap, restore rejects any user relation, routine, type,
extension, or non-public schema, not merely tables. It holds a read lock on the
verified backup through restoration and atomically publishes the uniquely named
report without replacing an existing file.

The target must be a separately named lowercase `agriinsight_restore_*` database
and must differ from the backup metadata's source database. A `local-or-staging`
scope is mandatory for a run and stored in the report. These guards prevent
accidental restoration into the ordinary application database; they do not
authorize production recovery.

Restore does not use the ordinary `AGRIINSIGHT_DB_HOST`/`PORT` inputs. It
requires the dedicated literal IPv4 loopback endpoint `127.0.0.1`, which must
exactly match the protected `AGRIINSIGHT_RESTORE_DRILL_ALLOWED_HOSTS` allowlist,
before role bootstrap or restore. A global per-target mutex is held from the
empty-target check through report publication, so a second local or RDP-session
operator cannot begin a concurrent drill against the same endpoint and database.
Remote staging remains blocked until an
approved TLS provider contract supplies certificate verification for both libpq
and Flyway JDBC. Store the backup, sidecar, report, and temporary files in an
ACL-controlled directory: the reparse-point check rejects existing symbolic
links/junctions, but cannot defend against another local account with concurrent
write permission replacing a path after it is checked.

## Operational approval gate

Before production, record approved values for RPO, RTO, retention, encryption/key owner, off-host backup location, restore owner, and a successful timed drill. Until those values and evidence are approved, run the backend only in local/staging environments.

## Rollback

Disable the optional backend/outbox adapter and keep the analytics MVP running. For a faulty migration, stop writes, preserve the evidence, validate a checksum-verified backup, and apply a forward repair. Do not edit applied Flyway checksums, drop outbox/domain tables, or repoint immutable image tags.
