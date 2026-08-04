# Hosted portfolio capture regression diagnosis

Date: 2026-08-04

Status: root cause fixed locally; hosted verification pending

## Exact symptom and reproduction

Pull-request CI run `30874797666` at head `caee198e` failed the real
seven-persona browser gate. The media capture command reported:

```text
Error: cost-analysis mobile has horizontal overflow
Expected: "fits"
Received: clientWidth=390, scrollWidth=390, TABLE width=832

Error: tenant-administration mobile has horizontal overflow
Expected: "fits"
Received: clientWidth=390, scrollWidth=390, TABLE width=864
```

The failure reproduces after the real stack reaches mobile capture for
`/costs?lens=procurement` and `/admin?search=tenant-admin&status=active`.
Nine other capture tests passed in the same job.

## Hypotheses tested

1. Page-level mobile overflow: eliminated. Both failures reported root
   `clientWidth=390` and `scrollWidth=390`; the body/root early gate also did
   not return `page-root`.
2. Unstable service or layout readiness: eliminated. The same two deterministic
   wide tables failed after the readiness checks, while nine peer captures
   passed and all non-browser CI jobs succeeded.
3. Containment-guard regression: confirmed. Commit `caee198e` changed the guard
   from accepting the first bounded horizontal scrollport to scanning every
   ancestor. It then rejected the decorative outer `.panel { overflow:hidden }`
   even when that panel fully contained the already-bounded scrollport.

## Evidence chain

- Cost DOM/CSS: `table.dataTable` (`min-width:52rem`) → `.tableScroll`
  (`overflow-x:auto`) → `.panel` (`overflow:hidden`).
- Administration DOM/CSS: `table.dataTable` (`min-width:54rem`) →
  `.tableScroll` (`overflow-x:auto`) → `.panel` (`overflow:hidden`).
- `caee198e` introduced the full-ancestor scan and is the only change between
  the accepted scrollport behavior and the failing behavior.
- Static contract tests asserted source tokens but did not exercise ancestor
  geometry, so they passed while hosted Playwright failed.

## Root cause and blast radius

The exact defect was in the capture assertion: it treated every outer
`hidden`/`clip` ancestor as interactive clipping, even when its rectangle
contained the complete accepted scrollport. Runtime product behavior and data
contracts were unchanged. The helper runs for all seven portfolio surfaces and
both viewports; current CI exposed the false positive only for Cost and
Administration mobile tables. The failure also prevented media building,
artifact upload, and downstream candidate image builds.

## Remediation and prevention

The DOM collector and boundary decision are now separated. The decision still
scans every ancestor and keeps these fail-closed cases:

- root or body exceeds its viewport;
- an outer clip narrows the accepted scrollport;
- interactive clipping occurs before a later scrollport;
- a clip-only path lacks the explicit non-interactive allowlist.

An outer decorative clip is accepted only when it is bounded and contains the
complete already-accepted scrollport. Unit regression coverage exercises the
valid Cost/Admin geometry and the two bypass cases. A headless Chrome smoke
also proved that Playwright can serialize the extracted collector and returns
the contained 390px layout as valid.

## Local verification

```text
focused Vitest: 2 files, 15 tests passed
full web Vitest: 50 files passed, 1 skipped; 400 passed, 9 skipped
Python portfolio media contract: 3 passed
TypeScript typecheck: passed
ESLint: passed, zero warnings
collector serialization and bounded scrollport containment: passed
git diff --check: passed (line-ending notices only)
```

## Unresolved questions

- Does the hosted seven-persona gate pass after the remediation commit?
