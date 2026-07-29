# Phase 2 Gold integration acceptance evidence

Date: 2026-07-29  
Accepted commit: `8149ee17c48ca5b391fb3b8a13dad96a8ec8bfa7`  
Hosted CI: [run 30464080148](https://github.com/JasonTM17/AgriInsight/actions/runs/30464080148)

## Scope accepted

- Materializes deterministic `gold/inventory_demand_forecast.csv` at
  warehouse/material/base-unit grain from bounded warehouse facts.
- Joins validated forecast evidence into `gold/inventory_status.csv` without
  changing `recommended_order_quantity` or legacy `predicted_30d_need`.
- Records manifest row count and SHA-256 checksum through the normal pipeline
  writer; same seed/as-of output remains byte-stable.
- Keeps the external analytics Inventory response legacy-projected. Public
  forecast API/UI work is deferred to Phase 3.
- Does not create purchase orders, mutate ledger facts, or claim advanced ML,
  external production deployment, or new registry publication.

## Contract and safety evidence

- The Gold builder bounds facts to the inclusive 180-day historical window and
  rejects malformed in-window movement data before forecasting.
- Forecast/status joins are one-to-one on warehouse/material and reject base
  unit drift, duplicate keys, stale as-of/history end, invalid model/status,
  non-finite arithmetic, impossible history span, and impossible deterministic
  backtest-window counts.
- Snapshot loading validates the extended internal schema and recomputes
  forecast days-of-supply and suggested order quantity. A tolerance of at most
  two floating-point ULPs or `1e-9` only permits CSV representation noise.
- Tamper tests cover non-finite, finite-but-wrong, large-magnitude, impossible
  date, and impossible backtest evidence after a matching checksum is written.

## Verification

| Gate | Result |
|---|---|
| Focused forecast tests | 47 passed |
| Pipeline tests | 3 passed |
| Analytics API tests | 140 passed |
| Syntax checks | 13 touched files compiled |
| Full local Python regression | 317 passed, 3 intentional skips |
| Independent Phase 2 review | PASS, 96/100; no Critical/High/Medium findings |
| Hosted CI | 10/10 jobs passed |

Hosted CI covered Python analytics, Java backend, Next web foundation,
dependency/configuration/secret scanning, real PostgreSQL/Kafka, the real
seven-persona browser gate with a 1.05M-reading corpus, and four candidate
image builds without push.

## Workstation policy

Before the full local regression, C had 10.87 GiB free and D had 15.62 GiB
free. Docker/Testcontainers, browser, and big-data work stayed hosted; local
tests used `D:\AgriInsight\artifacts\test-temp-root`.

## Phase 3 handoff

Phase 3 must expose only scoped, server-computed forecast evidence through a
versioned public API and the Inventory UI. It also owns API response bounds,
forecast-health aggregates, OpenAPI/codegen, responsive/a11y evidence, and the
truthful SVG/PNG/GIF documentation assets after browser behavior is accepted.

## Unresolved questions

None for Phase 2. Phase 3 product presentation and public contract work remain
open by design.
