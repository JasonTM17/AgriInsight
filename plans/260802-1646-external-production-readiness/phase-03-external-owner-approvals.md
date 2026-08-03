---
phase: 3
title: "External owner approvals"
status: pending
priority: P1
effort: "2-4d external coordination"
dependencies: [1, 2]
---

# Phase 3: External owner approvals

## Overview

Collect the decisions no repository change can make: real identity, broker,
hosting, recovery, legal, and release operations. Each missing decision remains
a visible NO-GO with an accountable owner and deadline.

## Requirements

- Functional: maintain a source-controlled, non-secret owner matrix and
  evidence template that rejects blank or placeholder records.
- Functional: record owner, approval reference, deadline, unlock criterion,
  and rollback responsibility for every external production control.
- Non-functional: do not fabricate person names, approval dates, access tokens,
  provider settings, or legal decisions.

## Related Files

- Create: `docs/production-readiness.md`
- Modify: `docs/deployment-guide.md`
- Modify: `docs/project-roadmap.md`
- Create: `plans/260802-1646-external-production-readiness/reports/`

## Implementation Steps

1. Publish the owner matrix for IdP/MFA/CORS, broker TLS/SASL/HA/retention,
   hosting/TLS/host controls, observability/on-call, recovery/off-host
   encryption, registry-token rotation/GHCR visibility, license, and rollback.
2. Link each row to the machine-checked promotion evidence field and define the
   exact artifact that unlocks it.
3. Obtain actual approvals through the responsible organization; store only
   safe references and timestamps in the repository.
4. Mark unresolved entries as NO-GO with a real deadline rather than changing
   technical status to green.

## Success Criteria

- [ ] No external control lacks an owner, deadline, reference, or unlock test.
- [ ] The evidence record contains no secrets or PII beyond approved owner
  identifiers.
- [ ] License and GHCR visibility decisions are explicitly captured before
  public exposure.

## Risk Assessment

- External approvals can stall. Surface the blocking row and its dependency;
  do not hide it with a technical workaround.

## Security Considerations

- References may point to protected systems; avoid embedding private URLs,
  credentials, or confidential incident detail in public source.
