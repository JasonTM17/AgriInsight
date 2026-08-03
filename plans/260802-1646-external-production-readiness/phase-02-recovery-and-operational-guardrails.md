---
phase: 2
title: Recovery and operational guardrails
status: in-progress
priority: P1
effort: 2-3d
dependencies: []
---

# Phase 2: Recovery and operational guardrails

## Overview

Refresh the database recovery proof for the current V30 schema and prevent
local-only runtime assumptions from being promoted as production controls.

## Requirements

- Functional: run a clean-target, checksum-linked backup/restore drill against
  current schema and preserve the measured report as non-secret evidence.
- Functional: keep restore forward-safe: empty target only, no `--clean`,
  role bootstrap, Flyway validation, and integration/RLS smoke all remain
  mandatory.
- Functional: require explicit external values for RPO, RTO, retention,
  off-host encryption/key ownership, restore ownership, and recurring schedule.
- Non-functional: do not add a fake backup destination, automatic retention
  deletion, TLS/SASL configuration, alert receiver, or secret rotation without
  an approved external provider contract.

## Architecture

The existing backup and restore wrappers remain the only data-moving path.
Current-schema drill orchestration produces evidence locally/staging, while the
promotion manifest records approved production recovery references. Production
broker and observability controls are represented as fail-closed external gates,
not emulated by the local single-node Compose profile.

## Related Code Files

- Modify: `scripts/backup-backend-postgres.ps1`
- Modify: `scripts/restore-backend-postgres.ps1`
- Create or modify: `scripts/run-backend-restore-drill.ps1`
- Create: `scripts/postgres-backup-integrity-helpers.psm1`
- Create or modify: focused recovery test harness under `tests/` or `scripts/`
- Modify: `docs/backend-deployment.md`
- Modify: `docs/deployment-guide.md`

## Tests Before

1. Protect D-drive-only, checksum, empty-target, role, and no-clean invariants.
2. Add a regression assertion that current schema evidence cannot be labelled
   production RTO proof without approved external RPO/RTO and owner fields.

## Implementation Steps

1. Inspect available PostgreSQL/Docker test infrastructure and design a
   disposable current-schema source and empty target that never touches a
   developer or production database.
2. Run and record a V30 clean restore drill; capture duration, schema version,
   count/RLS checks, source hash, and command version without secrets.
3. Add only the orchestration/validation needed to make the drill repeatable.
4. Update recovery runbooks with evidence freshness, failure retention, and
   operator handoff requirements.

## Success Criteria

- [ ] A V30 (or newer) clean-target restore report is reproducible and linked
  to its checksum-verified backup.
- [ ] The drill refuses unsafe targets and retains failed targets for diagnosis.
- [ ] Production remains NO-GO until off-host encryption, RPO/RTO, retention,
  restore owner, and recurring schedule are approved.

## Risk Assessment

- Backup/restore can consume disk and database resources. Always run disk guard
  and use an isolated target; never clean or overwrite an existing database.
- A local drill proves mechanics, not production recovery objectives.

## Security Considerations

- Never place database passwords in command-line arguments, reports, or Git.
- Preserve RLS role separation before and after restoration.
