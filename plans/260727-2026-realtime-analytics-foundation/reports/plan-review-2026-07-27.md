---
type: code-reviewer
date: 2026-07-27
---

# Plan review: Realtime analytics foundation

## Summary

CK hard/TDD plan is implementable with no unresolved critical/high finding.
The selected design keeps the operational transaction independent from Kafka,
uses the existing fenced outbox, and places the final dedupe/order boundary in
PostgreSQL. External release approvals do not block internal implementation.

## Scope review

- Direct outbox-to-read-model was rejected because it does not satisfy the
  roadmap's replayable transport requirement.
- Debezium/Connect was rejected for this slice because it adds two operational
  surfaces and duplicates a purpose-built outbox already present.
- Browser SSE, alerts, IoT, ML, and AI remain follow-on work. This is staged
  delivery, not removal from the project goal.
- Four phases are justified by distinct rollback boundaries: contract/role,
  publication, consumption/API, hosted evidence.

## Adversarial findings

| Severity | Finding | Resolution |
|---|---|---|
| High | Producer idempotence cannot dedupe an application resend after an ambiguous response across producer sessions. | Plan explicitly keeps at-least-once semantics and requires consumer event-id/checksum dedupe. |
| High | A generic login inheriting integration could broaden cross-tenant access. | Dedicated realtime login may inherit only the NOLOGIN integration role; exact membership and unrelated-table denials are acceptance tests. |
| High | Listener acknowledgment inside a DB transaction could commit the Kafka offset before PostgreSQL. | Use record acknowledgment after listener return; transactional service completes before the container commits the offset. |
| High | A poison record could block a partition forever or be silently skipped. | Bounded retry followed by confirmed same-partition DLT publication; no log-and-drop handler. |
| Medium | First observed aggregate version can be greater than zero for an existing aggregate. | Record an explicit first baseline; require consecutive versions afterward. |
| Medium | DLT partition mismatch can fail recovery. | Main and DLT topics have the same explicit partition count and a contract test. |
| Medium | Kafka auto-configuration could contact a broker during normal API startup. | All topic, listener, publisher, and schedule beans are property-conditional; unreachable-broker startup test required. |
| Medium | Raw payload persistence would create a second PII/secret-bearing store. | Read model retains metadata and SHA-256 only; DLT remains broker-governed operational evidence. |

## Verification results

- Tier: Standard
- Claims checked: 40
- Verified: 40
- Failed: 0
- Unverified: 0

### Fact checker

- Outbox drain/store/writer paths exist and match the stated responsibilities.
- V18 creates the outbox and V19 applies FORCE RLS/integration policies.
- JSON Schema v1 requires the exact envelope fields used in the plan.
- `agriinsight_integration` is NOLOGIN and the bootstrap gate currently rejects
  unapproved memberships.
- Schema readiness is 19; V20 is the correct next immutable migration.
- Permission catalog has 19 entries and a count assertion that must become 20.
- Security uses an exact route registry plus `anyRequest().denyAll()`.
- Tenant application services use `@TenantScoped`.
- OpenAPI artifact export and TypeScript generation paths exist.
- CI already separates backend, security, browser, and image gates.

### Contract verifier

- `CommandExecutionService` is the single source of `CommandCommittedEvent`.
- `PostgresOutboxWriter` is the only writer implementation.
- Current drain construction appears only in focused unit/integration tests;
  adding one conditional production bean has bounded consumers.
- Adding `REALTIME_READ` requires updates to `Role`,
  `AuthorizationCatalogTest`, authorization docs, route registry, HTTP security
  tests, and generated OpenAPI types; all are named in Phase 3.
- Adding V20 requires the application readiness default, Flyway integration
  expected version, configuration-safety assertions, and deployment/data docs;
  all are named in Phases 3-4.

## Whole-plan consistency sweep

- Files reread: `plan.md`, all four phase files, transport research, scout report.
- Decision deltas checked: 8.
- Reconciled stale references: 0.
- Unresolved contradictions: 0.

## Unresolved questions

- Production broker/security/retention/on-call choices remain explicit
  deployment inputs and do not block the internal candidate.
