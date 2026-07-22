# Backend Phase 4 Acceptance

Date: 2026-07-22
Status: ACCEPTED

## Accepted boundary

- Versioned farm, field, crop, season, Employee, activity, activity assignment, activity log, and harvest APIs.
- Tenant-wide readers/admins, assigned-farm managers, and assigned-task workers follow the documented role/scope matrix.
- Farm/activity assignment history is append-preserved; revoke changes scope immediately.
- Task lifecycle and metadata use explicit state/version rules.
- Activity evidence and harvest facts are immutable ledgers with linked corrections.
- Harvest input normalizes KG/TONNE to kg; operating cost remains outside this phase.
- PostgreSQL composite tenant FKs, FORCE RLS, least-privilege runtime grants, tenant-led indexes, and V1-V11 lifecycle migrations remain enforced.

## Acceptance evidence

| Gate | Result |
|---|---|
| Disk pre/post guard | PASS — C 11.279 GB, D 28.251 GB after verify |
| Backend guarded `mvn verify` | PASS — 353 unit/HTTP/security/module + 77 integration, 430 total |
| PostgreSQL/Flyway | PASS — PostgreSQL 18, V1-V11 + repeatable migration |
| Module boundary | PASS — Spring Modulith verification |
| Activity/assignment/log focused tests | PASS |
| Harvest domain/service/HTTP/persistence tests | PASS |
| Analytics regression | PASS — 65 passed, 3 expected optional-PDF skips |
| Git whitespace check | PASS |
| Testcontainer cleanup | PASS — no project test container remains |

## Security and data guarantees

- Authorization occurs before idempotency claim; unknown or hidden targets return safe errors.
- JWT tenant/role claims are not trusted; database-enriched principal and fixed permission catalog remain authoritative.
- Worker log author/employee lineage is checked at service and persistence boundaries.
- Evidence URIs cannot trigger backend fetches.
- Runtime roles cannot update/delete immutable activity logs or harvest facts.
- API responses do not expose tenant IDs or sensitive workforce attributes.

## Deferred by design

- Inventory/procurement APIs and warehouse scope: Phase 5.
- Operating-cost ledger and reporting boundary: Phase 6.
- Outbox, protected CI, scans, SBOM/provenance, Docker Hub/GitHub Packages publication: Phase 7.
- Production IdP fixtures, MFA policy, audit retention, backup/restore objectives: deployment decisions.

## Unresolved questions

None for Phase 4. Deferred production decisions remain tracked in deployment documentation.
