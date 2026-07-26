# Phase 5 UI/UX review — Overview and farm intelligence

Date: 2026-07-26
Scope: current uncommitted Overview/Farms routes, feature components/loaders, Phase 5 plan. Read-only review.

## Verdict

Direction is credible, but Phase 5 is not UI-acceptance ready. Server rendering, canonical image use, table semantics, locale formatting, and partial-source survival are good foundations. Filters, panel context, recovery states, chart meaning, mobile farm rows, and user-facing copy still need correction.

## Blocking findings

1. **Required filters fail as a generic page error**
   - `web/src/features/overview/overview-filter-schema.ts:44-53` explicitly rejects `fieldId`, `cropId`, `seasonId`, and non-default `datePreset`, although Phase 5 requires these URL filters.
   - `web/src/app/(platform)/overview/page.tsx:25-35` and `web/src/app/(platform)/farms/page.tsx:25-35` swallow the reason and show a generic failed panel.
   - Implement the server resolution chain before acceptance. Until then, unsupported filters need a specific, recoverable message; never turn a valid deep link into unexplained “Tải dữ liệu thất bại.”

2. **Scope is absent from visible lineage and panels**
   - `web/src/features/overview/components/lineage-banner.tsx:5-8` accepts `scope` but `:19-40` never renders it.
   - Overview KPI/trend/risk sections and farm KPI detail do not show their period, farm/season scope, or cutoff beside the values. One global banner does not meet “scope, freshness, and lineage beside each analytic panel.”
   - Add a concise panel context line: scope + period/date granularity + `asOf`; keep safe run/contract/fingerprint metadata available without filesystem details.

3. **Chart/table parity is visually and numerically incomplete**
   - `monthly-financial-trend.tsx:34-45` has no visible legend, axis, unit, baseline, or exact value.
   - `:40` converts negative profit to an upward absolute-height bar, hiding loss direction.
   - `:7-12` uses compact currency for the “equivalent” table, so the accessible alternative is not exact.
   - Use a signed baseline or omit the misleading profit bars; add a visible legend/text summary and full VND values in the table. Keep compact formatting only on the visual axis.

4. **Recovery actions leave the current task**
   - `overview-dashboard.tsx:32-34,58-60,85-87`, `farm-list.tsx:27-32`, and `farm-detail.tsx:38-40,63-65` call the generic `StatePanel` without a route-specific action.
   - The shared default action is `/protected`, so “Thử lại” navigates away instead of retrying/resetting Overview/Farms. Empty Gold states also inherit copy that suggests “tạo bản ghi đầu tiên,” which is wrong for read-only analytics.
   - Provide source- and route-specific recovery: reload same URL, reset filters, return to Farms, or open Data Quality. Do not offer create language where no mutation exists.

5. **Loading/error boundaries remove the product shell**
   - `web/src/app/(platform)/overview/loading.tsx:5-7` and `error.tsx:16-21` render a standalone foundation `<main>`, so navigation, identity, scope, and layout disappear during transitions/failures.
   - Keep stable shell geometry and skeletons. Error copy asks for a correlation code but displays none (`error.tsx:19`).
   - Farms has no route-local loading/error equivalent, producing inconsistent recovery.

## High-priority UX findings

### Vietnamese copy

- `overview-dashboard.tsx:25-29,40-43`
  - “Điểm cần quyết định hôm nay” is unsafe when lineage is stale; use “Điểm cần xem xét” or condition the wording on freshness.
  - Remove developer assertion “Không tính lại ở trình duyệt.”
  - Replace vague `Mở nông trại` with `Xem hiệu quả nông trại`.
- `farm-list.tsx:21-24`
  - Replace `Danh mục nghiệp vụ + Gold` and UUID/Spring implementation prose with user language: scope, cutoff, and what decision the page supports.
- `farm-detail.tsx:32-35,45-54`
  - Remove “KPI từ Gold ghép theo mã chuẩn,” “Hồ sơ nghiệp vụ,” “Định danh đã xác minh,” and “Hiệu quả Gold” from primary hierarchy.
  - Keep UUID/version/code in a compact “Thông tin kỹ thuật” disclosure; lead with farm status and performance period.
- `lineage-banner.tsx:29-38`
  - Prefer Vietnamese labels: `Phiên dữ liệu`, `Phiên bản hợp đồng`, `Dấu vân tay dữ liệu`. Technical values remain `translate="no"`.
- `overview-dashboard.tsx:68`
  - Map backend `riskType`/`status` enums to Vietnamese labels. Raw service strings must not leak into the interface.

### Filters and drill paths

- `farm-list.tsx:69-92` has useful visible labels and GET/URL state. Add an explicit reset link and filter-result summary.
- `overview-dashboard.tsx` exposes no Overview filters, so URL `search/status/sort` can change the farm count while the enterprise KPIs remain tenant-wide. This creates ambiguous scope. Show the active filter summary or restrict those keys to Farms.
- `farm-list.tsx:64-66` and detail `backHref` preserve query state—keep this behavior.
- `load-farm-intelligence-view-model.ts:78-88` merges analytics into Spring order, so `sort=profit_desc` does not visibly sort the rendered list. Either reorder after the safe join or remove the option.
- Inactive farms remain linked at `farm-list.tsx:45-49`, but detail resolution rejects inactive farms (`resolve-analytics-codes.ts:30-35`). Render inactive rows as non-drillable with explanatory text, or provide an approved read-only detail contract.
- Detail loading ignores all filters except `farmId` (`load-farm-intelligence-view-model.ts:92-118`), so preserved season/date/crop query state does not affect the KPI request. Do not display filters that the detail view cannot honor.

### Partial, empty, and failure states

- Overview renders duplicate generic banners for one source failure (`overview-dashboard.tsx:32-34` plus `:85-87`). Consolidate into one source-specific degraded notice.
- If Gold fails, keep Spring farm context and say exactly which analytics are unavailable. If Spring fails, keep tenant-wide analytics but avoid implying verified farm coverage.
- Empty risk should say “Không có cảnh báo trong phạm vi/cutoff này,” not a generic empty-record message.
- Farm list empty must distinguish empty catalog vs no filter matches; provide reset or authorized provisioning route.
- Farm detail with no analytic row must say the farm master is available but no Gold record exists for the selected period/cutoff.
- Stale/partial freshness from the envelope needs persistent treatment beyond the word “Đã cũ” in a metadata cell.

## Responsive and accessibility review

- Good:
  - CSS uses tokens, asymmetric grids, 44 px form controls, one-column fallbacks at 960/704 px, and no decorative motion.
  - Tables have captions; scroll regions are keyboard-focusable.
  - Images use canonical catalog metadata, descriptive alt, explicit dimensions, and a visible “không mang giá trị KPI” boundary.
  - Main data views remain server components; number/date formatting uses `Intl`.
- Fix:
  - `overview-farms.module.css:119-143` uses a 12-column chart with 10.4 px month labels. Increase legibility and reduce tick density on small screens.
  - `:197-208`, `:251-256`, and `lineage-banner.module.css:18-30` use 11–12.5 px labels/technical text. Raise critical/table/metadata labels toward 14 px and test Vietnamese at 200% zoom.
  - `farm-list.tsx:38-57` keeps six nowrap columns at mobile; provide priority-row/card fallback or a clearly signposted scroll mode. The inline farm link itself is smaller than the 44 px touch target.
  - Up to 100 rows render without paging/virtualization (`load-operational-farms.ts:39-50`, `farm-list.tsx:45`). Add bounded pagination; do not fail the entire page when `hasMore`.
  - `overview-farms.module.css:29-39,267-279` defines no hover/active feedback for links/buttons. Ensure visible hover, pressed, and focus-visible states.
  - Select controls rely on shared CSS that does not include `select` in the focus-visible/font inheritance selectors; verify visible keyboard focus and Be Vietnam Pro rendering.
  - Search should use `autoComplete="off"` and `spellCheck={false}` for names/codes; placeholder can be `Ví dụ: An Phú…`.
  - The chart is `aria-hidden`, which is valid only if the adjacent table remains exact and complete. Add a nearby plain-language insight so screen-reader users need not scan every cell.

## Provenance and image boundary

- Pass: both pages use `VISUAL_CATALOG_BY_AREA`, `next/image`, catalog alt/dimensions, and explicit contextual-not-KPI captions.
- `overview-dashboard.tsx:78` marks the contextual image `priority` even though it follows KPI/trend content. Let it lazy-load unless measurements prove it is the LCP element.
- Consider wording `Ảnh minh họa bối cảnh — không phải bằng chứng số liệu` for clearer Vietnamese. Never turn the contextual image into farm evidence or derive status from it.

## Anti-slop gate

- Pass: no purple gradient, centered marketing hero, emoji navigation, dead `href="#"`, stock-photo collage, or client KPI aggregation.
- Risk: the UI speaks like an architecture diagram (`Spring`, `Gold`, `UUID`, `Contract`, `Fingerprint`) instead of an agricultural operations product. Keep technical lineage available, but make decision, scope, period, cutoff, and next action the first scan.
- Risk: the overview is drifting toward a generic KPI band + chart + table dashboard. Restore the Field Ledger signature with value-versus-target rails, evidence age, and an explicit highest-impact drill path rather than decorative card density.

## Acceptance retest

- [ ] Farm/field/crop/season/date filters resolve and round-trip, or unsupported keys receive specific recovery.
- [ ] Every KPI/chart/table shows scope, period/unit, cutoff/freshness, and safe lineage.
- [ ] Financial chart preserves negative direction; table exposes exact values.
- [ ] Empty/partial/failed states remain in shell and retry/reset the current route.
- [ ] Profit sort works; inactive rows do not lead to a guaranteed 404.
- [ ] 375/768/1024/1440 + landscape, 200% zoom, keyboard, reduced motion, and no horizontal page overflow pass.
- [ ] Farm mobile rows expose a 44 px drill target; 50+ rows are paged/virtualized.
- [ ] Context images remain catalog-backed, lazy where appropriate, and explicitly non-evidentiary.
- [ ] Copy contains no implementation-facing Spring/Gold/browser assertions in the primary hierarchy.

## Unresolved questions

- Should inactive farms have a read-only detail page or remain non-drillable?
- Which Overview filters are contractually tenant-wide versus intended to change KPI scope?
- Should full lineage identifiers remain always visible or move behind a compact details disclosure while freshness/scope stay visible?

Status: DONE_WITH_CONCERNS
Summary: Safe server/data boundaries are strong, but the current Overview/Farms UI misses required filter support, panel scope, accurate chart parity, route-local recovery, and mobile farm-list behavior.
Concerns/Blockers: Resolve unsupported filter semantics, exact panel context, chart loss-direction/parity, `/protected` recovery links, inactive-farm drills, and 100-row mobile rendering before Phase 5 UI acceptance.
