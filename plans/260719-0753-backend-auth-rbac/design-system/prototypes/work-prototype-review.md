# Work activity prototype review

Status: mobile-first design prototype approved for backend-contract handoff; not production frontend code.

## Artifacts

- [`work-activity-prototype.html`](./work-activity-prototype.html)
- [`work-activity-components.css`](./work-activity-components.css)
- [`work-activity-responsive.css`](./work-activity-responsive.css)
- [`work-activity-prototype.js`](./work-activity-prototype.js)
- Shared shell foundation: [`overview-field-ledger-foundation.css`](./overview-field-ledger-foundation.css)

The prototype has no third-party runtime dependency, external image, API call, token, local-storage draft, or production mutation. It labels itself as a design scenario and never claims that an activity was sent or synchronized.

## Data provenance and fixture boundary

| Prototype evidence | Verified source |
|---|---|
| `FIELD-0001`, Khu vực 1.1, Cà phê, sensor age, moisture, and recommended sensor check | `artifacts/gold/field_health_status.csv` |
| `FIELD-0012`, Khu vực 3.4, Cà phê, pest affected area, and field-verification recommendation | `artifacts/gold/field_health_status.csv` |
| `FIELD-0002`, Khu vực 1.2, Sầu riêng, latest moisture and verification context | `artifacts/gold/field_health_status.csv` |

Assignment times, worker identity, shift, completion state, form result, and evidence-upload interaction are explicit UX scenarios because the Phase 4 operational API does not exist yet. They are not presented as Gold facts and must not be copied into production seed or business logic.

## CK FE checks

| Check | Evidence | Result |
|---|---|---|
| Field Ledger direction | Operational queue/detail split, source-led task evidence, modest surfaces, restrained harvest accent | PASS |
| Anti-slop | No centered hero, purple gradient, equal feature-card row, emoji icon, stock image, neon glow, or decorative motion | PASS |
| Responsive | Browser renders at 375, 768, 1024, and 1440 px with `scrollWidth === innerWidth` | PASS |
| Mobile work model | At 375 px, queue and detail are mutually exclusive; opening a task focuses the back control and returning restores the selected row | PASS |
| Touch targets | Zero visible controls below 44 × 44 px; the visually hidden file input is activated by a 76 px labeled upload surface | PASS |
| Form boundary | Visible labels, units, 24-hour time contract, required validation, first-invalid-field focus, retained entry during review | PASS |
| Draft protection | Switching tasks with entered evidence opens an explicit stay/discard decision; evidence is never silently rebound to another task | PASS |
| Tabs | `tablist`/`tab`/`tabpanel`, single roving tab stop, Arrow Left/Right, Home, and End navigation | PASS |
| Navigation/dialog | Closed mobile rail is inert/hidden; open rail isolates workspace; Escape and explicit close restore focus; scope dialog fields are labeled | PASS |
| Protected states | `?state=offline`, `?state=conflict`, and `?state=revoked` expose distinct safe behavior without a false success claim | PASS |
| Color access | Tested semantic pairs range from 5.02:1 to 15.12:1; state meaning also uses text and structure | PASS |
| Reduced motion | Reduced-motion media changes transitions to `0.01ms`; no perpetual animation | PASS |
| Runtime health | JavaScript syntax check passes; browser console and page error logs are empty | PASS |

## Interaction and state evidence

- Required validation keeps the Evidence tab selected and focuses `result` before review.
- Switching tasks after entry preserves the current task and draft until the user explicitly chooses to discard; discard resets the form before binding the next task.
- A completed review formats the measured value with Vietnamese decimal punctuation and preserves the explicit unit and UTC+7 date context.
- Offline state changes the final action to “Lưu vào hàng chờ”; it does not claim server synchronization.
- Conflict state keeps the entry, shows current versus server version, and changes the action to “Đối chiếu rồi gửi”.
- Revoked state disables the entry fieldset and submit action while retaining the safe-draft explanation.
- The review screen explains append-only correction lineage instead of offering destructive edit/delete.

## Known entry gates

- Backend Phase 4 must freeze assignment, activity, evidence, optimistic-version, idempotency, append-only correction, authorization, and audit contracts before production implementation.
- Product/security must approve whether offline drafts may contain notes or images, how they are encrypted, retained, revoked, and purged. This prototype intentionally stores nothing.
- Evidence MIME types, size limits, malware scanning, image metadata handling, geolocation policy, and upload progress/error contracts require backend and security approval.
- Real empty, denied, save-failure, correction, and queued-sync fixtures remain required before production React acceptance.
- Playwright visual baselines, screen-reader runs, real-device virtual keyboard/safe-area checks, and Core Web Vitals belong to the production frontend milestone.
- Temporary review screenshots live only under ignored `artifacts/_tmp/ui-review`; they are not release assets or Docker images.

## Unresolved questions

- May an offline draft contain evidence images, or text fields only?
- Is geolocation required, optional with consent, or prohibited for activity evidence?
- Which role may resolve a conflict or submit an append-only correction?
- What evidence retention and upload-size policy applies per tenant?
