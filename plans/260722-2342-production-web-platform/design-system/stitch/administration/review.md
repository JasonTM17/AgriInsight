# Administration Stitch evidence

Status: accepted as composition evidence; rejected as production code.

## Source

- Stitch project: `9084754434575632570` (`AgriInsight/backend-auth-rbac`)
- Field Ledger design system: `assets/c1989dfbbef24da0a3d2617a620edb8a`, version `1`
- Screen: `4b940458cf764e8bac8822f7b82dd5ec`
- Title: `Quản trị hệ thống - AgriInsight`
- Canvas: `2560 × 2048`
- Evidence image: `design.png`
- Evidence SHA-256: `0295ac9393389784b09433138d18b842a37dbe8bb112419f408bf96823c5e056`
- Generation session: `6871169623525772612`
- Review edit session: `6871169623525772612`

## Accepted direction

- Organization context, member table, pending requests, role matrix, farm
  assignments, and recent immutable audit entries are visually discoverable.
- The single safe task is clear: invite a member while keeping scope visible.
- Active, disabled, and pending states combine icon and text.
- The layout is dense on desktop and has a deliberate table-to-card fallback
  point for mobile implementation.

## Implementation blockers

- The export retains a stray `PIXEL_4_4XL` text artifact in the rail.
- It exposes both `Thêm người dùng` and `Mời thành viên`; production must have
  one primary action and demote or remove the duplicate.
- Generated pending-request examples use public-looking email domains; runtime
  fixtures must use non-personal demo identities and never expose secrets.
- Last-admin protection, version conflicts, and server-authoritative
  authorization need explicit state copy and action recovery in React.

## Handoff rules

Rebuild from the backend authorization contract. Navigation visibility is
advisory only. Do not ship Stitch HTML, placeholder emails, raw exports, or
client-side permission decisions.
