---
phase: 4
title: "Field Ledger shell and design gates"
status: pending
priority: P1
effort: "3-4d"
dependencies: [3]
---

# Phase 4: Field Ledger shell and design gates

## Overview

Build the shared application shell, design tokens, Vietnamese copy catalog, permission-aware navigation, and first-party visual asset sync that every later route phase will consume. Reuse the accepted Stitch overview evidence; generate new Stitch compositions only for Crop Health, Data Quality, and Administration, and never ship raw Stitch exports as runtime code.

## Context links

- `plans/260722-2342-production-web-platform/plan.md`
- `docs/design-guidelines.md`
- `plans/260719-0753-backend-auth-rbac/design-system/MASTER.md`
- `plans/260719-0753-backend-auth-rbac/design-system/stitch/overview/review.md`
- `plans/260719-0753-backend-auth-rbac/design-system/prototypes/overview-prototype-review.md`
- `dashboard/assets/generated/README.md`

## Verified baseline

- The master design system already defines the color tokens, typography, spatial rules, anti-slop gate, and the eight target areas: Overview, Farms, Work, Inventory, Costs, Crop health, Data quality, Administration (`plans/260719-0753-backend-auth-rbac/design-system/MASTER.md:20-45`, `47-82`, `88-99`).
- The accepted overview Stitch export is design evidence only; it uses dead links, public CDNs, and cannot ship as production code (`plans/260719-0753-backend-auth-rbac/design-system/stitch/overview/review.md:23-44`).
- The overview prototype review already proved responsive and accessibility expectations for the shell direction, including skip link, landmarks, keyboard behavior, and reduced motion (`plans/260719-0753-backend-auth-rbac/design-system/prototypes/overview-prototype-review.md:31-54`).
- The visual inventory now has reviewed filenames, alt descriptions, runtime use, evidence rules, and SHA-256 hashes for all eight WebPs, including Work and Administration (`dashboard/assets/generated/README.md`, `tests/test_visual_assets.py`).
- Current Streamlit rendering keeps the crop-health demo-evidence warning explicit; the web shell must preserve the same provenance boundary (`dashboard/page_visuals.py:25-104`).
- Design guidance already says production web work must consume the Field Ledger source of truth, preserve asset provenance, and avoid browser token storage or unproven image publication (`docs/design-guidelines.md:3-24`).

## Requirements

- Functional: deliver the shared `web/` shell, app layout, nav rail, header, route chrome, and placeholder loading/empty/error containers without implementing the later area business pages.
- Functional: encode the Field Ledger tokens in Tailwind/shadcn/Radix primitives and a Vietnamese-first copy catalog.
- Functional: retain all eight reviewed first-party contextual WebPs, record full provenance in canonical `dashboard/assets/generated/catalog.json`, then sync them into generated/ignored web output with hash checks and the crop-health demo-evidence flag preserved.
- Functional: reuse the accepted overview Stitch evidence; create new Stitch evidence only where coverage is missing today: Crop Health, Data Quality, Administration.
- Security and quality: do not ship raw Stitch HTML, CDN font links, or public asset URLs in runtime code.

## Data flow

1. Fresh Spring `/api/v1/me` authorization context from Phase 3 -> permission-derived server nav model -> shell chrome -> route placeholders owned by later phases.
2. Existing eight reviewed assets -> canonical eight-entry catalog -> verify schema/SHA-256/size/signature -> predev/prebuild copy into generated/ignored `web/public/visuals/` plus provenance manifest -> runtime image components.
3. Design evidence flow -> existing overview Stitch review reused as-is -> new Stitch outputs for Crop/Data/Admin stored under the current plan folder -> implementation reads only the approved evidence, never ships the export bundle.
4. Missing asset or provenance mismatch -> shell falls back to non-blocking UI and fails build/test rather than silently swapping files.

## File matrix

- Modify: `web/package.json` - add Tailwind, Radix, shadcn, and shell test scripts.
- Modify: `.gitignore` - ignore generated `web/public/visuals/**` output while preserving canonical dashboard sources.
- Modify: `dashboard/page_visuals.py` - consume the machine-readable catalog.
- Modify: `dashboard/assets/generated/README.md` - human guidance links to `catalog.json`; it is not parsed by builds.
- Modify: `tests/test_visual_assets.py` - validate catalog schema, hashes, dimensions, provenance, and demo-evidence flags.
- Modify: `web/src/app/layout.tsx` - wrap the auth foundation with the shared Field Ledger shell.
- Modify: `web/src/app/page.tsx` - route users into the shell entry behavior established here.
- Create: `web/postcss.config.mjs` using the pinned Tailwind 4/PostCSS plugin; use CSS-first tokens in `web/src/app/globals.css` and do not add a redundant Tailwind config unless a failing compatibility test proves it necessary.
- Create: `web/components.json`
- Create: `web/src/app/globals.css`
- Create: `web/src/styles/tokens.css`
- Create: `web/src/components/app-shell/app-shell.tsx`
- Create: `web/src/components/app-shell/navigation-rail.tsx`
- Create: `web/src/components/app-shell/app-header.tsx`
- Create: `web/src/components/app-shell/skip-link.tsx`
- Create: `web/src/components/app-shell/state-panels.tsx`
- Create: `web/src/components/ui/` - shadcn/Radix primitives used by the shell only.
- Create: `web/src/content/vi/navigation.ts`
- Create: `web/src/content/vi/recovery-messages.ts`
- Create: `web/src/lib/permission-navigation.ts`
- Create: `web/src/lib/visual-catalog.ts`
- Create: `dashboard/assets/generated/catalog.json` - canonical machine-readable source manifest.
- Verify, do not regenerate unless review fails: `dashboard/assets/generated/work-operations.webp` and `dashboard/assets/generated/tenant-administration.webp` - reviewed contextual visuals, each <= 350 KiB, never data evidence.
- Generate, do not maintain: ignored `web/public/visuals/` and its `provenance-manifest.json` during predev/prebuild/container build.
- Create: `web/scripts/sync-dashboard-assets.mjs`
- Create: `web/tests/shell/navigation.test.ts`
- Create: `web/tests/shell/accessibility.test.ts`
- Create: `web/tests/shell/visual-provenance.test.ts`
- Create: `plans/260722-2342-production-web-platform/design-system/stitch/crop-health/`
- Create: `plans/260722-2342-production-web-platform/design-system/stitch/data-quality/`
- Create: `plans/260722-2342-production-web-platform/design-system/stitch/administration/`
- Delete: none.

## Shared shell contracts

- Navigation owns the eight approved areas only. Later route phases supply the page content, not new top-level IA.
- Navigation is advisory and server-derived from fresh Spring permissions, never hard-coded role names or stale session claims. Hidden items never replace backend authorization.
- Vietnamese copy catalog owns labels, empty/error guidance, and recovery text for the shell. Later phases may append domain copy but should not fork shared wording.
- Machine catalog owns `filename`, `sha256`, byte size, dimensions, `alt`, `runtimeUse`, generation provenance, and `demoEvidence`; all eight areas have an entry, but only crop-health remains explicitly flagged as AI demo evidence. Every other image is contextual and cannot carry KPI meaning.
- Stitch rule: reuse overview evidence; produce new Stitch material only for Crop Health, Data Quality, and Administration. Do not ship raw Stitch HTML, CDN font links, or direct export assets in `web/public/`.

## Tests before

- Write shell navigation tests first: permission-based visibility, stale-session-role rejection, active-state behavior, skip link, and keyboard order.
- Write provenance tests that require exactly eight area assets, compare generated web hashes against the reviewed source catalog, and fail on mismatch, missing alt text/provenance, oversized output, or incorrect demo-evidence flag.
- Write accessibility tests for shell landmarks, reduced-motion class behavior, and no page-level horizontal overflow at shell breakpoints.
- Write build-time tests that forbid direct imports of Stitch HTML or CDN-only runtime dependencies.

## Green steps

1. Add Tailwind/shadcn/Radix foundation and encode the Field Ledger tokens from `MASTER.md` into CSS variables plus Tailwind theme aliases.
2. Build the shared app shell and route chrome only: nav rail, top bar, skip link, standard state panels, and slot for later pages.
3. Introduce a server-owned Vietnamese navigation and recovery-message catalog; derive visibility from current Spring permissions, not a local role table.
4. Re-verify the completed Work/Admin sources for composition/accessibility/cultural fit, WebP <= 350 KiB, prompt summary/tool/date/hash, and absence of embedded secrets; regenerate only if a review gate fails.
5. Add `dashboard/assets/generated/catalog.json`, update dashboard/tests/docs to consume or validate it, and make README parsing impossible by design.
6. Run deterministic asset sync in `predev`, `prebuild`, and container build: verify eight entries, size, dimensions, RIFF/WEBP signature, SHA-256, provenance, and crop-health evidence flag before generating ignored web output.
7. Reuse approved overview Stitch evidence without copying runtime HTML. After quota is available, use one reviewed multi-screen Stitch project for Crop Health, Data Quality, and Administration; design evidence never blocks safe local implementation or ships as runtime export.

## Refactor

- Keep shell components separate from later route trees so sequential domain work does not repeatedly edit shared navigation or token files; any future parallel work still requires explicit non-overlapping ownership.
- Keep asset provenance logic in one script plus one manifest module.
- Keep layout state panels generic and reusable; later phases feed real page states into them.

## Focused commands

- `node web/scripts/sync-dashboard-assets.mjs --check`
- `python -m pytest tests/test_visual_assets.py -q`
- `npm --prefix web run test -- shell`
- `npm --prefix web run typecheck`

## Broad commands

- `npm --prefix web run lint`
- `npm --prefix web run test`
- `npm --prefix web run build`
- `git diff --check`

## Acceptance

- [ ] Shared shell, navigation, and token files exist and are ready for later route-area phases.
- [ ] The shell uses the Field Ledger token system and Vietnamese-first catalog rather than placeholder copy.
- [ ] Visual assets are synced from the reviewed dashboard source with verified hashes and preserved provenance metadata.
- [ ] Work and Administration add two generated/reviewed contextual WebPs, giving all eight areas a cataloged visual without turning any image into KPI evidence.
- [ ] `catalog.json` is the only machine source; Markdown is not parsed and generated `web/public/visuals/**` binaries are ignored rather than maintained duplicates.
- [ ] The crop-health visual still renders as explicit AI-generated demo evidence.
- [ ] Overview uses existing approved Stitch evidence; only Crop Health, Data Quality, and Administration receive new Stitch evidence in this phase.
- [ ] No raw Stitch HTML, CDN-only font import, or static export bundle ships as runtime code.

## Risks and rollback

| Risk | L x I | Mitigation |
|---|---|---|
| Shared shell gets rewritten by every route phase | Medium x High | freeze ownership here and isolate later phases to route trees |
| Asset provenance is lost during copy/optimization | Medium x High | canonical JSON catalog, hash-checked generated sync, and provenance tests |
| New Stitch exports creep into runtime | Medium x Medium | test gate forbidding raw Stitch HTML and CDN runtime assets |
| Mixed-language shell copy appears | Medium x Medium | central Vietnamese catalog and snapshot tests |

Rollback: keep the secure BFF foundation from Phase 3 but remove shell exposure and synced assets; later route phases stay blocked until shell/design gates are rebuilt correctly.

## Dependencies, parallelization, and ownership

- Depends on: Phase 3.
- Blocks: Phases 5-10.
- Parallelization after close: later area phases are sequential by default around shared route/nav/client integration; parallel work requires explicit non-overlapping ownership. This phase retains shell, tokens, shared copy, catalog, and asset sync files.
- File ownership: shared shell only. Later phases must not edit `web/src/components/app-shell/**`, `web/src/styles/**`, `web/src/content/vi/navigation.ts`, or `web/scripts/sync-dashboard-assets.mjs` without an explicit shell follow-up.

## Commit groups

1. `feat(web): add field-ledger shell and permission navigation`
2. `feat(web): add ui tokens and vi copy catalog`
3. `feat(assets): add complete visual catalog and verified sync`
4. `test(web): lock shell accessibility and asset gates`

## Success Criteria

- [ ] Shared shell boundaries are stable enough for parallel route work.
- [ ] Design and asset gates are encoded as tests, not tribal knowledge.
- [ ] Runtime ships only first-party reviewed visuals and approved shared tokens.

## Validation log

- Tier: Standard
- Claims checked: 8
- Verified: 8
- Failed: 0
- Unverified: 0
