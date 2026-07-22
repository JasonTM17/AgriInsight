# Backend deployment and recovery

Phase 7 supplies a local/staging delivery contract. It is not a production approval: production still needs an OIDC issuer/audience decision, approved RPO/RTO, retention, encrypted off-host backup, and a named restore owner.

## Optional local Compose profile

The existing analytics pipeline/dashboard Compose flow is unchanged. Backend services are opt-in:

```powershell
Copy-Item .env.example .env.backend.local
# Edit .env.backend.local with unique local passwords and OIDC values.
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend config --quiet
docker compose --env-file .env.backend.local -f compose.yaml -f compose.backend.yaml --profile backend up --build backend
```

The overlay binds PostgreSQL and API ports to `127.0.0.1`, stores PostgreSQL data under the ignored D-local `backend/.runtime/postgres`, runs the idempotent role bootstrap, then runs a one-shot Flyway migration before the restricted runtime. Tenant/first-admin provisioning remains explicit; container startup never creates an administrator. No service mounts `artifacts/`.

Compose role passwords are environment inputs only. Do not put real values in `.env.example`, images, command history or logs. The backend image is read-only with a `/tmp` tmpfs, drops Linux capabilities and runs as UID/GID `10001:10001`.

## First-party images

The root Python image is `agriinsight-python`; the backend image is `agriinsight-backend`. Both Dockerfiles pin base-image manifest digests, use allowlisted build contexts, add OCI source/revision/version labels and expose deterministic smoke checks. PostgreSQL is consumed upstream and is never republished.

Pull-request CI builds both images without registry login or push. The protected `release-images` workflow runs only for a semantic-version tag (`vMAJOR.MINOR.PATCH`) and requires:

- repository variable `DOCKERHUB_NAMESPACE`;
- environment secrets `DOCKERHUB_USERNAME` and least-privilege, rotatable `DOCKERHUB_TOKEN`;
- environment reviewers/branch/tag protection configured by the repository owner.

It publishes only immutable semantic-version and `sha-<full-commit>` tags to:

```text
<DOCKERHUB_NAMESPACE>/agriinsight-python
<DOCKERHUB_NAMESPACE>/agriinsight-backend
ghcr.io/<github-owner>/agriinsight-python
ghcr.io/<github-owner>/agriinsight-backend
```

There is intentionally no automatic `latest`. BuildKit emits SBOM/provenance; Trivy scans the exact returned digest; both registry tags are resolved back to that digest; and a non-root smoke command runs against the digest. A failed post-publish evidence step fails the release and requires an audited new tag/republish rather than tag mutation.

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

## Restore drill

Restore is forward-safe: it requires the checksum sidecar, a pre-created empty target database, the operator role, migration role, and runtime role credentials. It refuses to drop a non-empty database and never runs `pg_restore --clean`.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restore-backend-postgres.ps1 `
  -BackupFile 'D:\AgriInsight\artifacts\_tmp\backups\agriinsight-20260722.dump'
```

Order is: disk guard → checksum → empty-target check → idempotent role bootstrap → `pg_restore --no-owner --single-transaction` (ACLs retained) → Flyway validate → integration-role/outbox-RLS gate → runtime schema-history/count smoke → measured restore report. A failed restore is retained for diagnosis; repair is an audited forward migration or a verified clean restore, never deletion of applied migrations.

## Operational approval gate

Before production, record approved values for RPO, RTO, retention, encryption/key owner, off-host backup location, restore owner, and a successful timed drill. Until those values and evidence are approved, run the backend only in local/staging environments.

## Rollback

Disable the optional backend/outbox adapter and keep the analytics MVP running. For a faulty migration, stop writes, preserve the evidence, validate a checksum-verified backup, and apply a forward repair. Do not edit applied Flyway checksums, drop outbox/domain tables, or repoint immutable image tags.
