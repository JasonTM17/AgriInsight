# Phase 1 contract evidence — 2026-07-23

Status: implementation and verification complete; independent blocker re-review pending.

## Frozen additive reads

The backend now exposes eight bounded reads required by the planned Work and
Administration areas:

- activity assignments
- activity logs
- activity log correction history
- user role assignments
- safe external-identity link status
- farm assignments
- warehouse assignments
- tenant audit events

Every collection enforces `limit=1..100`, `offset=0..10000`, fetches at most
`limit + 1`, and returns an explicit `hasMore` signal. Identity and audit
responses use allowlisted fields; raw external subjects, provider claims,
arbitrary audit metadata, and secrets are not returned.

Work reads use `ACTIVITY_READ`, not the append-only command path. Tenant-wide
readers retain tenant scope, farm managers retain active assigned-farm scope,
and field workers require an active activity assignment with author/assignment
visibility for logs. External-identity and audit helpers execute through
bounded `SECURITY DEFINER` functions; the application role does not receive a
broad table-read grant.

## PostgreSQL and RLS evidence

Focused Testcontainers verification covered the real stores and policies for:

- farm and warehouse assignment pagination/filtering
- activity assignment visibility after revoke
- activity log lineage, worker visibility, manager farm scope, and cross-tenant denial
- role-assignment pagination and cross-tenant target denial
- external-identity filtering/redaction
- tenant-audit filtering/redaction
- runtime helper ownership, grants, bounds, and convergence

Result: 13/13 focused tests passed across 9 suites with zero failures, errors,
or skips.

The guarded broad backend gate then passed:

```text
Surefire: 459 passed
Failsafe/Testcontainers: 100 passed
Failures: 0
Errors: 0
Skipped: 0
```

## Deterministic OpenAPI evidence

Canonical artifact:
`backend/src/main/resources/contracts/agriinsight-api-v1.openapi.json`

Verified inventory:

| Contract item | Count |
|---|---:|
| Paths | 67 |
| Operations | 94 |
| `X-Correlation-Id` request references | 94 |
| Versioned `200` response `ETag` references | 13 |
| Shared header components | 3 |
| Shared parameter components | 3 |

`scripts/export-backend-openapi.ps1 -Check` regenerated the live contract,
compared canonical bytes, and passed with:

```text
sha256=673b2dabb8853d75fff5b719fd1ecfaef350b0b076170e78a63b05fedbb7dfa8
```

The artifact includes `bearerAuth`, shared sanitized problem responses,
correlation metadata on every operation, and the conditional request/response
headers used by mutations and versioned reads.

## Disk and cleanup

The final heavy gates ran with the project disk guard enabled. Last measured
free space was C 13.35 GB and D 25.89 GB. Test containers and temporary auth
runtime data were removed; Maven, npm, Big Data artifacts, source assets, and
unrelated Docker resources were preserved.

## Unresolved questions

None for the Phase 1 backend contract. Production issuer, patched frontend
dependency set, and protected deployment inputs remain later-phase gates.
