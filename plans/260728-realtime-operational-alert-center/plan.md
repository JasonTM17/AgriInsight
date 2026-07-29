---
title: Realtime operational alert center
description: >-
  Deliver a tenant-safe, durable operational alert center for realtime transport
  health without inventing domain signals.
status: in-progress
priority: P1
effort: 5-7d
branch: main
tags:
  - feature
  - backend
  - database
  - api
  - frontend
  - critical
blockedBy: []
blocks: []
created: '2026-07-28T08:07:07.598Z'
createdBy: 'ck:plan'
source: skill
---

# Realtime operational alert center

## Overview

Deliver the first real in-app alert experience after the accepted realtime
transport foundation. It surfaces only evidence the current Kafka/outbox data
can prove: publisher backlog, consumer delivery lag, and parseable records
that reached a dead-letter topic. A user can review and acknowledge an alert;
the worker alone opens, refreshes, or resolves an operational condition.

This is deliberately not a shortcut to low-stock, crop-health, weather, or
overdue-work notifications. Those are separate domain policies that require
source-side evaluation or a new versioned semantic event. The existing Gold
alert feeds remain batch analytics with snapshot lineage and keep their own UI
locations.

Current execution status: Phase 1 worker hardening is complete, merged on
`main`, and released in `v0.2.3`. Its confirmed migration sequence is V23-V28
with expected schema version 28: V23 remains
additive with `NOT VALID` source/evidence checks and requires bounded operator
backfill before worker enablement; V24-V26 each create one scan index
concurrently and V27 adds the readiness-only partial invalid-source-evidence
index. V28 repairs the V22 acknowledgement function through a forward
`CREATE OR REPLACE FUNCTION` migration without rewriting V22. V27 does not
replace the V23 backfill.
Phases 2 and 3 are planned only; no public alert API, acknowledgement route,
BFF route, or UI is complete.

## Scope challenge

- Existing code: V18-V22 outbox/realtime and immutable alert storage,
  `GET /api/v1/realtime/summary`, a hardened tokenless Next BFF, batch Gold
  alert panels, and an inert header bell. The current source/Compose change is
  the private worker hardening only.
- Completed Phase 1 change: a dedicated non-web alert worker with restricted
  login, metadata-only scanner/observer, durable cursors, bounded recovery, and
  no raw payload/error retention. No new broker protocol or domain payload.
- Complexity: three phases and more than eight files are justified by the
  database/RLS, worker, HTTP/BFF, and browser trust boundaries. Splitting the
  lifecycle, contract, and panel avoids duplicate ownership of a migration or
  public route.
- Selected scope: Phase 1 is complete. A public operations alert center
  is planned only for later phases;
  domain alert semantics, WebSocket/SSE, email/SMS/push, and public deployment
  remain explicitly deferred.

## Design decisions

| Concern | Decision |
|---|---|
| Alert source | Metadata-only realtime transport evidence: aged unprocessed outbox events, published-but-unreceived events, and parseable DLT records. |
| Domain boundary | Do not inspect inventory/work/farm tables from the cross-tenant realtime worker. Do not enrich the v1 event envelope. |
| Lifecycle | The released Phase 1 worker upserts a stable `(tenant, policy, dedupe key)` alert, uses a durable cursor plus clean-evaluation hysteresis before resolution, and never records raw error text or payload. |
| User state | Acknowledgement is an immutable per-profile observation revision. A newer observation makes older revisions stale; the same profile can acknowledge the new observation without rewriting history. |
| Authorization | Introduce dedicated read and acknowledgement permissions rather than silently broadening `REALTIME_READ`. Tenant scope derives only from the authenticated database profile. |
| API | Exact, bounded backend operations: latest 50 open-alert feed (no history/cursor in v1) and idempotent acknowledgement. Existing `/api/v1/realtime/summary` stays compatible. |
| Browser transport | Same-origin BFF routes only. Panel uses initial load, manual refresh, and a low-frequency poll only while open; no browser bearer token, SSE, or WebSocket. |
| UI direction | Preserve the reviewed Field Ledger system: dense evidence rows, Vietnamese-first labels, source/freshness provenance, explicit stale/denied/error states, keyboard-first popover. |
| Deployment | ChatGPT/Codex is not a public VPS. Container build/scan/smoke and compose handoff remain valid; actual external deployment requires a real host and protected credentials/reviewer approval. |

## In scope

- Alert policies `OUTBOX_PUBLISH_BACKLOG`, `REALTIME_DELIVERY_LAG`, and
  `REALTIME_DLT_RECORD`, including deterministic severity thresholds from
  worker configuration.
- Preserve immutable V22 alert storage; add V23-V28 hardening, FORCE RLS,
  least-privilege grants, a new worker-only evaluator, and a distinct DLT
  observer consumer group. The
  observer validates a bounded envelope value while treating framework headers
  as untrusted and has a terminal failure path that cannot republish to its
  own DLT topic or forward the original key, payload, headers, or error text.
- Feed, acknowledgement, BFF, header bell/panel, generated client contract,
  server/client validation, and targeted tests.
- CI/hosted acceptance evidence, docs, rollback notes, and a real deploy
  handoff that makes no production claim.

## Explicitly deferred

- Semantic business alerts such as low stock, pest risk, weather action, crop
  health, or overdue work; these need separate owner-approved policy inputs.
- SSE/WebSocket/mobile push/email/SMS, alert assignment/escalation, arbitrary
  notification preferences, and model-generated alert wording.
- Cross-tenant UI filters, client-provided tenant/profile/role values, direct
  browser-to-backend calls, raw event payload/error persistence, and changing
  the v1 operational event schema.
- Public VPS deployment and production readiness claims until a real target
  host plus production environment ownership exist. Protected registry
  promotion is complete for `v0.2.3`.
- The public alert feed/API, acknowledgement UI, and browser alert center until
  Phase 1 worker hardening is verified and Phase 2/3 are implemented.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Tenant-safe alert lifecycle](./phase-01-tenant-safe-alert-lifecycle.md) | Completed and released in `v0.2.3` |
| 2 | [Exact backend alert API and BFF contract](./phase-02-exact-backend-alert-api-and-bff-contract.md) | Pending |
| 3 | [Live operations UX and acceptance](./phase-03-live-operations-ux-and-acceptance.md) | Pending |

## Dependencies

- Uses the internally accepted transport/read-model work in
  [`260727-2026-realtime-analytics-foundation`](../260727-2026-realtime-analytics-foundation/plan.md).
  Production Kafka ownership does not block source implementation. Main CI
  `30413064146` and protected release `30413877863` are the acceptance and
  publication evidence for this hardening.
- Reuses the current opaque-session web candidate in
  [`260722-2342-production-web-platform`](../260722-2342-production-web-platform/plan.md)
  and uses its completed protected `v0.2.3` image promotion.
- Does not depend on the RAG provider-account or hosted latency gates in
  [`260727-2048-deepseek-rag-assistant`](../260727-2048-deepseek-rag-assistant/plan.md).
- Research basis: [backend and UI scout](./research/codebase-alert-center-scout.md)
  and [Field Ledger design rationale](./research/field-ledger-alert-panel-design.md).

## Acceptance criteria

- An alert is created only from a bounded, validated transport condition with
  a provable tenant; malformed records without a tenant never create a tenant
  alert. Valid DLT values with additional Kafka framework headers remain
  observable through the distinct DLT consumer.
- Replaying a source event or rerunning the evaluator cannot duplicate an
  alert. Recovery resolves the same alert record and preserves its history.
- Runtime access is tenant/profile-scoped through FORCE RLS. Alert rows are
  tenant-readable only; acknowledgement revisions are both tenant- and
  current-profile-restricted under SQL policy. The isolated alert worker has
  only selected metadata columns plus alert/cursor state and never gains
  business-table, raw-payload, or error-text access.
- Only the new exact permissions can read/acknowledge the feed. Every mutation
  is idempotent, captures the locked current observation timestamp, and returns
  a currently authorized representation.
- The browser calls a same-origin BFF route only, shows evidence provenance and
  freshness, supports keyboard operation/reduced motion/responsive widths, and
  has clear empty, stale, denied, partial, and retry states.
- Focused unit/HTTP/RLS/Kafka/BFF/component/browser checks pass. Heavy
  PostgreSQL/Kafka/browser verification runs in guarded CI while C/D remain
  below the local heavy-work floor.
- Documentation differentiates this operational alert source from batch Gold
  analytics and records source/Compose behavior separately from unfinished
  migration, release, Docker Hub/GHCR publication, VPS, and production claims.

## Risks and rollback

- The isolated alert worker may receive only its reviewed metadata RLS policies
  and selected columns under the separate no-inheritance login. Reuse of
  `USING (TRUE)` for runtime access, inheritance from integration, payload/error
  access, or business-table access is a release blocker.
- Alert fan-out may grow during an outage. Policies must aggregate by bounded
  dedupe key, use a server-fixed latest-50 open-alert window, and avoid
  retaining raw payload/error text.
- A transient empty scan must not mark a continuing outage resolved. Each policy
  stores an evaluation watermark and clean-streak state; resolution needs both
  a configured healthy duration and consecutive successful clean scans.
- Polling must start only when the panel is open and stop on close/unmount; it
  cannot become a hidden browser realtime channel.
- Rollback disables the evaluator/consumer configuration first, then removes
  the BFF/panel release. The migration is additive and historical alert rows
  are retained rather than deleted.

## Red-team outcome

The plan was adversarially reviewed on 2026-07-28. All six findings were
accepted before implementation: DLT header/parser incompatibility, immutable
acknowledgement recurrence, profile-level RLS proof, worker topology, recovery
hysteresis, and mutable cursor pagination. V1 intentionally removes cursor
pagination in favour of a bounded latest-50 open feed. Full evidence and the
applied corrections are in
[the red-team report](./reports/red-team-2026-07-28-alert-center-plan.md).

## Unresolved owner inputs

- Confirm production thresholds, retention, escalation/on-call owner, and
  Kafka broker monitoring ownership before external release. Safe development
  defaults must be documented and fail closed when worker configuration is
  incomplete.
- Provide a real VPS/platform endpoint, registry namespace, protected
  environment reviewers, and non-secret deployment access only when external
  deployment is intended. ChatGPT/Codex cannot be that host.
