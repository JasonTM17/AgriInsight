# Phase 4 test validation — 2026-07-28

## Scope

- Realtime E2E source, PowerShell runner, Maven dependency, CI job, and Compose topology.
- Local Docker/Testcontainers execution intentionally omitted: current disk guard is `C: PASS` and `D: WARN` (about 21.5 GB free, below the 25 GB warning threshold).

## Completed checks

| Check | Result | Evidence |
|---|---|---|
| Java test compilation | PASS | `backend/mvnw.cmd ... test-compile` compiled 283 test sources on Java 21. |
| PowerShell syntax | PASS | `run-realtime-e2e-tests.ps1` parses through the PowerShell AST. |
| Hosted-mode rejection on Windows | PASS | A spoofed GitHub-hosted environment still rejects a Windows host before Docker/Maven execution. |
| Compose topology validation | PASS | `docker compose ... config --quiet` passed with config-only dummy inputs. |
| Docs links | PASS | `node .claude/scripts/validate-docs.cjs docs/` found 16 working internal links; config-key notices are repository-wide heuristics. |
| Diff whitespace | PASS | `git diff --check` passed. |

## Realtime gate design

- Uses real PostgreSQL and pinned Apache Kafka 4.3.1 Testcontainers.
- Starts the production non-web worker wiring: `OutboxPublishingSchedule`, `@KafkaListener`, configured DLT error handler, and dedicated realtime database login.
- Verifies broker pause/recovery, duplicate replay, listener stop/start with a unique event while stopped, aggregate ordering, poison DLT coordinates, authenticated tenant summary, and tenant denial.
- Collects 20 sequential end-to-end samples from durable outbox append to authenticated summary and asserts per-run `p95 <= 30s`; writes `freshness_p95_millis` to the test log.

## Remaining evidence

- The first hosted `realtime-e2e` CI run is still required.
- Do not mark Phase 4 accepted, production-ready, or registry-publishable until that run is green.

## Unresolved questions

- None in source validation; hosted execution remains pending.
