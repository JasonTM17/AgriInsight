# Phase 4 code review — 2026-07-28

## Scope

- Real PostgreSQL/Kafka E2E path, worker harness, runner, CI topology, Maven dependency, Compose deployment contract, and supporting docs.

## Result

No unresolved Critical or High findings.

The review verified that the test now uses production worker wiring rather than test-only publisher/listener substitutes. It pauses the actual Kafka container, exercises recovery, stops and starts the real listener, verifies a unique event is not lost during downtime, validates DLT origin coordinates, traverses the authenticated summary route, and measures a 20-sample p95 freshness target.

## Findings resolved during review

| Finding | Resolution |
|---|---|
| Hosted mode could be invoked locally | Restrict `-HostedCi` to GitHub-hosted Linux environment markers and an existing rooted `RUNNER_TEMP`. |
| Test bypassed deployed worker wiring | Start a secondary non-web Spring context with real scheduler, listener, error handler, Kafka and PostgreSQL configuration. |
| Restart check covered only duplicate replay | Publish a unique ordered event while listener is stopped; require its offset and metric after restart. |
| Compose differed from tested topology | Enable both publisher and consumer in `realtime-worker`; update deployment docs. |
| Freshness target was not a p95 metric | Define and assert 20-sample `p95 <= 30s` from durable append through authenticated summary. |
| Runner cleanup could overlook non-container resources | Snapshot Testcontainers-labelled containers, networks, and volumes, then fail only on newly-created owned resources. |

## Static validation

- Maven `test-compile`: PASS (282 test sources).
- PowerShell AST parse: PASS.
- Compose config: PASS.
- Documentation internal links: PASS.
- `git diff --check`: PASS.

## Hosted completion

No local Docker E2E was started while D is WARN. Hosted workflow [`30337950699`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699) completed successfully; realtime job [`90207600976`](https://github.com/JasonTM17/AgriInsight/actions/runs/30337950699/job/90207600976) passed the real PostgreSQL/Kafka recovery, replay, DLT, RLS, and authenticated-summary gate. Phase 4 is internally accepted.

## Unresolved questions

- No source-level blocker. Production Kafka operations, protected registry release, and Docker Hub publication remain owner-gated.
