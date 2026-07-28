# Red-team review: realtime operational alert-center plan

Date: 2026-07-28  
Scope: plan only; no implementation changed

## Summary

Six findings were raised. All were accepted and incorporated before Phase 1.
No code or release claim was made during review.

| # | Severity | Finding | Verdict | Plan correction |
|---|---|---|---|---|
| 1 | Critical | Existing strict v1 parser rejects DLT framework headers; a reused error path could self-loop. | Accept | Separate DLT consumer group, bounded value-only envelope validator, terminal non-recursive failure policy, and valid/invalid/observer-failure/no-loop tests. |
| 2 | High | One immutable acknowledgement per profile could not be re-acknowledged after recurrence. | Accept | Immutable acknowledgement revisions keyed by observation timestamp; current state derives from latest revision. |
| 3 | High | Tenant-only RLS could leak same-tenant profile acknowledgement state. | Accept | Composite tenant/profile references, both-context SQL `USING`/`WITH CHECK`, no runtime update/delete, and direct profile/pool-reset tests. |
| 4 | High | Worker topology could enable evaluator under web/runtime credentials. | Accept | Explicit non-web `realtime-worker` profile, startup role verifier, disabled default listener/scheduler, and profile/grant/HTTP absence tests. |
| 5 | Medium | One clean scan can flap/resurrect alerts during an outage. | Accept | Persisted evaluation watermark, clean streak, healthy duration, policy guard, restart/takeover/late-receipt tests. |
| 6 | Medium | Mutable severity/last-observed ordering makes a cursor feed inconsistent. | Accept | V1 removes cursors/history entirely; backend returns a fixed latest-50 open-alert window. |

## Evidence checked

- `RealtimeOperationalEventParser` requires exactly four v1 headers.
- `RealtimeKafkaConsumerConfiguration` uses `DeadLetterPublishingRecoverer`,
  which adds DLT framework headers.
- V20 runtime RLS for metrics is tenant-only; profile helper availability does
  not itself protect acknowledgement data.
- The current worker property class is under `integration.infrastructure` and
  consumer activation is property-based, not a separate deployment guarantee.

## Result

Plan is ready for structural validation after its accepted corrections. Phase 1
may not start until the worker/DLT/revision/profile-RLS requirements remain
present in implementation tasks and tests.

## Unresolved questions

- Production threshold, healthy-duration, retention, terminal observer failure
  topic, and on-call owner need explicit operator approval before external
  release.
- A real VPS/platform and protected registry environment are still required for
  deployment; ChatGPT/Codex is not a hosting target.
