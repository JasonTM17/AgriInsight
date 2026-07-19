# Cost Dashboard Phase 3 — Review Found the Bugs the Happy Path Hid

**Date**: 2026-07-19

**Component**: Cost Analysis dashboard / local reporting operations

**Outcome**: shipped locally, independently reviewed, not yet public-network safe

## What Actually Happened

The first polished dashboard was not landing-ready. Happy-path AppTest passed,
yet review found two severe truths: Compose exposed an unauthenticated dashboard
on every host interface, and a concurrent pipeline run could mix Cost Gold files
from different generations. That was uncomfortable because the UI looked done.
It was not done.

The fix became an integrity boundary: read manifest A, hash and parse the same CSV
bytes, read manifest B, accept only identical manifests and matching checksums,
retry one transition, then fail closed. Compose now publishes only
`127.0.0.1:8501`; Gold stays read-only while only `_tmp` is writable.

Review kept earning its cost. It also caught display-name aggregation merging
different supplier/warehouse/material codes, raw font/runtime paths reaching UI,
unbounded Streamlit cache entries, a v1 filter-hash compatibility break, and a
read-only Docker mount that made optional XLSX impossible. Each issue had a real
failure mode; none was dismissed as theoretical.

## Decisions

- Chose checksum handshake over a broad version-directory pipeline rewrite.
  Atomic version directories remain stronger long-term, but the handshake closes
  the current read race without destabilizing the proven pipeline.
- Preserved the v1 hash when season is unselected instead of silently renaming all
  legacy exports.
- Grouped procurement by business codes plus names. Display names are labels, not
  identities.
- Logged runtime diagnostics server-side and returned fixed UI messages. Operators
  keep evidence; users never receive machine paths or stderr.
- Kept authentication out of this phase, but enforced loopback publication. The
  honest boundary is local/internal, not “production public.”

## Evidence

- Full suite: `65 passed, 3 expected skips`, no failures, 75.6 seconds.
- Wheel: 622,367 bytes; SHA-256
  `1F8599D7616B5A038CCE420371DBAF76618B921DB35664C273FFFFE2A730B1B7`.
- Independent review: zero active findings.
- Desktop and 390×844 browser QA: no clipping, console error or page error.
- Final disk evidence: C/D both PASS; temp, cache and build output stayed on D.

The relief is real: this phase now feels defensible, not merely attractive in a
demo. The frustration was also useful. Every serious defect came from a boundary
the first implementation did not model explicitly.

## Next Actions

1. Land the phase as small conventional commits; do not push without approval.
2. Start business-backend and authentication/RBAC planning.
3. Keep dashboard loopback-only until identity, permission and row-level checks
   are implemented and tested together.

## Unresolved Questions

None for Phase 3. Product choices for the authentication milestone belong in its
own plan rather than being guessed here.
