# AgriInsight generated media

These first-party visual assets support product documentation and repository
preview only. They are not agronomic observations, model-training data, or
evidence for a production decision.

## Verified product tour loops

`agriinsight-product-tour-desktop.gif` and
`agriinsight-product-tour-mobile.gif` are seven-frame loops derived from the 14
desktop/mobile WebPs in `docs/assets/screens/catalog.json`. The builder verifies
every source SHA-256 against the hosted catalog before writing either GIF.

- Source: real hosted product screenshots from GitHub Actions
  [`30890843798`](https://github.com/JasonTM17/AgriInsight/actions/runs/30890843798)
- Builder: `scripts/build-portfolio-tour-gifs.ps1`
- Order: Overview → Work → Cost → Crop Health → Data Quality → Assistant → Administration
- Desktop: 960 × 600, 7 frames, SHA-256
  `1c27cc2782290e0bdf09249f51aa3c4d2d6b86d848e765c9200a08d1546f3656`
- Mobile: 390 × 844, 7 frames, SHA-256
  `e0b639d70e73b2723a5a412a2ebf63579d803eecbe0ea0f9a141130ea7277d89`
- Boundary: verified UI preview only; not live production telemetry, customer
  data, external deployment evidence, or a service-level claim

## Field Ledger loop

`agriinsight-field-ledger-loop.gif` is a 960 × 480, four-frame contextual loop
derived from the reviewed `dashboard/assets/generated/overview-fields.webp`
scene. It uses a restrained crop progression to give the GitHub project page a
lightweight visual preview without adding a runtime dependency.

- Source: OpenAI-generated first-party demo visual, generated 2026-07-22
- Processing: ImageMagick 7.1.2, palette GIF, 160 ms frame delay, looped
- SHA-256: `7777d130bbec90b7e6c5b59aac6ca6c91d74b7f0348432ae9898550fe60c0314`
- Boundary: contextual marketing/demo only; never a field observation

## Inventory demand forecast evidence

`agriinsight-inventory-forecast-loop.gif` is a 960 × 600, three-frame loop
showing the accepted Inventory forecast panel at the left, evidence, and middle
table positions. `docs/assets/screens/inventory-demand-forecast-desktop.webp`
is the paired 1280 × 800 still.

- Source: real Keycloak/PostgreSQL/Spring/FastAPI/Next/Chrome hosted gate,
  commit `aa12c8728bd580ae42abe5ac680d7e9b7a1364c8`
- Evidence: GitHub Actions
  [`30504951460`](https://github.com/JasonTM17/AgriInsight/actions/runs/30504951460),
  artifact `inventory-forecast-media-aa12c8728bd580ae42abe5ac680d7e9b7a1364c8`
- Processing: ImageMagick 6.9.12.98; WebP quality 82; palette GIF with 1.8 s
  frame delay and infinite loop
- Desktop WebP SHA-256:
  `e7e578a2815f0a70e3aa042b96940c95d62f0dbe41e3eea0f958b6934ab2b1fa`
- Forecast GIF SHA-256:
  `5a9d5d16653184dc051f2c4f367d6dc385b424a34131ffc8109bbd3c729fba00`
- Boundary: verified documentation/demo evidence only; not external production
  deployment, agronomic ground truth, model accuracy SLA, or purchase-order
  authorization

## Yield forecast evidence

`agriinsight-yield-forecast-loop.gif` is a 960 × 600, two-frame loop showing
the accepted Farm detail Yield panel and its scrollable evidence disclosure.
The paired WebP stills are `yield-forecast-desktop.webp` (1280 × 800) and
`yield-forecast-mobile.webp` (780 × 1688).

- Source: real Keycloak/PostgreSQL/Spring/FastAPI/Next/Chrome hosted gate,
  commit `ecfe58ccceee923e43951ce6b3a942581e62a298`
- Evidence: GitHub Actions
  [`30696001895`](https://github.com/JasonTM17/AgriInsight/actions/runs/30696001895),
  artifact `yield-forecast-media-ecfe58ccceee923e43951ce6b3a942581e62a298`
- Processing: ImageMagick 6.9.12.98; WebP quality 82; palette GIF with 1.8 s
  frame delay and infinite loop
- Desktop WebP SHA-256:
  `d16e37cc75d0c20b253f61dc9db6d47a923f831d856f2e80e429ea388beffb73`
- Mobile WebP SHA-256:
  `a508fc698ea4fa5c34d3b6ee46e2312bed8a3fb2377b42257821565648843124`
- Forecast GIF SHA-256:
  `5262363262f15055bcd2ffd63955268c4ff129fa907ed0f3a5fb74eae199198c`
- Boundary: verified documentation/demo evidence only; not external production
  deployment, agronomic ground truth, a confidence interval, accuracy SLA, or
  operational authorization
