# GitHub social preview verification

Date: 2026-08-04

Status: verified

## Source

- Repository: `JasonTM17/AgriInsight`
- Tracked asset: `docs/assets/agriinsight-social-preview.jpg`
- Dimensions: `1280x640`
- SHA-256: `5328405D189AFB7AF662A013EA6BA64F89548898970235028EBFEE2C82F91C47`

## Owner settings evidence

The authenticated repository-owner Settings page accepted the tracked asset
through **Settings -> General -> Social preview**. After the upload completed,
the Social preview panel rendered the farm image and retained the `Edit`
control; no manual upload handoff remains.

## Public metadata evidence

An unauthenticated request to `https://github.com/JasonTM17/AgriInsight`
returned identical `og:image` and `twitter:image` metadata pointing at
`repository-images.githubusercontent.com`. The resolved public object returned
HTTP `200` with these properties:

| Check | Public object | Tracked source | Result |
|---|---:|---:|---|
| Width | 1280 | 1280 | PASS |
| Height | 640 | 640 | PASS |
| SHA-256 | `5328405D189AFB7AF662A013EA6BA64F89548898970235028EBFEE2C82F91C47` | `5328405D189AFB7AF662A013EA6BA64F89548898970235028EBFEE2C82F91C47` | PASS |

The public metadata therefore resolves to the exact tracked bytes, not a
placeholder or unrelated repository image. Signed query parameters are
intentionally omitted because they expire; the repository-image host, object
identity, dimensions, and digest are the durable verification evidence.

## Boundary

This verifies repository presentation only. It does not represent an external
production deployment, production traffic, or a new container publication.

## Unresolved questions

None for the social-preview requirement.
