---
title: Phase 1 Cost Data Contracts
date: 2026-07-18 17:02
severity: medium
component: cost data contracts
status: resolved
---

# Phase 1 Cost Data Contracts

## Context

Phase 1 split three fields that had been drifting together: operating cost, procurement spend, and inventory valuation. The goal was to stop pretending they are interchangeable and make the contracts fail closed instead of guessing.

## What Happened

We landed the 9-frame typed contracts and validation path for cost data. The contracts now keep operating cost, procurement spend, and inventory valuation separate, and they reject invalid input instead of coercing it. We also kept farm-without-season as a valid case, because forcing season presence there would have been a fake constraint.

Scout found a stale Gold CSV and manifest mismatch. The fix was narrow: prune only the top-level Gold outputs that were out of sync, not the entire pipeline. That avoided blowing away unrelated artifacts and kept the blast radius small.

The first implementation subagent stalled and had to be interrupted. The first review also misfired, flagging missing default evidence because it was working off stale context. That got corrected on re-adjudication, but it wasted time and made the review look sharper than it was.

## Reflection

This was mostly the right design, but it was not cleanly executed. The hard part was resisting shortcuts: these cost frames look similar on the surface, but collapsing them would have baked bad assumptions into the warehouse and Gold layer. The disk situation on C: made the week uglier than it needed to be; we had to use `-B` and push temp/build work to D: just to keep Phase 2 moving. That kind of pressure is exactly when sloppy defaults sneak in.

## Decisions

- Separate operating cost, procurement spend, and inventory valuation at the contract level.
- Keep the 9-frame typed contract set and fail-closed validation.
- Retain farm-without-season as valid input.
- Fix the Gold CSV/manifest mismatch with a narrow top-level Gold prune only.
- Treat stale-review context as a process bug, not a product bug.

## Next Steps

Phase 2 cannot start on the C: path as-is. Move the remaining heavy build/temp work to D: and keep using the disk-pressure workaround until capacity is fixed. Reuse the typed cost contracts as the only accepted input path so the split stays real.

## Unresolved Questions

None at the moment.
