---
title: Local readiness follow-up after cache relief and dependency integration
status: blocked
generated_at: '2026-08-03T15:00:48+07:00'
scope: AgriInsight v0.4.0 local production-readiness continuation
evidence_type: local cleanup, targeted tests, static supply-chain checks, and review
---

# Local readiness follow-up

## Verdict

Production remains **NO-GO**. The repository-owned CI compatibility defect found
in the integrated dependency updates is fixed and covered by targeted tests,
but the default workspace disk guard still fails. Broad local runtime gates and
an exact-head hosted CI run therefore remain outstanding.

Current local head is
`fca2a35df2e2f7d9c02cc6c5ce8d89b8efd50223` on `main`, 20 commits ahead of
`origin/main`. Nothing was pushed or deployed.

## Integrated dependency review

Six local dependency commits and the CLI timeout stabilization commit were
inspected before accepting the combined result:

- PostgreSQL JDBC `42.7.12` to `42.7.13`.
- `actions/setup-java` `v4` to `v5.6.0`.
- Docker setup-buildx, metadata, login, and build-push Actions to their proposed
  major releases.
- Two assistant CLI subprocess timeouts from 10 to 30 seconds.

GitHub API tag resolution confirmed that all five Action full-SHA pins match
their advertised official tags. The current workflow YAML parses, `pom.xml`
parses as XML, and `git diff --check` passes. `actionlint` is unavailable on
this workstation.

Review found one deterministic incompatibility: setup-java v5 exports
`MAVEN_ARGS=-ntp`, while the realtime runner rejects inherited Maven arguments.
Commit `fca2a35d` now verifies that exact case-sensitive value, removes only
`MAVEN_ARGS`, and leaves `MAVEN_CONFIG` and `MAVEN_PROJECTBASEDIR` for the
runner's existing fail-closed checks. CK re-review found no remaining blocker
in the fix diff.

## Targeted verification

The following low-write command ran with Python bytecode and pytest cache
disabled because the default disk guard was not PASS:

```text
python -B -m pytest -p no:cacheprovider \
  tests/test_ci_realtime_e2e_workflow_contract.py \
  tests/analytics_api/test_assistant_latency_cli.py \
  tests/analytics_api/test_assistant_provider_evaluation_cli.py -q
.... [100%]
exit=0
```

The two workflow contract cases and two CLI regression cases passed. A fresh
YAML parse and `git diff --check` also exited 0. These results prove only the
targeted contracts; they do not replace Maven, Docker, browser, restore, or
hosted CI evidence.

Earlier in this continuation, the web contract, typecheck, unit, lint, and
build commands passed. The earlier full Python run reported 471 passed, 3
skipped, and 2 subprocess timeouts; both affected cases then passed in focused
runs, and their timeout-only change is included in the targeted result above.
Because those broad results predate the final combined head, they are retained
as diagnostic history rather than exact-head release evidence.

## Capacity cleanup and current guard

Only ignored, reproducible AgriInsight caches were removed. Backups,
`artifacts/workspace-relief`, recovered diagnostic traces, project data,
installed dependencies, and shared Docker state were preserved. A running
Codex process still locks about 0.33 GiB under
`artifacts/_tmp/vscode-extensions`; the remaining empty `.next` lock was not
treated as reclaimable data.

The latest default guard reported:

| Drive | Free space | Default thresholds | Result |
|---|---:|---|---|
| C | 9.297 GiB | WARN below 10 GiB; FAIL below 8 GiB | WARN |
| D | 18.453 GiB | WARN below 25 GiB; FAIL below 20 GiB | FAIL |

The guard exited 2. Free space also fell while other projects and desktop
applications were active; no external project, shared Docker resource, user
backup, or unrelated process was modified from this workflow.

## Required next actions

1. Restore C to at least 10 GiB and D to at least 25 GiB without deleting
   shared or user-owned data.
2. Re-run the default guard and retain its `policy=default` evidence.
3. Run the full Python, web, Maven, realtime, Docker, restore, security, and
   browser gates at the exact local head.
4. Run hosted CI for the exact integrated head before accepting the major
   Action upgrades or publishing another image set.
5. Keep external IdP/MFA, broker, hosting/TLS, observability, recovery,
   credential, license, and approval gaps as NO-GO until accountable evidence
   exists.

## Unresolved questions

- Which user-approved storage outside the AgriInsight workspace can be freed
  or relocated to restore the default capacity thresholds?
- When will the integrated local commits be pushed so exact-head hosted CI can
  run? No push is authorized by this local-only continuation.
