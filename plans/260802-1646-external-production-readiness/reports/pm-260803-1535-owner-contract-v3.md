---
title: External production readiness progress after owner-contract v3
status: in-progress
generated_at: '2026-08-03T15:35:05+07:00'
baseline_head: 68b71bda0a35b2354be6ef0a254ed0c77f8dda53
verdict: NO-GO
---

# Production readiness progress

## Outcome

The repository-owned external approval contract is hardened, tested, reviewed,
and documented. This does not supply any external owner or approval. Production
remains **NO-GO**.

## Completed in this slice

- Promotion evidence is now exact `format_version: 3`; numeric strings and
  floating-point values fail closed.
- All ten approval controls require the exact seven-field row schema, including
  `rollback_responsibility`; missing, extra, or misspelled fields fail.
- Approval values reject unresolved markers after Unicode normalization,
  including dash, minus-sign, variation-selector, combining-control, and
  zero-width confusables. Generic target/recovery/rollback string behavior was
  not changed.
- Human owner-matrix rows map directly to machine evidence keys without
  fabricating owners, references, dates, or rollback assignments.
- Phase 1 stale success checkboxes were reconciled to its already-completed
  status. Phase 3 records two repository preparation steps complete and leaves
  all external coordination/success criteria open.

## Verification

| Gate | Result |
|---|---|
| Promotion evidence focused suite | 47 passed; exit 0 |
| Template JSON | format 3, exactly 10 controls, required fields present; exit 0 |
| PowerShell AST parse | Both touched modules parse; exit 0 |
| Documentation validator | Exit 0; 28 internal links valid; heuristic config-key warnings remain non-blocking |
| Whitespace check | `git diff --check` exit 0; line-ending normalization warnings only |
| CK adversarial review | PASS; all five findings resolved and final micro re-review found no blocker |

Broad Python, Maven, Docker, restore, security, integration, and browser gates
were not run in this slice. The default disk guard prohibits those claims.

## Plan status

| Phase | Checklist | Status | Next evidence |
|---|---:|---|---|
| 1 — Promotion controls | 4/4 | Completed | Preserve exact-head CI and registry controls |
| 2 — Recovery guardrails | 0/3 | In progress | Default disk PASS, then current-schema clean restore drill |
| 3 — External approvals | 2/7 | Pending external coordination | Real owners, deadlines, approval refs, rollback responsibilities, unlock evidence |
| 4 — Deployment validation | 0/3 | Pending | Approved target environment and all Phase 1–3 inputs |

## Current blockers

| Blocker | Owner | Deadline | Unlock criterion |
|---|---|---|---|
| Production OIDC/MFA/CORS | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Target IdP/MFA authorization evidence passes |
| Kafka TLS/SASL/HA/retention/on-call | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Target interruption, recovery, ACL, and isolation evidence passes |
| Hosting/TLS/host controls | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Approved digest-pinned target passes host/TLS validation |
| Audit retention and alert delivery | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Protected retention, denial/success audit, alert and legal-hold evidence passes |
| Recovery RPO/RTO/off-host encryption | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Approved policy plus current-schema timed restore evidence passes |
| Credential rotation | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Target rotation rehearsal passes without secret disclosure |
| Observability and rollback authority | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Alert delivery and previous-digest/disable-exposure rehearsal passes |
| Registry visibility/token rotation | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Approved policy and protected paired-registry evidence pass |
| License/legal | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Root license decision and OCI/documentation alignment are approved |
| Local capacity | UNASSIGNED — NO-GO | UNASSIGNED — NO-GO | Default guard PASS: C at least 10 GiB and D at least 25 GiB |

At `2026-08-03T15:35:05+07:00`, the default guard reported C `8.046 GiB`
free (WARN, hard-fail below 8) and D `16.949 GiB` free (FAIL below 20). No
shared Docker resource, external project, backup, recovered diagnostic trace,
or unrelated process was modified.

## Next actions

1. Restore workstation capacity without deleting shared/user-owned state.
2. Run the V30-or-newer clean-target restore drill and capture checksum-linked
   evidence after default disk PASS.
3. Responsible organization assigns real owners/deadlines and supplies safe
   approval references for every v3 evidence key.
4. Run exact-head hosted CI and all target-environment deployment, rollback,
   restore, security, integration, and browser gates.

## Unresolved questions

- Who owns each external control, and what real deadline is approved?
- Which protected target environment and evidence store are authorized?
- Which user-approved storage can be freed or relocated to restore the default
  workstation thresholds?
