---
title: Realtime analytics foundation technical acceptance
date: 2026-07-28
status: internally-accepted
---

# Realtime analytics foundation technical acceptance

## Decision boundary

The outbox-to-Kafka-to-PostgreSQL summary transport has hosted technical evidence sufficient for internal acceptance. This report does not authorize a production release, broker rollout, registry promotion, Docker Hub publication, or a product-wide latency SLA.

## Immutable hosted evidence

| Evidence | Result |
|---|---|
| Source commit | [`90131d26da8694e63899183ebe20b1866943f657`](https://github.com/JasonTM17/AgriInsight/commit/90131d26da8694e63899183ebe20b1866943f657) |
| Full workflow | [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) — success across backend, web, analytics, security, browser, and image gates |
| Realtime job | [`90207600976`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699/job/90207600976) — real PostgreSQL and official Apache Kafka 4.3.1 |
| E2E marker | `REALTIME_E2E result=PASS freshness_seconds=0 recovery_millis=5094 freshness_p95_millis=130 samples=20` |

The 130 ms p95 is an observed result for this 20-sample hosted run against the documented `<= 30s` per-run acceptance target. It is not a production SLA.

## Acceptance matrix

| Requirement | Evidence | Result |
|---|---|
| Durable outbox event reaches authorized tenant summary | Real PostgreSQL/Kafka E2E traverses publisher, consumer, RLS read model, authenticated summary route | PASS |
| Duplicate/replay is idempotent | Same event replay leaves metric count unchanged; consumer receipt/checksum behavior is asserted | PASS |
| Poison/gap behavior fails closed | Malformed record follows bounded retry to DLT and preserves source coordinates | PASS |
| Broker and consumer recovery lose no accepted event | Kafka interruption and listener stop/start with a unique event are covered; recovery completed in 5094 ms | PASS |
| Tenant isolation remains intact | Authenticated summary and RLS/privilege checks run in the gate | PASS |
| Freshness remains bounded | 20 accepted samples, `freshness_p95_millis=130` against `<= 30s` | PASS |
| Existing project gates remain intact | Full workflow `30337950699` completed successfully, including security, browser, and four image build/scan/smoke jobs | PASS |

## Review and security basis

- [Phase 4 review](./code-review-2026-07-28-phase-04.md) reported no unresolved Critical or High source finding.
- [Recovery compatibility review](./code-review-2026-07-28-realtime-recovery-fixes.md) verifies timestamp normalization, legacy timestamp equivalence, bounded Kafka metadata waits, and error classification.
- [Security scan](./security-scan-2026-07-28-phase4.md) found no tracked secrets and zero production web dependency vulnerabilities. Eleven high advisories remain in development-only lint/OpenAPI tooling and require a separate compatibility-tested migration before a production release.

## Current-main verification

Commit `90131d26da8694e63899183ebe20b1866943f657` adds compatibility for queued payloads created before timestamp normalization. Focused regression validation passed 8 tests with zero failures/errors/skips. Its full hosted CI [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) completed successfully, including the real PostgreSQL/Kafka, browser, and image gates.

## Rollback and deferred scope

- Disable `AGRIINSIGHT_REALTIME_PUBLISHER_ENABLED` and `AGRIINSIGHT_REALTIME_CONSUMER_ENABLED`; operational commands continue committing to the durable outbox.
- Do not drop V20/V21 read models. Repair uses a forward migration or controlled replay.
- Production Kafka count, replication, SASL/TLS, retention, monitoring, on-call ownership, production OIDC, backup/RPO/RTO, protected registry reviewer approval, and Docker Hub credentials remain external owner decisions.
- SSE, alert presentation, IoT ingestion, ClickHouse/dbt/Airflow, ML forecasting, and AI/Text-to-SQL remain out of scope.

## Unresolved questions

- None inside the accepted technical slice. Production-owner decisions remain open by design.
