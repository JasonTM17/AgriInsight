---
title: Local production-like readiness gate
status: blocked
generated_at: '2026-08-03T13:40:03+07:00'
scope: AgriInsight v0.4.0 local production-like validation
evidence_type: fresh local read-only and zero-write commands
---

# Local production-like readiness gate

## Summary

Local readiness is **blocked by the default workspace disk policy**, not by a
test failure. No Docker, database, browser, build, or cleanup operation was
run after the guard failed. The fresh zero-write evidence below was recorded at
Git head `55fdf3c50e807aeaf7e6a857207071c878edf167` on
`2026-08-03T06:40:03.4903143Z`; it does not prove runtime gates that require
capacity.

## Fresh passing evidence

| Gate | Command | Result |
|---|---|---|
| PowerShell syntax | Parse all 22 `scripts/*.ps1` and `scripts/*.psm1` files | Exit 0; `errors=0` |
| Tracked-diff whitespace | `git diff --check` | Exit 0; does not include untracked reports |
| Python report asset syntax | `node --check src/agriinsight/report-assets/build-cost-report.mjs` | Exit 0; no stdout |
| OpenAPI contract drift | `npm --prefix web run contracts:check` | Exit 0 |
| Web types | `npm --prefix web run typecheck` | Exit 0 |

The previously recorded 58/58 focused release/promotion/recovery result is
historical evidence, not a fresh result in this report. It is not used to mark
any currently unrun runtime gate as passing.

## Capacity gate

At `2026-08-03T13:34:00+07:00`, the default guard reported:

| Drive | Free space | Thresholds and raw policy | Result |
|---|---:|---|---|
| C | 9.408 GiB | WARN below 10 GiB; FAIL below 8 GiB; `policy=default` | WARN |
| D | 18.358 GiB | WARN below 25 GiB; FAIL below 20 GiB; `policy=default` | FAIL |

The default guard exited `2`, so it blocks every local entrypoint that invokes
it. The backend runner requires `overall=PASS` but does not distinguish default
from override output. The web E2E runner aborts on the current nonzero exit but
does not separately require PASS after a WARN. The guard supports explicit
threshold overrides down to 8 GiB and labels individual drive lines
`policy=override`; the raw rows above prove no override was set or used here.
An override result must never be described as default-policy capacity evidence.

## Gates not run

The following are intentionally unrun, not passing: full `python -m pytest`,
Python compile/wheel smoke, web unit tests/lint/audit/build, Maven `verify`,
Compose config/build/up, V30 restore drill, PostgreSQL/Kafka realtime gate, and
the seven-persona browser gate.

## Capacity diagnosis

`artifacts/_tmp` contains about 4.58 GiB of ignored, regenerable project
cache: 3.35 GiB `vscode-extensions`, 0.69 GiB `ms-playwright-cache`, 0.25 GiB
Maven repository, and 0.23 GiB realtime compile output. Removing all of it
would still leave D below the 25 GiB PASS threshold. Docker also reports
21.31 GiB of reclaimable build cache, but it is shared with active Eventory,
PipeForge, Supabase, TravelAI, and other project workloads. It must not be
pruned from this project workflow.

## Required next actions

1. Restore C to at least 10 GiB and D to at least 25 GiB without deleting
   active containers, volumes, user data, backups, or shared Docker cache.
2. Re-run the default disk guard; a default PASS is the safe baseline before
   broad local runtime work. The current backend runner accepts any reported
   PASS, so operators must retain the per-drive `policy=default` lines when
   claiming default-policy capacity evidence.
3. Run every unrun gate above through the guarded project entrypoints and add
   their exact output to a follow-up evidence report.

## Unresolved questions

- Which explicitly approved, project-owned cache locations may be removed or
  relocated to restore the required capacity?
