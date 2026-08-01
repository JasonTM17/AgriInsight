# Review: hosted Yield Forecast media capture

## Scope

- Plan: `plans/260730-1022-yield-forecasting/phase-03-scoped-api-farm-dashboard-and-hosted-acceptance.md`
- Reviewed: existing hosted capture, media builder, CI upload, Yield panel, a11y/responsive gate.
- No implementation files changed.

## Spec compliance — proposed capture requirements

| Requirement | Required implementation evidence |
| --- | --- |
| Hosted real journey only | Run via existing `run-web-e2e-tests.ps1 -HostedCi -CaptureMedia` after the 1.05M-reading corpus. A local or synthetic screenshot cannot satisfy Phase 3. |
| Stable Yield selector | Scope to `section[aria-labelledby="yield-forecast-title"]`, assert heading `Dự báo sản lượng theo mùa vụ`, then use the named table region `Bảng bằng chứng dự báo sản lượng có thể cuộn`. Do not couple to CSS classes or `table` parent traversal. |
| Data-bearing evidence | Require at least one `tbody tr` and one `details`; `expectReadyPage` alone does not prove the Yield panel is non-empty. Open the first summary and assert the disclosure opens before a GIF frame. |
| Still and GIF coverage | Capture `yield-forecast-desktop.png`, `yield-forecast-mobile.png`, and at least two deterministic `yield-forecast-*.png` frames. Frames should show the table and opened disclosure, not just the farm header. |
| Separate media products | Add a separate `YieldForecastGifPath` and read only `yield-forecast-*.png`. Reusing `forecast-*.png` would mix inventory and Yield frames. |
| CI artifact | Upload a separate `yield-forecast-media-${{ github.sha }}` artifact with desktop/mobile PNG, `yield-forecast-*.png`, matching desktop/mobile WebP, and `assets/generated/agriinsight-yield-forecast-loop.gif`; keep `if-no-files-found: error`. |

## Blocking findings

### High — current responsive/a11y gate never exercises the new panel

`accessibility-and-responsive.spec.ts` scans `/farms` only. It does not follow a
farm link, so neither Axe nor the five viewport overflow checks reaches the
farm-detail Yield panel. Extend the executive flow to resolve a visible
`main a[href^="/farms/"]` URL after login and run both checks against that
farm-detail route. Add keyboard evidence: focus the named table region,
operate the first `summary` with the keyboard, and assert `details[open]`.

The phase also requires 200%-zoom and reduced-motion states. The present five
viewport loop is useful but does not prove either state; add explicit checks
for the Yield route before accepting the phase.

### High — current media build does not meet integrity/provenance acceptance

`build-demo-media.ps1` validates only GIF frame count and an 8 MB GIF cap. It
does not emit SHA-256, dimensions, WebP size bounds, or hosted-run provenance,
all explicitly required by Phase 3. Emit a tracked or uploaded manifest for
the six Yield artifacts with file name, bytes, dimensions, SHA-256, frame
count for the GIF, and the hosted run URL/ID. Verify the committed media later
matches that manifest downloaded from the accepted run.

### High — adding a separate capture spec needs runner-discovery proof

The approved sample is `demo-media.spec.ts`; this review did not inspect the
runner/config selection. A new Yield-only spec is acceptable only if the
`-CaptureMedia` invocation is proven to discover it. Otherwise add the Yield
test to the existing discovered capture spec. Do not claim hosted evidence
until the test name and generated files are visible in the hosted job log.

## Non-blocking implementation notes

- Use the existing executive credentials; their successful `/farms` journey
  establishes a FARMS-authorized principal before selecting the farm.
- Assert the Yield table row exists before changing viewport. Do not require
  `Sẵn sàng`: an authorized item can validly be `Thiếu lịch sử` and still be
  evidence the server supplied verbatim.
- On mobile, retain the panel scope and re-assert its heading before the
  screenshot; captures must fail rather than accidentally photograph a stale
  shell after a viewport change.
- The CI image matrix already depends on `browser-e2e`, so a failed capture or
  media build correctly blocks candidate images. No registry publication
  belongs in this phase.
- CI upload alone cannot put binaries in Git. After a green exact-head run,
  download the Yield artifact, visually inspect it, verify the manifest hash,
  and commit the accepted WebP/GIF in a focused evidence commit.

## Review verdict

Do not mark Phase 3 hosted/media acceptance complete until all three High
findings are addressed and a new exact-head hosted run uploads the distinct
Yield artifact successfully.

## Unresolved questions

- Which capture test files the `-CaptureMedia` runner discovers must be proven
  before placing the Yield test in a new spec.

## Re-review — commit `bf919eb`

Reviewed the committed tree only. The local working copy has a later uncommitted
change to `scripts/build-demo-media.ps1`; it is outside this re-review.

### Resolved findings

- **Capture discovery: resolved.** `web/playwright.capture.config.ts` sets
  `testDir: "./tests/capture"` and `testMatch: "**/*.spec.ts"`; the new
  `yield-forecast-media.spec.ts` is therefore discovered by the dedicated
  capture config without adding a scenario to the main E2E count.
- **Distinct capture/artifact paths: resolved.** The Yield test emits the two
  required still names and `yield-forecast-01/02.png`; the script consumes only
  `yield-forecast-*.png`; CI uploads a separate SHA-named artifact containing
  both PNGs, both WebPs, frames, GIF, and manifest with `if-no-files-found:
  error`.
- **Farm-detail route/a11y coverage: materially improved.** The executive
  quality flow resolves an actual scoped farm URL, runs Axe and five responsive
  viewports on it, and both the overview journey and hosted capture assert the
  named Yield panel/table/disclosure rather than a CSS class.

### Remaining findings

#### High — required keyboard interaction and 200%-zoom overflow are still unproved

`expectFarmDetailYieldEvidence` focuses the table region and `summary`, but
never presses `Enter`/`Space` or asserts `details[open]`. The only open-state
assertion is mouse-driven. Add keyboard activation and the open assertion in
the a11y quality test.

The 720px "200% zoom-equivalent" visit only checks element visibility/focus;
it does not run the existing horizontal-overflow diagnostic. Apply the same
no-overflow assertion at that viewport. These are explicit Phase 3 acceptance
requirements, so hosted/media acceptance remains blocked.

#### High — media metadata is recorded but WebP/dimension limits are not enforced

The manifest now correctly includes schema version, hosted provenance, logical
viewports, selector, repository-relative paths, SHA-256, bytes, dimensions and
GIF frame count for the two published WebPs and GIF. However, `bf919eb` only
rejects GIFs over 8 MB and frame counts below two. It does not reject oversized
WebPs or dimensions outside the declared output contract; it merely records
them. Add explicit fail-closed limits for the final WebP/GIF bytes and expected
maximum dimensions/frame policy before calling the artifact accepted.

#### Medium — `bf919eb` breaks ImageMagick 7-only installations

`Resolve-ImageIdentify` throws if a standalone `identify` executable is absent
(`scripts/build-demo-media.ps1:88-99`). ImageMagick 7 commonly exposes
`magick identify` instead. Hosted Ubuntu likely supplies `identify`, but a
Windows ImageMagick 7 workstation can fail before generating the manifest.
Support the `magick identify` subcommand or document the hard requirement.

#### Low — hosted provenance does not require `GITHUB_SERVER_URL`

The script requires repository, run ID and SHA but not `GITHUB_SERVER_URL`
before composing `runUrl`. GitHub-hosted runners provide it, but requiring it
would make malformed provenance fail closed rather than producing a relative
URL.

### Re-review verdict

The discovery and artifact-routing High finding is closed. Do not mark hosted
Yield evidence accepted yet: address the two remaining High findings, then use
a new exact-head hosted run and validate the downloaded binary hashes against
the manifest.

## Re-review `4d895c4`

Static review of the targeted commit: the remaining High findings are resolved.

- The a11y quality path now focuses the disclosure, presses `Enter`, asserts
  `details[open]`, and performs the existing document-overflow invariant at
  the 720px 200%-zoom-equivalent viewport.
- The manifest now hashes and records every captured Yield screen/frame plus
  the two WebPs and final GIF. Required still names fail on absence; the GIF
  builder still requires two frames and caps it at 8 MB.
- Final WebPs are fail-closed at 1280px × `StillMaxHeight` and 3 MB; the final
  GIF is checked against its 960px width. Hosted provenance now requires
  server URL, repository, run ID and commit SHA.
- The ImageMagick v7-only case is handled: when no `identify` executable
  exists, the builder invokes `magick identify`.

No static regression or remaining High finding found in this commit. This is
not hosted execution evidence: Phase acceptance still requires the exact-head
browser/media artifact, its manifest, and binary-hash comparison after the
GitHub run completes.

## Final responsive re-review `58c5649`

The breakpoint change is correctly scoped. It changes only the existing
`.farmTable` responsive branch from `44rem` to `48rem`, so at the failed 720px
200%-zoom-equivalent viewport the established card/table transformation is
active. The change does not affect layouts wider than 768px; between 705px and
768px it deliberately reuses the already-mobile-tested labels, clipped table
header, wrapping cells, stacked filters and pagination rather than introducing
a second layout implementation.

The existing quality test continues to exercise the actual farm-detail URL,
requires Yield evidence, opens the native disclosure by keyboard, and asserts
page-level no-overflow at 720px. No static CSS side effect or regression found.
Hosted execution remains the final proof for this fix and its media capture.

## Metadata collection re-review `3ab73ba`

Verified the precise StrictMode fix in `Get-MediaManifestEntry`. Wrapping the
entire conditional ImageMagick invocation in `@(...)` preserves an array when
the command emits exactly one metadata line, so the subsequent required
`$metadata.Count` check is valid on Linux ImageMagick 6. It also preserves
zero-output failure handling and multiple GIF metadata lines; the first line
continues to provide the expected width, height and frame-count tuple. No
static regression found in this narrow fix. A new hosted run must still execute
the media builder to replace the failed-run evidence.
