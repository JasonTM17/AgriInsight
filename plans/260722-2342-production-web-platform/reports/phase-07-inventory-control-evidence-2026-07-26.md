# Phase 7 Evidence — inventory-control (2026-07-26)

Scope: warehouse-scoped `/inventory` browser surface over the frozen Spring
inventory ledger plus the read-only FastAPI Gold inventory envelope. Web-only
phase: no Java file changed (git-verified below).

## Commit series

| Commit | Subject |
| --- | --- |
| 6438d88 | feat(web): allowlist inventory operations |
| e1411b1 | fix(analytics): require supplier reconciliation |
| ecec052 | feat(web): add scoped inventory read model |
| 299a633 | feat(web): secure inventory ledger mutations |
| 6034dc0 | test(web): expose inventory persona and tidy receipt rejection case |
| 9b8b21a | feat(web): render warehouse-scoped inventory control |
| b2c8102 | feat(web): activate inventory navigation |
| 993445a | fix(web): defer expiry ordering to the frozen inventory contract |
| 2103399 | test(web): cover inventory read boundaries and ledger journeys |
| 1ba430c | docs(review): record phase 7 server slice verdict |
| 82d6b48 | fix(web): treat blank inventory filter fields as absent |
| 3005399 | fix(web): retain inventory idempotency key on rejected mutations |
| e7fdfcb | fix(web): disclose inventory paging and analytics limits |
| 9e5e5b9 | fix(web): give inventory form controls visible focus rings |
| ef0981d | refactor(web): drop unused inventory preparation error flag |
| 27fddb3 | docs(review): record phase 7 UI slice verdict |

Two sessions implemented this phase concurrently against the same tree. The
server-slice work converged on identical code; all commits were verified for
authorship and absence of AI trailers before being kept.

## Measured gates

Run on the final tree (all remediation applied).

| Gate | Command | Result |
| --- | --- | --- |
| Typecheck | `npm --prefix web run typecheck` | exit 0 |
| Lint | `npm --prefix web run lint` (`--max-warnings=0`) | exit 0 |
| Generated contract drift | `npm --prefix web run contracts:check` | clean |
| Unit/contract suite | `npm --prefix web run test` | 30 files passed / 1 skipped (31); **209 passed / 9 skipped (218)** |
| Focused inventory | `npm --prefix web run test -- inventory` | 3 files / **78 tests** passed |
| Production build | `npm --prefix web run build` | `✓ Compiled successfully in 8.1s`; emits `/inventory` plus `/api/inventory/transactions`, `/api/inventory/transactions/[transactionId]`, `/api/inventory/transactions/[transactionId]/reversals` |
| Python (changed surface) | `python -m pytest tests -k "reconcil or demo"` | 18 passed |
| Java | not re-run | `git diff --name-only 6438d88~1..HEAD -- backend` → **none**; Phase 5's accepted backend gate remains authoritative |

## Server-truth verification

- No `sort` parameter exists anywhere in the feature (grep clean). Balances keep
  `warehouse.code, material.code, id`; lots keep `expiry_date, received_at, id`
  (server FEFO); transactions keep `occurred_at DESC, id DESC`.
- No ABC class, FEFO priority, alert severity, days-of-supply or low-stock
  threshold is recomputed in the browser; every one renders verbatim from its
  source envelope.
- Paging discloses `hasMore` and stops at the backend 10,000 offset ceiling
  instead of implying a total; the analytics panels label the ABC top-8 cut and
  say when the Gold snapshot holds more rows.
- Warehouse scope: the route redirects to the first server-visible warehouse and
  fails closed on `foreign_warehouse` / `foreign_material`, including when the
  catalog fetch itself fails.

## Mutation trust boundary

Both inventory POSTs run the fixed layering: trusted Host (400) → same-origin
(403) → CSRF (403) → session (401) → `Idempotency-Key` (400) → bounded 64 KiB
streamed body (413/415/400) → strict zod → exact upstream operation.

- `Idempotency-Key` is retained whenever the outcome is unknown, so an identical
  retry dedupes upstream instead of appending a second ledger row. A payload
  change still rotates the key.
- Reversals additionally require a strong quoted-integer `If-Match`
  (`/^"\d{1,19}"$/`). The value originates from an authenticated transaction GET
  and the BFF re-reads and compares the source version before forwarding, so a
  stale or guessed version cannot win.
- Supplier-role denial is permission-driven (`INVENTORY_READ` absent), not route
  cosmetics, and the denied response carries no token material.

## Review cycles

Two independent code reviews ran against this phase.

| Review | Verdict | Report |
| --- | --- | --- |
| Server slice | 0 Critical / 0 High / 3 Medium / 6 Low | `code-review-2026-07-26-phase-07-server-slice.md` |
| UI slice | 0 Critical / **2 High** / 3 Medium / 5 Low | `code-review-2026-07-26-phase-07-ui-slice.md` |

The UI slice was initially committed after static gates only. The gap was caught
before acceptance and a dedicated content review was run, which found both High
defects. Both are fixed and covered:

- **H1** — the filter form submits unselected selects and dates as empty strings;
  the route parser rejected `""` against the kind enum and ISO-date checks, so
  any partial filter apply replaced the page with the invalid-link panel. Fixed
  by trimming scalars and treating blanks as omitted (matching the work parser).
  Covered by a contract test feeding the exact browser output.
- **H2** — the mutation hook cleared its fingerprint and key on every non-2xx
  answer, including 502/504 where the ledger write may already have committed.
  A retry then minted a fresh key and bypassed server dedupe. Fixed by keeping
  the key until a parsed success arrives. Covered end to end by injecting a 502
  and asserting the retry reuses the same wire key for exactly one new row.

Medium/Low items fixed in the same pass: offset-ceiling disclosure (M1),
analytics truncation disclosure (M3), `:focus-visible` rules for selects and
textarea (L1), and removal of a dead error field (L3).

### Accepted with note

- Server-review M2 — 409 sanitization posture kept as-is: conflict detail is
  sanitized while the correlation ID stays available for support.
- Server-review remaining 6 Low items.
- UI-review M2 — the If-Match design-record drift is resolved in favour of the
  phase contract, which specifies `Idempotency-Key` **and** `If-Match` on
  reversals. `docs/system-architecture.md` now records the implemented split:
  the client attaches a server-issued ETag and the BFF re-validates it upstream.
  A consequence worth knowing: the backend bumps the source version on reversal,
  so an ambiguous reversal that did commit answers 409 ("reload") rather than
  replaying — safe, and the error copy steers to the right recovery.
- UI-review L2 (loading state hand-rolls its panel instead of the shared
  `StatePanel`), L4 (a permanent analytics denial is framed as transient), L5
  (hand-built URLs may carry an empty `materialId=`, which fails closed).

### Review questions resolved from source

1. **Does the gate reseed per run?** Yes. `scripts/run-web-e2e-tests.ps1:382`
   removes the artifact runtime root — which holds the bind-mounted postgres data
   — before the stack boots, and again during cleanup. The demo bootstrap then
   reseeds. The demo seed creates supplier masters only and leaves the ledger to
   real Spring commands, so `inventory_transactions` starts empty and the
   `toHaveCount(1)` assertion is deterministic.
2. **Does Spring answer a duplicate transaction POST with 2xx or 409?** Verified
   in source, not inferred from docs. `CommandExecutionService.executeInternal`
   (`:78-87`) branches three ways: a freshly claimed key applies the mutation; a
   reused key whose `schemaVersion`/`commandHash` does **not** match the stored
   record returns `conflict(...)` (409); a reused key whose fingerprint *does*
   match falls through to `replay(...)`, which reconstructs the existing resource
   from its stored target once the record is `COMPLETED` (`:148-156`).
   So an identical retry under a retained key replays a 2xx representation and
   appends nothing. This is exactly what makes the H2 fix load-bearing: keeping
   the key reaches server dedupe, while rotating it would have appended a second
   row to an append-only ledger.

## Module layout and refactor track

The phase REFACTOR items are satisfied without further extraction:

- Server-visible warehouse enforcement is shared, not duplicated:
  `scopedOperationalSource` guards all three operational pages and
  `parseScopedInventoryAnalytics` guards the Gold envelope.
- Lot and transaction row formatting is consolidated in `inventory-format.ts`
  and reused by both the operational and analytic panels.
- Every abstraction stays inventory-local; no cross-domain stock framework.

Four files exceed the 200-line modularization prompt:
`inventory-generated-client-adapter.ts` (295),
`load-inventory-view-model.ts` (247), `inventory-transaction-form.tsx` (221),
`inventory-analytics-panels.tsx` (205). Each was reviewed for a real seam and
deliberately left whole. The adapter is seven read operations over one shared
`requestInventoryPayload` core — the same shape as
`work-generated-client-adapter.ts`, larger only because inventory has more
operations; splitting it would fragment a shared private core and diverge from
the sibling. The loader is a single load-and-shape concern, and separating its
type block from its only consumer would add an import hop without reducing
complexity. Recorded here rather than churned immediately before the gate.

## Deviations from the phase file

Recorded before implementation and unchanged since.

- The phase file lists ABC, alerts **and trends** from `/internal/v1/inventory`.
  The frozen analytics contract has no trend time-series, so no trend panel was
  built and the page states this rather than fabricating a series.
- "Mandatory warehouse" is enforced web-side. Spring does not require a
  warehouse filter on its read routes; the browser route makes it explicit and
  server-visible.
- The demo seed adds supplier masters one-to-one from `silver/suppliers.csv`
  (code, display name, active only — no province or rating column exists) and
  seeds no ledger rows; every balance, lot and transaction in E2E comes from real
  Spring commands.
- Adding the reconciliation domain to `_RECONCILED_DOMAINS` was required for the
  supplier gate; without it ten analytics tests answered 503.
- File Matrix: `inventory-filter-schema.ts` is realized as
  `inventory-route-state.ts`, mirroring `work-route-state.ts`. Beyond the matrix
  the route also ships `layout.tsx` and `error.tsx` (per-segment boundary rule),
  three exact BFF route handlers, and the generated-contract schema modules.

## Outstanding

The guarded runtime E2E gate has **not** yet run on this build. The workspace
disk guard hard-fails below 20 GB free on D and the drive sits at 17.7 GB. The
Docker WSL data disk holds 43.8 GB inside a 69.1 GB host file; an in-guest
`fstrim` freed 960 GiB of ranges but the VHDX carries no sparse flag, so the
space needs a one-time offline `compact vdisk`, which requires an elevated shell.
Acceptance for this phase stays open until the gate reports `WEB_PLATFORM_E2E=PASS`
with all 8 Playwright scenarios, including both `@inventory` journeys.

## Unresolved questions

1. Should the legacy `/protected?module=inventory` mapping in
   `getActiveNavigationKey` be retired now that the real route exists? No in-repo
   link remains; external bookmarks would land on the placeholder with correct
   nav highlighting.
