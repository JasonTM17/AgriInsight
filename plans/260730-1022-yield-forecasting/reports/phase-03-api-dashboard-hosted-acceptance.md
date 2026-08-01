# Phase 3 — scoped API, Farm detail, and hosted acceptance

Status: accepted 2026-08-01

## Scope accepted

- Additive internal `GET /internal/v1/yield-forecast` preserves FARMS scope,
  canonical farm/field/crop/season filters, fixed expected-harvest/season order,
  100-item pagination and 1 MiB response bound.
- Next BFF permits only the exact read. The Farm detail Yield panel validates
  the typed response and renders server forecast/backtest evidence without
  browser calculation or mutation; a forecast failure leaves farm identity and
  observed performance available.
- Responsive/a11y coverage includes keyboard disclosure, reduced motion, 200%
  zoom-equivalent layout, mobile cards, stale/loading/error states and the real
  OIDC persona journey.

## Hosted evidence

- Feature head: `54947ab7a34733273ca3c0e3b76d2cdfe647d94b`
- Hosted CI: [30696001895](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895)
  — 10/10 jobs passed: dependency/config/secret scan, Python analytics, Next
  foundation, Java contracts, real PostgreSQL/Kafka, real seven-persona browser
  plus four no-push candidate-image build/scan gates.
- Accepted artifact:
  `yield-forecast-media-ecfe58ccceee923e43951ce6b3a942581e62a298`, generated
  for GitHub merge SHA `ecfe58ccceee923e43951ce6b3a942581e62a298`.
- Manifest contract: `schemaVersion=1`, GitHub Actions source,
  `JasonTM17/AgriInsight`, run `30696001895`; all 7/7 paths matched declared
  SHA-256 and bytes. The desktop/mobile WebP are 1280×800 / 780×1688; the GIF
  is 960×600 with two frames and 69,400 bytes.
- Visual review passed: desktop and mobile copy is legible, status explanation
  is separated from `Sẵn sàng`, mobile cards wrap correctly, and the second GIF
  frame retains panel alignment while revealing the intentional scrollable
  evidence viewport.

## Review and local checks

- Current capture-helper change: `npm --prefix web run typecheck`,
  `npm --prefix web run lint`, and capture-suite discovery all passed with temp
  files on D. The previous hosted browser/media acceptance covers the actual
  Chromium journey; local browser/Docker was intentionally avoided under the
  C/D disk guard.
- Code review report:
  [code-review-2026-08-01-yield-forecast.md](./code-review-2026-08-01-yield-forecast.md).
- Security review found no Critical/High/Medium/Low issue in the scoped source
  path; secret scanning added no match. See
  [security-2026-08-01-yield-forecast.md](./security-2026-08-01-yield-forecast.md).

## Rollback and open boundaries

Rollback removes the additive BFF operation/panel and deploys the prior
matching analytics/web images with their compatible Gold manifest. Do not serve
a new `yield_forecast.csv` through an old generated client.

This acceptance is internal documentation/demo and decision-support evidence.
It does not approve external VPS hosting, public ingress, production OIDC,
ingress rate limiting, successful-read audit retention, agronomic ground truth,
confidence intervals, model accuracy/SLA, or automatic operational action.

## Unresolved questions

- Which operator owns external ingress rate limiting and successful-read audit
  retention before production promotion?
