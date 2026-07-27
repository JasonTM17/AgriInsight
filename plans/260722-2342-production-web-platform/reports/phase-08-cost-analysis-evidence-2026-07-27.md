# Phase 8 Cost Analysis Evidence — 2026-07-27

## Result

Implementation/static gate complete. The feature is intentionally still marked
browser-gate-pending because the guarded local E2E runner refuses to operate
below the repository's 8 GiB C: free-space floor.

## Delivered

- `/costs` route with exactly `operating` and `procurement` lenses.
- Lens-safe URL parser: rejects `inventory`, `categoryId`, duplicate values,
  invalid UUIDs, incompatible filters, and operating windows over 366 days.
- Spring operating adapter with runtime Zod validation for entries/pages and
  summaries.
- FastAPI procurement adapter with canonical operational farm UUID → active
  farm-code resolution, freshness/lineage validation, and bounded detail.
- BFF commands:
  - `POST /api/costs/entries`
  - `POST /api/costs/entries/{entryId}/corrections`
  - both require trusted origin, CSRF, `COST_MANAGE`, bounded JSON and stable
    idempotency keys.
- BFF export:
  - `GET /api/costs/export`
  - allowlisted `csv|pdf|xlsx`, safe stream headers only, no filesystem paths.
- UI panels with source badges, KPI cards, monthly trends, detail tables,
  supplier drivers, lineage and operating/procurement export links.
- `COST_READ` navigation now targets `/costs?lens=operating`; no third
  inventory lens was added.
- Playwright journey checked in at
  `web/tests/e2e/cost-analysis.spec.ts` for lens switching, export links,
  mobile overflow and denied supplier scope.

## Verification

| Gate | Result |
|---|---|
| `python -m pytest` | PASS — 183 passed, 3 intentional skips |
| Cost-focused Python tests | PASS — 49 passed |
| `npm --prefix web run contracts:check` | PASS |
| `npm --prefix web run typecheck` | PASS |
| `npm --prefix web run lint` | PASS |
| `npm --prefix web run test` | PASS — 239 passed, 9 intentional skips |
| `npm --prefix web run build` | PASS — Next 16 production build |
| Guarded browser E2E | PENDING — C: ~2.8 GiB, guard floor 8 GiB |
| Docker runtime health | PASS — AgriCore compose services healthy |

## Commits

- `e0470c5 feat(web): add cost read and mutation adapters`
- `f1f9b1a feat(web): ship cost analysis experience`
- `23b7d63 test(costs): cover browser lens and scope journeys`
- `5e7ef69 feat(web): add operating cost exports`

## Storage and rollback

- Only Docker dangling volumes were pruned after verifying 76 dangling
  volumes; active containers/images were not removed. Docker reported only 99 B
  reclaimed because the remaining reclaimable bytes are shared/virtualized.
- Ubuntu/WSL and browser profile data were not deleted.
- Rollback is commit-scoped: revert the four commits above in reverse order;
  existing Spring/Gold cost contracts remain untouched.

## Unresolved

- Run the guarded Playwright suite and optional media capture after C: reaches
  the configured 8 GiB floor. Do not lower the guard threshold.
- Protected production registry release and reviewer approval remain outside
  this local implementation gate.
