# Phase 3 API, dashboard, and hosted acceptance

- Date: 2026-07-30
- Accepted implementation: `aa12c8728bd580ae42abe5ac680d7e9b7a1364c8`
- Behavior CI: [30469892794](https://github.com/JasonTM17/AgriInsight/actions/runs/30469892794)
- Closeout CI: [30504951460](https://github.com/JasonTM17/AgriInsight/actions/runs/30504951460)

## Accepted scope

- Publishes one exact nested `forecast` object per authorized inventory item
  after Spring-resolved warehouse scope and verified snapshot reconciliation.
- Preserves `predicted30dNeed` and `recommendedOrderQuantity` as separate
  run-rate policy values.
- Publishes five label-free scoped `forecastHealth` counters and caps ABC,
  alerts, and items at 100.
- Measures the final serialized UTF-8 response, caps it at 1 MiB, and returns
  sanitized `503 analytics_response_too_large` when it cannot fit.
- Validates the exact generated TypeScript contract and renders Vietnamese
  status, freshness, point/range, model, history, horizon, days-of-supply, and
  rolling-origin backtest evidence without browser forecast math.
- Keeps every horizontal inventory table keyboard-scrollable with a visible
  focus indicator and distinct accessible name.
- Does not submit a purchase order, mutate the inventory ledger, claim
  advanced-ML accuracy/SLA, or claim external production deployment.

## Verification

| Gate | Accepted result |
|---|---|
| Analytics API full suite | 145 passed |
| Focused forecast API boundary | 31 passed |
| Inventory web contract | 30 passed |
| Media/runner source contract | 10 passed |
| Container release contract | 12 passed |
| TypeScript and targeted ESLint | Passed |
| Capture config discovery | 4 scenarios listed |
| Behavior hosted CI | 10/10 jobs passed |
| Closeout hosted CI | 10/10 jobs passed |
| Real platform | Keycloak, PostgreSQL, Spring, FastAPI, Next, Chrome, seven personas, 1.05M readings |
| Candidate images | Python, backend, web, analytics API built, scanned, and smoked without push |

## Failure-driven closeout

The hosted path was not rubber-stamped. Each failure changed a bounded contract:

1. `30471181095`: capture config resolved from repository root incorrectly.
   Commit `d2a5d25` made the root-relative path explicit.
2. `30472218658`: cancelled after local config discovery exposed CommonJS-only
   `__dirname`. Commit `0a7c363` moved capture output to `import.meta.dirname`.
3. `30472745909`: axe found an intermittently overflowing inventory table
   without keyboard focus. Commits `019a04c` and `63701cb` added labeled focusable
   regions, an unclipped focus ring, and direct regression coverage.
4. `30501424408`: the optional media helper assumed every populated route had a
   table/article/list and treated an inner Inventory heading as the page `h1`.
   Commit `cdb9ff4` bound readiness to route-specific proven markers.
5. `30503242875`: browser and capture passed, but ImageMagick rejected a very
   tall mobile WebP canvas. Commit `b4c4242` bounded stills to 1280 × 12000;
   a real 20 × 17000 smoke produced a valid 14 × 12000 WebP.
6. `30504006050`: media generation passed, but visual review rejected a
   mid-table screenshot and Trivy found two HIGH build-tool findings in the
   analytics venv. Commits `99a1868` and `aa12c87` aligned the viewport to the
   forecast boundary, made all three GIF frames distinct, stopped upgrading
   pip nondeterministically, and removed pip/setuptools/wheel from runtime.

## Visual evidence

| Asset | Dimensions | SHA-256 |
|---|---:|---|
| Architecture SVG | scalable | `7e83027f5f3ef8406ea026b7036ac03808620332ee55d73b926ee60452c3d3f2` |
| Architecture PNG | 1920 × 1216 | `711acb107be5ef3e62a5b60b58a15215cfa79cc9edec207f358bf8e20ec9ab1e` |
| Desktop forecast WebP | 1280 × 800 | `e7e578a2815f0a70e3aa042b96940c95d62f0dbe41e3eea0f958b6934ab2b1fa` |
| Forecast GIF | 960 × 600, 3 frames | `5a9d5d16653184dc051f2c4f367d6dc385b424a34131ffc8109bbd3c729fba00` |

The desktop, mobile, and three raw forecast frames were inspected directly.
The accepted desktop still shows the section title, freshness, aggregate
health, server status, point, range, days-of-supply, and suggested order. The
GIF moves from the left evidence boundary to expanded model/backtest evidence
and then the middle forecast fields.

## Workstation and release boundary

C had 13.81 GiB free and D had 14.51 GiB free at closeout. Docker, browser,
big-data, and four-image work stayed on guarded hosted storage. No unrelated
container or user-owned untracked path was removed.

The closeout run built four candidates without push. Registry publication
remains a separate protected tag workflow with reviewer, digest, SBOM,
provenance, scan, pull, and smoke gates.

## Unresolved questions

None within Phase 3. External production hosting and advanced-model SLA remain
separate owner decisions.
