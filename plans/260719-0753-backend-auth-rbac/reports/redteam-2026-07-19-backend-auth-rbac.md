# Red-team report: Backend auth, RLS, operations, and release

Date: 2026-07-19
Verdict: **GO for CK cook phase 1; NO-GO for production deployment**
Scope: `plans/260719-0753-backend-auth-rbac/`

## Review method

- Read every phase, research note, normative authorization matrix, frontend follow-up brief, and planner report.
- Challenged trust boundaries: JWT class/claims, pre-tenant identity resolution, tenant context ordering, DB roles/RLS, assignments, command idempotency, corrections/reversals, outbox leases/order, backup/restore, and container publication.
- Parsed 86 method + `/api/v1` route templates: zero duplicate or malformed templates.
- Checked 11 internal relative links: zero broken links.
- Audited 167 phase file-ownership entries. All repeated paths are serialized transitions documented in `plan.md`; `backend/Dockerfile` explicitly moves from phase-1 scaffold to phase-7 release hardening.
- Ran `ck plan validate ... --strict`: seven phases, zero errors, zero warnings. Ran `ck plan parse ... --json`: phase links/frontmatter resolve.
- An independent reviewer produced adversarial findings but exhausted its execution quota before issuing a final synthesis. The controller reproduced each recorded finding against the files, applied the fixes below, then ran the final consistency checks.

## Findings closed

| Severity | Initial failure mode | Resolution in plan |
|---|---|---|
| Critical | Tenant identity lookup could become circular under RLS or gain broad table access. | Exact JWT validation precedes a minimum-column hardened resolver; tenant-scoped principal load then occurs under RLS before route authorization. Dedicated `NOLOGIN` definer, pinned search path, explicit policies/grants, and catalog tests are mandatory. |
| Critical | Runtime/migration/role-bootstrap credentials could collapse into one privileged DB identity, and a phase-1/2 database could retain objects under a legacy owner. | Pre-Flyway idempotent role gate, separate migration owner, restricted runtime, function definer, and later integration role. Upgrade uses an explicit V1-V3 object-ownership allowlist and refuses shared/unexpected owners; fresh/upgrade/restore tests inspect `current_user`, attributes, membership, grants, and ownership. |
| Critical | Farm/warehouse/activity assignments could be created before their parent FK tables. | Assignment tables are owned by phases 4/5 beside their parents with composite tenant FKs; generic polymorphic scope rows are prohibited. |
| Critical | Route/role prose could authorize endpoints not covered by an exact registry. | `authorization-matrix.md` is normative with 86 exact method/templates, fixed permissions/grants, endpoint-inventory parity, service scope checks, and `anyRequest().denyAll()`. |
| High | Reused idempotency key could silently apply a changed path, version, or optimistic precondition. | Canonical hash binds tenant, principal, method, normalized route, path/query/body, `If-Match`, and hash-schema version; same key/different command is 409; durable records have no MVP purge. |
| High | Concurrent first stock receipt or partial reversal could overdraw/duplicate a lot. | Create-or-lock protocol handles absent rows; deterministic lot/aggregate locks, allocation lineage, service-derived reversal values, and reconciliation tests are required. |
| High | Cost target hierarchy could accept inconsistent same-tenant farm/field/season/activity combinations or double count inventory. | One canonical target with one-hot FKs; ancestors are derived; reversals copy the original target. Operating, procurement, and inventory-value lenses remain separate. |
| Critical | Outbox retries could reorder one aggregate or allow stale workers to acknowledge a reclaimed lease. | Aggregate version uniqueness/predecessor gating plus owner/token/generation/expiry fencing; dead letter blocks successors; no public controller/scheduler/pretend delivery adapter. |
| Critical | Upgrade/restore could reference an integration role absent from a database dump. | Current role bootstrap always runs before Flyway upgrades and clean restores; restore drills preserve/test ACLs, grants, RLS, runtime access, checksum, counts, and measured time. |
| High | Docker publication could expose credentials, mutable tags, or untested images. | Pull requests never push; protected release uses least-privilege secrets, pinned actions, pre-publish scan, immutable version/SHA tags, OCI labels, SBOM/provenance, and exact registry-digest scan/smoke. No automatic `latest`; third-party images are not mirrored. |
| Medium | Frontend could become generic, insecure client-side RBAC, or compete with an unstable backend contract. | Separate CK FE brief defines Field Ledger direction, role/task IA, BFF recommendation, WCAG/performance budgets, anti-slop rules, chart-table fallbacks, and backend phases 1-3 entry gate. |

## Final security posture

- Authentication and authorization are separate: valid token alone grants no business permission.
- Tenant isolation is defense-in-depth: scoped queries plus PostgreSQL `FORCE ROW LEVEL SECURITY`; runtime is not owner/superuser/`BYPASSRLS`.
- All facts are append/correction/reversal oriented; no public business `DELETE` route exists.
- Backend runtime cannot write Bronze/Silver/Gold, SQLite, manifests, or artifact paths. Python remains sole analytics publisher.
- Outbox is a reliable handoff boundary, not a claim that Kafka/Python consumption already exists.
- Docker Hub publication is authorized for the release phase, but cannot execute until namespace/visibility and CI credentials exist and all gates pass.

## Residual release gates

These are explicit product/operations decisions, not hidden implementation assumptions. They do not block phase-1 code, but they block the affected production capability:

- Production OIDC issuer/provider, audience, MFA/step-up, and browser session lifetime.
- Audit/event retention and privacy policy.
- PostgreSQL RPO, RTO, backup retention, encrypted off-host destination, and named restore owner.
- Docker Hub namespace, repository visibility, token ownership/rotation, vulnerability exception SLA, and image retention.
- Vietnamese-only vs bilingual frontend launch and whether AgriCore supplies a visual source of truth.

## Validation conclusion

No unresolved critical/high planning defect remains. Phase 1 may start under TDD and the disk guard. Production remains NO-GO until the external release gates above are approved and empirically proven; no plan wording may be used as evidence that those drills or deployments already passed.

## Unresolved questions

Only the residual release gates listed above.
