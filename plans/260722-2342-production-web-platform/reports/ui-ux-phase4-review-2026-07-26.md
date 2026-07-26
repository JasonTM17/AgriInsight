# Phase 4 UI/UX review — Field Ledger shell

Date: 2026-07-26  
Scope: Phase 4 shell plan, Field Ledger design source of truth, Crop Health/Data Quality/Administration Stitch evidence, existing `web` entry shell.

## Review conclusion

Proceed with a shared Field Ledger shell, but treat the three Stitch screens as composition evidence only. The implementation must normalize the inconsistent labels, remove generated/demo artifacts, and make scope/freshness/authorization server-owned. The current web entry is an auth-foundation landing page, not yet a product shell.

## Shell hierarchy to freeze

### Desktop (1024/1440)

1. **Persistent labeled navigation rail (240–272 px)**
   - Primary areas, in one canonical order:
     `Tổng quan`, `Nông trại`, `Công việc`, `Tồn kho`, `Chi phí`, `Sức khỏe cây trồng`, `Chất lượng dữ liệu`, `Quản trị`.
   - Keep utility actions (help, profile, sign out) outside the eight-area IA; do not add `Cài đặt` as a ninth product area without a contract.
   - Each item is a real link, has icon + visible label, and exposes active state with text/icon/border—not color alone.
2. **Global header**
   - Brand lockup (AgriInsight, `translate="no"`), current organization/tenant context, scoped search only when the route contract supports it, notification/profile controls.
   - Never hard-code a role label such as “Quản trị viên”; resolve identity and scope from `/api/v1/me`.
3. **Scope/freshness strip**
   - Farm/field/season scope where applicable; run ID/cutoff for Data Quality; always show synchronized cutoff/freshness.
   - Do not claim “thời gian thực” unless a versioned contract proves it.
4. **Route content**
   - One `h1`, concise subtitle, one primary action, then the asymmetric evidence/work surface.
   - Standard state panels (loading, empty, stale, partial, denied, offline, conflict, failed) must name the next safe action.
5. **Contextual evidence**
   - Tables/charts/images remain subordinate to the decision and retain source, unit, date granularity, scope, and provenance.

### Mobile (375/landscape; tablet at 768)

- One-column content; rail becomes a labeled drawer opened by a 44–48 px menu button. While open, trap focus, support Escape, mark the workspace inert, and return focus to the trigger.
- Keep current page + scope/cutoff in the top bar; move secondary filters/search into a labeled sheet. Do not rely on hover or horizontal swipe.
- Crop-health observations, quality issues, and admin directory rows become priority rows/cards with an explicit “Xem chi tiết”/expand control. Wide audit columns may use a deliberate scroll wrapper with visible affordance; never page-level horizontal overflow.
- Use `min-height: 100dvh`, safe-area insets, 4/8 px spacing rhythm, and reserve space for fixed bars.
- At 200% zoom and large text, Vietnamese labels wrap; no critical action may disappear or truncate without an accessible full value.

## Vietnamese-first copy decisions

Centralize labels and recovery text in the Phase 4 catalog. Use sentence case; avoid all-caps microcopy except compact table headers where it remains readable.

| Evidence / issue | Production direction |
|---|---|
| Rail alternates `Nông trại` / `Trang trại`, `Tồn kho` / `Kho hàng` | Pick one canonical nav label (`Nông trại`, `Tồn kho`) and use “trang trại/kho” only in prose. Snapshot-test the catalog. |
| Crop Health heading `Giám sát Sức khỏe Cây trồng` | `Sức khỏe cây trồng` (or `Theo dõi sức khỏe cây trồng` if a verb is needed); sentence case. |
| Crop Health subtitle claims real-time analysis | Say `Dữ liệu đồng bộ đến [timestamp]`; show age/stale state beside it. |
| Crop scope shows `Vụ cà phê 2026` while rows are rice / `Vụ Hè Thu 2026` | Scope comes from server route context; never copy Stitch fixture values into runtime. |
| Crop evidence caption | Preserve exact boundary: `Ảnh minh họa do AI tạo — chỉ dùng cho demo`. Pair with alt text and a non-image evidence state. |
| Data Quality title wraps as `Chất lượng dữ liệu hệ thống` | Use concise `Chất lượng dữ liệu`; put run ID, contract version, cutoff in a separate context line. |
| Data Quality green aggregate | Keep `Tươi`, `Đầy đủ`, `Hợp lệ`, `Duy nhất` as labeled measures, but never let a green aggregate hide quarantine, drift, mismatch, or stale state. |
| Data Quality “Cấu hình quy tắc” / “Mở phiên đối soát” | Keep action labels specific; expose owner, reason, count, recovery path, and correlation/run ID from the API. |
| Admin has both `Thêm người dùng` and `Mời thành viên` | One primary action only: `Mời thành viên`; remove/demote the duplicate. |
| Admin pending examples use public-looking emails | Use non-personal demo identities in fixtures; never expose secrets, raw claims, or another tenant’s identity. |
| Admin role/scope changes | Copy must name last-admin protection, stale-version conflict, and server denial with a recovery path (retry/reload/cancel), not provider diagnostics. |
| Existing home page | Replace security implementation prose and `Kiểm tra vùng bảo vệ` test CTA with shell entry behavior. Keep auth/security details in docs/status, not the product landing view. |
| Existing header | Replace English `Secure web foundation` with a Vietnamese product descriptor (for example `Hệ thống vận hành`). Keep technical identifiers (brand, run IDs, hashes) `translate="no"`. |

## Accessibility and interaction gates

- Preserve the existing skip link, then add semantic `header`, `nav`, `main`, optional `aside`, and an explicit nav label. Use sequential headings and `aria-current="page"` for the active route.
- Use `<a>/<Link>` for navigation and `<button>` for actions. Icon-only controls require an accessible name; decorative icons are `aria-hidden`.
- Focus-visible rings must meet the Field Ledger contrast tokens; dialogs/sheets trap focus, close on Escape, and restore focus to the invoking control.
- Status, risk, variance, disabled, stale, and permission states use icon + text + pattern/shape. Never rely on green/red or color-only badges.
- Tables need captions, semantic headers, keyboard-sortable controls, and a nearby summary/table alternative for every chart. Hashes/run IDs must remain fully available for copy/screen readers.
- Form labels, helper text, field-level errors, `aria-live="polite"` async updates, and first-invalid focus are required for invite, filters, assignments, and recovery actions.
- Use `Intl.DateTimeFormat`/`Intl.NumberFormat` for Vietnamese locale; display UTC detail where audit precision matters.
- Images need descriptive alt, explicit dimensions/aspect ratio, lazy loading below fold, and canonical catalog provenance. Crop-health AI evidence remains visibly non-production evidence.
- No browser token storage, client KPI recomputation, or client-only authorization decisions. Hidden nav is advisory; deep links must still render server-authoritative denied/404 states.

## Motion and loading

- Honor the master target of motion intensity 3/10: 150–250 ms transform/opacity transitions, ease-out entry/ease-in exit, no perpetual or decorative motion.
- Never transition `all`, animate width/height, or create layout shift. Drawer/disclosure motion must be interruptible and disabled/reduced under `prefers-reduced-motion`.
- Reserve geometry for KPI rows, lineage cards, observation ledgers, and directory rows. Use stable skeletons after ~300 ms; never flash zero values or an empty tenant.
- Avoid animated map noise, chart entrance theatrics, parallax, or “AI glow”; motion should explain navigation, disclosure, or state change.

## Anti-slop / evidence risks

- Reject centered hero, purple/blue gradients, equal three-card walls, giant unbounded tables, stock-photo collages, emoji/system icons, generic English placeholder data, and dead `href="#"` links.
- The Stitch screenshots have inconsistent rail/header branding and labels, a stray `PIXEL_4_4XL` artifact in Administration, duplicate invite CTAs, illustrative numbers, and a Crop Health coffee/rice scope mismatch. These are evidence defects, not runtime content.
- Do not ship Stitch HTML, public CDN font/icon links, direct exported PNGs, or inline fixture values. Bundle the approved Be Vietnam Pro/Noto Sans stack and use the canonical eight-entry asset catalog with hash/provenance checks.
- Keep topographic/dotted texture sparse and behind content only if contrast remains clear; it cannot carry KPI meaning.
- Search fields must be route-scoped and contract-backed; do not imply a global index that does not exist.
- Keep all later route phases within the frozen eight-area IA. New domain actions belong inside routes, not new rail items.

## Current web entry audit

- `web/src/app/layout.tsx:25-35` already has a useful skip link and `lang="vi"`, but only a generic site header; it lacks the labeled nav rail, scope/cutoff context, route chrome, and user/session controls required by Phase 4.
- `web/src/app/layout.tsx:33` exposes English `Secure web foundation`; this violates Vietnamese-first shell copy.
- `web/src/app/page.tsx:4-16` is security-foundation copy and a test route (`Kiểm tra vùng bảo vệ`), not a safe Field Ledger landing/redirect. Replace with server-aware shell entry behavior (authorized users to their permitted overview; unauthenticated users to login).
- `web/src/app/globals.css` currently uses ad-hoc Segoe UI/Georgia and a mint gradient; map typography, colors, spacing, focus, and motion to `MASTER.md` tokens instead of extending these one-off values.
- `web/src/proxy.ts:8-18` lists the intended protected prefixes, which is a useful route boundary, but it must not become the navigation permission source. Navigation visibility still derives from fresh Spring authorization context; guessed/deep-linked routes need generic server denial without foreign metadata.

## Acceptance checklist for implementation review

- [ ] Eight-area nav catalog has one canonical Vietnamese label/order; utilities are separate.
- [ ] Desktop rail, header, scope/freshness strip, and route slot pass 375/768/1024/1440 + landscape with no page overflow.
- [ ] Mobile drawer focus/inert/Escape/return-focus behavior is tested.
- [ ] Every route has one `h1`, one primary action, URL-owned filters/selection, and recoverable state panels.
- [ ] Crop/Data/Admin copy fixes above are represented in snapshots and no generated fixture values leak into runtime.
- [ ] WCAG 2.2 AA contrast, keyboard, semantics, chart/table alternatives, 200% zoom, reduced motion, and screen-reader labels pass.
- [ ] Asset sync rejects missing/oversized/hash-mismatched/provenance-missing images and preserves the Crop Health demo-evidence flag.
- [ ] No raw Stitch export, CDN-only dependency, public asset URL, browser token, or client-side permission/KPI logic ships.

## Unresolved questions

- Whether launch is Vietnamese-only or bilingual; choose before adding any alternate copy path.
- Whether `Tồn kho` or `Kho hàng` is the approved business term; do not let screens diverge.
- Exact product descriptor for the header lockup (`Hệ thống vận hành` vs another approved brand phrase).

Status: DONE_WITH_CONCERNS
Summary: Shell direction is clear and evidence is usable, but current entry files are only an auth foundation and the Stitch screens contain scope/copy/artifact inconsistencies that must be normalized before implementation is accepted.
Concerns/Blockers: Resolve canonical inventory/farm labels and launch language; ensure Phase 4 snapshots enforce server-owned scope, Vietnamese copy, and the accessibility/state gates above.
