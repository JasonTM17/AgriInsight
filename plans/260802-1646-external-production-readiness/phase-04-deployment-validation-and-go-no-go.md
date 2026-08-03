---
phase: 4
title: "Deployment validation and go-no-go"
status: pending
priority: P1
effort: "2-3d plus environment access"
dependencies: [1, 2, 3]
---

# Phase 4: Deployment validation and go-no-go

## Overview

Use the approved external environment to prove a digest-pinned deployment,
then issue one evidence-backed GO or NO-GO verdict. A registry-only result is
never enough.

## Requirements

- Functional: validate current digests, TLS/host policy, OIDC authorization,
  broker failure/recovery, health/observability, backup restore, rollback, and
  all existing security/integration/browser gates in the target environment.
- Functional: package URLs/reports for exact CI, publication, deployment,
  runtime checks, recovery drill, rollback rehearsal, and external approvals.
- Non-functional: halt immediately on a failed or missing control and preserve
  diagnostic evidence without exposing secrets or tenant data.

## Related Files

- Modify: `docs/deployment-guide.md`
- Modify: `docs/project-roadmap.md`
- Create: `plans/260802-1646-external-production-readiness/reports/go-no-go-*.md`

## Implementation Steps

1. Verify every Phase 1-3 input with the promotion validator and owner matrix.
2. Apply only approved digest-pinned configuration through the supported
   deployment path; capture health and version evidence.
3. Run OIDC/MFA authorization, broker interruption/recovery, current-schema
   restore, observability/alert delivery, rollback, security, integration, and
   browser checks.
4. Produce a signed-off GO or NO-GO report with all evidence links, residual
   risks, and the exact rollback action.

## Success Criteria

- [ ] Every named objective control has direct target-environment evidence.
- [ ] A failed check leaves the verdict NO-GO and points to its owner/unlock
  criterion.
- [ ] A GO verdict identifies deployed immutable digests and a tested rollback
  authority/path.

## Risk Assessment

- Target-environment operations require external authority. This phase remains
  blocked until Phases 1-3 and appropriate access are complete.

## Security Considerations

- Use least-privilege deployment identities and protected evidence storage.
- Never place production token, backup, raw audit, or customer data in reports.
