# Phase 02 — Generated visual assets

## Goal

Create a coherent, locally owned image set for the dashboard and repository
without stock-photo licensing ambiguity or deceptive field-evidence claims.

## Files

- `dashboard/assets/generated/*.webp`
- `dashboard/assets/generated/README.md`
- `docs/assets/agriinsight-social-preview.jpg`

## Asset set

| Asset | Intended use |
|---|---|
| `overview-fields.webp` | Executive overview and repository identity |
| `farm-performance.webp` | Farm Performance |
| `inventory-control.webp` | Inventory |
| `crop-health-evidence.webp` | Crop Health demo evidence |
| `data-quality-sensors.webp` | Data Quality |
| `cost-procurement.webp` | Cost Analysis |

## Rules

- Generate with the built-in image tool; no user API key is needed.
- No logos, watermarks, readable fake labels, or unlicensed third-party images.
- Optimize runtime images as stripped WebP and keep each below 350 KiB.
- Record prompt intent, dimensions, SHA-256, alt description, and provenance.
- Mark the crop-health visual as generated demo evidence in the UI and catalog.
- Do not claim the visuals were captured at a real AgriInsight customer site.

## Validation

- Decode every image and verify expected dimensions/format.
- Confirm catalog paths exist and hashes match.
- Review representative optimized images visually.
- Keep the social preview under 1 MiB at 1280 × 640.
