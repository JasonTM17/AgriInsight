---
phase: 4
title: "Hosted integration and handoff"
status: pending
priority: P1
effort: "1-2d"
dependencies: [3]
---

# Phase 4: Hosted integration and handoff

## Context links

- [Plan](./plan.md)
- [CI workflow](../../.github/workflows/ci.yml)
- [Deployment guide](../../docs/deployment-guide.md)

## Overview

Prove the complete outbox-to-Kafka-to-read-model path on hosted storage, review
security/failure modes, update operations/docs, and hand off the next UI/alerts
phase without mislabeling internal evidence as production. The source already
contains the guarded realtime runner, hosted CI job, authenticated MockMvc
summary-route test, and RLS schema coverage; hosted CI has not yet produced a
green realtime run, so this phase stays pending.

## Requirements

- Hosted gate uses real PostgreSQL and official Kafka 4.3.1.
- Test duplicate, broker outage/recovery, consumer restart, poison DLT,
  aggregate ordering, tenant denial, and measured freshness.
- Preserve existing Python/web/image gates and keep C/D disk policy explicit.
- No registry push or production claim without protected owner approval.
- Do not mark the slice accepted until the first hosted realtime run lands.

## Related code files

- Modify: `D:\AgriInsight\.github\workflows\ci.yml`
- Create: `D:\AgriInsight\scripts\run-realtime-e2e-tests.ps1`
- Modify: `D:\AgriInsight\README.md`
- Modify: `D:\AgriInsight\docs\system-architecture.md`
- Modify: `D:\AgriInsight\docs\project-roadmap.md`
- Modify: `D:\AgriInsight\docs\deployment-guide.md`
- Modify: `D:\AgriInsight\docs\data-contracts.md`
- Create: acceptance, test, security, and code-review reports in this plan.

## Tests before

- Define exact machine-readable pass marker and cleanup assertions.
- Define the p95 local/hosted freshness target as `<= 30s`, measured across 20
  sequential accepted samples from durable outbox append to the authenticated
  tenant summary; log `freshness_p95_millis`, assert no-loss/zero duplicate
  increments/DLT delivery, and check owned-container cleanup.

## Implementation steps

1. Add a D-local/hosted-safe PowerShell runner with ownership labels and
   guaranteed cleanup; never delete unrelated Docker resources.
2. Seed a real command/outbox row, publish/consume, and poll the authenticated
   summary until the event appears.
3. Inject one duplicate, one malformed record, one consumer restart, and one
   broker interruption; verify read-model counts and DLT coordinates.
4. Add a serialized hosted CI job after backend/security and before images.
5. Run CK test, security, code review, and whole-plan consistency gates.
6. Update docs and record exact run/commit evidence.
7. Commit small conventional clusters, push `main`, and wait for required CI.

## Todo

- [x] Add deterministic E2E runner and CI job.
- [ ] Capture freshness/replay/failure evidence.
- [ ] Run security and code review with zero unresolved critical/high issues.
- [x] Sync docs, roadmap, and plan status.
- [ ] Push and verify hosted CI.

## Success Criteria

- [ ] Real outbox event reaches the authorized summary through Kafka.
- [ ] Replay increments zero extra metrics; malformed input lands in DLT.
- [ ] Broker/consumer recovery loses no accepted event.
- [ ] Existing CI jobs remain green and owned resources clean up.
- [ ] Evidence distinguishes internal acceptance from external production.

## Risk assessment

| Risk | Mitigation |
|---|---|
| Hosted flake | readiness polling, bounded deadlines, diagnostics, owned cleanup |
| Disk pressure | hosted disk guard; no local broker/build while C is below floor |
| Scope bleed into UI/alerts | explicit follow-on plan after transport acceptance |

## Rollback

Disable publisher/consumer properties and remove the optional realtime overlay.
Operational commands continue committing to the existing outbox. Do not edit or
drop V20; use a forward migration if read-model repair is required.

## Next steps

After acceptance, open the FE workflow for an Overview live-operations panel
and the backend alert policy phase. ML/AI remain downstream of stable event
freshness and labeled data contracts.
