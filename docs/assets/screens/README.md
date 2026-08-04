# Hosted product screenshots

This directory contains 14 curated WebP screenshots of the real AgriInsight UI:
desktop and mobile evidence for Overview, Work, Cost Analysis, Crop Health,
Data Quality, Assistant, and Administration.

The screenshots were captured by the real seven-persona browser gate against
the hosted CI integration stack. They demonstrate a portfolio/pre-production
reference implementation; they are not production telemetry, customer-farm
records, agronomic ground truth, or proof of a production service-level target.
The Assistant pair shows its initial evidence-first workspace before any
provider query is sent, so it does not claim provider quality, latency, or cost.

`catalog.json` is the machine-readable source of provenance. It records the CI
repository, merge commit, run URL, persona, route, viewport, dimensions, byte
size, and SHA-256 for each of the 14 files. `tests/test_portfolio_media.py`
validates that catalog against the committed binaries.

Regenerate the set only through `.github/workflows/ci.yml` and
`scripts/build-demo-media.ps1`. Review every captured frame before importing
the resulting `portfolio-media-<sha>` artifact.
