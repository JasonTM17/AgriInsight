---
phase: 1
title: Tenant-safe alert lifecycle
status: in-progress
effort: 2-3d
---

# Phase 1: Tenant-safe alert lifecycle

## Overview

Priority: P1  
Current status: in progress
Owner boundary: backend database + separate realtime worker

Create the durable, metadata-only alert lifecycle before exposing any new UI.
It evaluates only transport evidence already owned by the outbox/realtime
system, persists concise tenant alerts under FORCE RLS, and records a current
profile's acknowledgement without changing the underlying operational fact.
`V22` is immutable. The current V23-V27 worker hardening is in progress and
readiness expects schema version 27. V23 adds `NOT VALID` source/evidence
checks, so an operator must complete its idempotent 500-row source-evidence
backfill before worker enablement. V24-V27 create one index concurrently each;
V27 is the readiness-only invalid-source-evidence index and does not replace
the V23 backfill. Phase 2/3 public API, BFF, and UI work has not started.

## Context links

- [Plan overview](./plan.md)
- [Realtime foundation](../260727-2026-realtime-analytics-foundation/plan.md)
- [Codebase scout](./research/codebase-alert-center-scout.md)
- [`D:\AgriInsight\docs\code-standards.md`](../../docs/code-standards.md)
- [`D:\AgriInsight\docs\system-architecture.md`](../../docs/system-architecture.md)

## Key insights

- The v1 Kafka envelope proves tenant/event/aggregate metadata but intentionally
  discards business payload. It must not be stretched into stock, crop, or work
  rules.
- The existing integration worker remains the legacy publisher/consumer path.
  The follow-on alert worker must not inherit it: it reads only selected
  cross-tenant transport metadata and cannot access operational business tables,
  profile assignment data, payloads, or errors.
- Existing metric rows are aggregate-only. A true actionable alert needs a
  separate durable projection, a stable dedupe key, and a lifecycle that can
  resolve after recovery.

## Requirements

### Functional

1. Preserve immutable V22 `realtime_operational_alerts` and
   `realtime_alert_acknowledgement_revisions`; add V23-V27 without rewriting
   applied history. V23 must stay additive/`NOT VALID` and V24-V27 must retain
   one concurrent index per migration. V27 remains readiness-only and does not
   replace the V23 backfill.
2. Implement exactly three first-policy codes:
   `OUTBOX_PUBLISH_BACKLOG`, `REALTIME_DELIVERY_LAG`, and
   `REALTIME_DLT_RECORD`.
3. Evaluate backlog and delivery lag from outbox/receipt/metric metadata only;
   a DLT alert recovers only after its validated event identity is eventually
   present in the receipt projection.
   A separate DLT consumer group observes the DLT topic and validates a bounded
   event *value* against the v1 envelope while treating all Kafka headers as
   untrusted. It creates a tenant alert only when that value proves a tenant.
   Unparseable poison data remains an infrastructure-only code/metric and never
   becomes a guessed tenant alert.
4. The worker upserts a deterministic alert identity and resolves an open
   record only after a persisted evaluation watermark records both the configured
   healthy duration and a minimum number of consecutive successful clean scans.
   It preserves `opened_at`, `last_observed_at`, `resolved_at`, severity,
   policy code, a bounded evidence reference, `clean_since`, and clean-streak
   state.
5. Acknowledgements are current-profile immutable observation revisions. The
   acknowledgement transaction locks the alert, copies its exact
   `last_observed_at` to `acknowledged_observation_at`, and inserts a unique
   `(tenant, alert, profile, observation)` row. The current state is the latest
   acknowledgement at or after `last_observed_at`; older revisions remain
   history and a profile may acknowledge a later recurrence.
6. Add dedicated `REALTIME_ALERT_READ` and `REALTIME_ALERT_ACKNOWLEDGE`
   permissions with an explicit role matrix. Do not broaden `REALTIME_READ`.

### Non-functional

- No raw Kafka value, outbox payload, exception text, bearer token, or request
  body may enter the alert rows, audit data, API, or test diagnostics.
- Alert rule config is bounded, validates positive durations/limits and
  hysteresis values, and is disabled unless the dedicated `realtime-worker`
  process profile is enabled. The worker profile sets
  `spring.main.web-application-type=none`; the default web profile starts no
  evaluator or DLT observer.
- Worker startup must verify the restricted no-inheritance
  `agriinsight_alert_worker` database login. It fails closed if alert evaluation
  is enabled under another credential or if legacy publisher/consumer behavior
  is enabled in the alert-worker service. The worker has no HTTP exposure and
  no business-table, raw-payload, or error-text grants.
- Evaluation is idempotent under retry/concurrency and emits bounded aggregate
  rows rather than one alert per retry attempt.
- All operational runtime paths use tenant/profile context and FORCE RLS. The
  alert worker receives only selected cross-tenant metadata grants needed for
  scanning and recovery; it does not inherit the integration role.

## Architecture

```text
tenants + outbox_events + realtime_event_receipts + alert metadata
                │ (metadata-only restricted alert worker)
                ▼
  RealtimeOperationalAlertEvaluator / DLT observer
                │ stable policy + tenant + dedupe key
                ▼
  realtime_operational_alerts ──< realtime_alert_acknowledgement_revisions
                │                    (current profile only)
                ▼
  later Phase 2: scoped service and exact HTTP/BFF operations
```

### Data model and invariants

| Record | Required invariants |
|---|---|
| `realtime_operational_alerts` | UUID primary key; tenant ID; validated policy/severity/state enums; SHA-256 dedupe key; optional only validated event UUID/correlation ID; one identity per tenant/policy/dedupe key; UTC timestamps; version; `clean_since`, clean streak, and evaluation watermark for recovery hysteresis. |
| `realtime_alert_acknowledgement_revisions` | tenant-aware alert/profile composite references; immutable UUID revision; `acknowledged_observation_at` copied under the alert lock; unique `(tenant, alert, profile, observation)`; no user text or destructive mutation path. |
| Worker evaluation | compares only granted tenant/outbox/receipt/alert metadata in a bounded repeatable-read snapshot, uses a policy advisory lock and durable cursor per policy, upserts/reopens deterministically, checks current conditions before recovery, and does not call business APIs, fetch URLs, or issue database scope from a Kafka header. |
| DLT observer | separate consumer group with its own non-recursive error policy; validates a bounded value/envelope while allowing extra DLT framework headers; never trusts those headers for tenant scope and cannot publish back to its observed DLT topic. It validates the envelope, then in a dedicated transaction looks up `(tenant_id, event_id)` in `outbox_events`, uses database `occurred_at`, and only upserts on a match; unmatched DLTs increment the unverified metric and log a stable event. |

## Related code files

| Path | Action | Purpose |
|---|---|---|
| `D:\AgriInsight\backend\src\main\resources\db\migration\V22__create_realtime_operational_alerts.sql` | Immutable baseline | Alert/revision tables, composite tenant/profile FKs, hysteresis fields, indexes, FORCE RLS policies, permission/role seeds. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V23__harden_realtime_operational_alert_worker.sql` | In progress | Additive `NOT VALID` source/evidence checks, restricted worker RLS/grants, and durable scan cursors; no table-wide legacy-row update/validation. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V24__add_realtime_alert_indexes_concurrently.sql` | In progress | One concurrent backlog scan index with named absent/invalid-index precondition. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V25__add_realtime_alert_delivery_lag_index_concurrently.sql` | In progress | One concurrent delivery-lag scan index with named absent/invalid-index precondition. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V26__add_realtime_alert_unrecovered_dlt_index_concurrently.sql` | In progress | One concurrent unrecovered-DLT scan index with named absent/invalid-index precondition. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\V27__add_realtime_alert_evidence_readiness_index_concurrently.sql` | In progress | One concurrent readiness-only invalid-source-evidence scan index; application readiness expects 27. |
| `D:\AgriInsight\backend\ops\postgres\backfill-realtime-alert-source-evidence.sql` | Run before worker enablement | Idempotent `agriinsight_migrator` backfill, at most 500 valid legacy rows per run; do not enable until both remaining-row checks are false. |
| `D:\AgriInsight\backend\src\main\resources\db\migration\R__tenant_rls_helpers_and_grants.sql` | Modify | Revoke broad access first, then grant minimum columns to runtime, integration, and the separate alert-worker login without inheritance. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\domain\Permission.java` | Modify | Add explicit alert read/ack permissions. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\authorization\domain\Role.java` | Modify | Keep role catalog/permission expectations aligned with SQL seeds. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\RealtimeAlertWorkerProperties.java` | Create | Keep bounded alert/DLT observer/hysteresis settings separate from the established v1 worker contract. |
| `D:\AgriInsight\backend\src\main\resources\application.yml` | Modify | Keep all alert worker/listener paths off by default. |
| `D:\AgriInsight\backend\src\main\resources\application-realtime-worker.yml` | Create | Non-web worker topology; only this profile enables the evaluator/consumer after login verification. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertPolicy.java` | Create | Typed policy codes, severity/state, and deterministic evaluation inputs. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertStore.java` | Create | Narrow store port for worker upsert/resolve and runtime future reads. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertAcknowledgementStore.java` | Create | Lock and insert immutable current-profile acknowledgement observations without an HTTP surface. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertEvaluator.java` | Create | Bounded metadata-only policy evaluation. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\application\RealtimeDeadLetterEnvelopeValidator.java` | Create | Bounded value-only DLT envelope validation that ignores untrusted framework headers. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\PostgresRealtimeOperationalAlertStore.java` | Create | Worker SQL implementation with deterministic locks/upsert behavior. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\PostgresRealtimeOperationalAlertAcknowledgementStore.java` | Create | Runtime SQL implementation that locks and copies the exact alert observation. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\RealtimeDeadLetterAlertObserver.java` | Create | Distinct DLT listener group with no self-DLT publishing path. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\RealtimeOperationalAlertWorkerConfiguration.java` | Create | Keep existing v1 retry/DLT behavior unchanged while wiring observer failures to a distinct terminal topic and committed unattributable metric. |
| `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\infrastructure\RealtimeWorkerRoleVerifier.java` | Create | Fail startup if an enabled evaluator/observer is not using the restricted worker login/topology. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\application\RealtimeOperationalAlertEvaluatorTest.java` | Create | Unit rules for open/refresh/resolve/dedupe/bounds. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\RealtimeOperationalAlertSchemaIntegrationTest.java` | Create | V22/FORCE RLS/grant/role/profile-isolation/pool-reset proof. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\persistence\RealtimeOperationalAlertStoreIntegrationTest.java` | Create | Cross-tenant/profile, replay, reopening, re-acknowledgement, hysteresis, and index behavior. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\infrastructure\RealtimeDeadLetterAlertObserverTest.java` | Create | Prove valid forwarding, malformed non-attribution, and observer-failure propagation without a self-loop. |
| `D:\AgriInsight\backend\src\test\java\com\agriinsight\backend\realtime\infrastructure\RealtimeOperationalAlertWorkerConfigurationTest.java` | Create | Prove distinct terminal topic and worker role validation. |

## Implementation steps

1. Read the V20/V21 migration, repeatable grant file, role bootstrap, worker
   properties, and DLT configuration. Write down the current worker lifecycle
   before adding code; do not alter v1 schema/parser behavior.
2. Define compact immutable application records/enums for policy, severity,
   state, evidence reference, and alert mutation. Reject unknown values before
   persistence. Use explicit `Instant` values supplied by a testable clock.
3. Preserve V22 as applied history. V23 must add metadata evidence shape as
   `NOT VALID` plus durable cursors and worker isolation without table-wide
   legacy updates/validation. Before enablement, run the V23 operator backfill
   as `agriinsight_migrator` in repeatable at-most-500-row batches until both
   remaining-row checks are false; invalid source shape requires correction or
   retirement, never `source_event_id` rewriting. V24-V27 each create one scan
   index concurrently. If one fails with an invalid index, drop that named index
   concurrently and repair/retry Flyway; a valid pre-existing index requires
   history reconciliation rather than retry. V27 is readiness-only and does not
   replace the backfill.
4. Extend repeatable grants after all revokes. Runtime receives tenant-only
   alert select plus acknowledgement revision select/insert constrained by both
   `app_current_tenant_id()` and `app_current_profile_id()`; it never gets
   revision update/delete. The dedicated alert worker gets only selected
   tenant/outbox/receipt metadata plus alert/cursor grants; verify it neither
   inherits integration nor touches inventory, work, farm, cost, identity,
   payload, or error columns.
5. Implement policy inputs through parameterized, bounded metadata SQL:
   pending-publish age, published-without-receipt age, and valid DLT event
   identity. Map condition facts to a stable policy/dedupe key rather than raw
   free-form messages.
6. Implement an isolated `realtime-worker` profile with
   `spring.main.web-application-type=none`, explicit
   `agriinsight_alert_worker` login verification, and listener/scheduler
   conditional configuration. The default web profile must have zero
   evaluator/observer listeners. Only `realtime-alert-worker` disables legacy
   publisher/consumer behavior; prove worker HTTP endpoints are absent and no
   business-table grant is needed.
7. Implement a DLT observer in a distinct consumer group. Its value-only
   validator permits extra framework headers but never trusts them. Classify
   malformed/unattributable messages as a committed bounded metric, and route
   transient observer failures after bounded retry to a distinct terminal
   observer-failure topic with no observer attached; it must never republish to
   the DLT topic it consumes.
8. Implement idempotent upsert/reopen/resolve with a per-policy advisory guard,
   a durable scan cursor, and a stored evaluation watermark. Require
   `healthy_for` plus at least two successful clean scans before resolution;
   recheck the current condition in the same snapshot, reset clean state on a
   fresh condition, record saturation at the configured bound, and test worker
   restart/takeover.
9. Implement immutable acknowledgement revision semantics. Lock the alert,
   copy its current observation time, insert its unique revision, and calculate
   current acknowledgement from the maximum revision. A concurrent evaluation
   must either precede or follow that lock deterministically.
10. Write tests before touching public HTTP. Exercise policy thresholds, clock
   equality, valid DLT with framework headers, malformed DLT, observer failure
   without self-loop, replay, late receipt, resolve/reopen/re-acknowledge,
   RLS/profile isolation/pool reset, worker profile, and concurrent evaluator
   runs.

## Test scenario matrix

| Priority | Scenario | Proof |
|---|---|---|
| Critical | Cross-tenant/profile runtime access | FORCE RLS direct SQL tests; same-tenant different-profile caller cannot read/insert another acknowledgement revision. |
| Critical | DLT extra headers and malformed value | Valid v1 value produces one alert despite extra headers; malformed value produces no tenant alert/raw data. |
| Critical | Observer exception | Bounded retry then terminal distinct topic/metric; no publish or loop back to observed DLT topic. |
| Critical | Duplicate/replayed event | Same dedupe identity; no duplicate alert or acknowledgement revision. |
| High | Publish backlog / delivery lag threshold | Exact below/equal/above duration tests with injected clock. |
| High | Recovery and subsequent recurrence | Stored watermark needs healthy duration + clean streak; same record resolves/reopens and profile can insert a new acknowledgement revision. |
| High | Least privilege | Integration cannot read business tables; runtime cannot update worker-owned fields. |
| Medium | Worker/profile isolation | Web profile owns no scheduler/listener; worker startup rejects runtime credential and HTTP exposure. |

## Todo list

- [x] Freeze policy vocabulary and exclude semantic domain policies.
- [x] Preserve V22 alert/revision baseline, profile RLS, grants, and permissions as immutable history.
- [ ] Complete and verify V23-V27: V23 additive `NOT VALID` evidence/cursor/worker hardening, V24-V27 one concurrent index each, expected schema version 27, and named invalid-index recovery.
- [ ] Prove deterministic dedupe, current-condition recovery, concurrent acknowledgement revision, profile isolation, fair continuation, and saturation semantics.
- [ ] Keep v1 event schema, summary endpoint, and existing Kafka tests compatible; complete migration/test/review/merge before any protected publication.

## Success criteria

- [ ] V23-V27 are proven on fresh and existing schema paths; V22 and every
  applied migration remain untouched. V23 backfill completes before worker
  enablement, V27 remains readiness-only, and V24-V27 recovery never blindly
  retries an index migration.
- [ ] Every alert is deterministically attributable to a valid tenant and
  source condition, with no payload/error/body retention; valid DLT values
  survive extra framework headers and malformed values stay unattributable.
- [ ] RLS and grants prove runtime/integration least privilege, including
  same-tenant profile isolation and context reset.
- [ ] Duplicate/retry/concurrent evaluation cannot create duplicate alerts;
  healthy-duration/clean-streak rules prevent false recovery flapping.
- [ ] Existing outbox/Kafka DLT behavior remains green; no raw payload/error is
  retained by the alert worker.

## Risk assessment

- DLT containers preserve extra framework headers and malformed values.
  Mitigation: separate value-only validator, non-recursive observer policy,
  valid/invalid/retry/no-loop tests.
- An alert policy can become an outage amplifier. Mitigation: aggregation,
  caps, deterministic dedupe, and one scheduled worker path.
- Misconfigured scheduler/role could run in the request process. Mitigation:
  explicit non-web worker profile, role verifier, fail-closed conditional
  configuration, and worker/web separation tests.

## Security considerations

- Never accept tenant, profile, policy, source event, severity, or error text
  from a browser or Kafka header as authoritative context.
- Do not copy integration `USING (TRUE)` policies to runtime tables.
- Protect acknowledgement revisions with both current tenant and current
  profile SQL policies, immutable observation identity, and canonical
  idempotency in Phase 2; no silent update/delete operation is introduced.

## Next steps

Phase 2 may begin only when the confirmed migration, grants, and worker
lifecycle tests prove the alert projection is durable and tenant-safe. Until
then there is no public alert API, BFF route, or UI.
