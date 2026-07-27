# Phase 11 Hosted Browser Gate

Date: 2026-07-27

Status: accepted

Commit: `ac09db8`

GitHub Actions: `30267362838`

## Scope

- Real Keycloak authorization-code/PKCE login.
- PostgreSQL web sessions, Spring identity/authorization, FastAPI analytics,
  and verified Big Data artifacts.
- Seven personas across the eight permission-driven product areas.
- Responsive geometry, axe accessibility, lab Web Vitals, security boundaries,
  and owned-runtime cleanup.

## Static evidence

| Gate | Result |
|---|---|
| Python analytics | 202 passed |
| Java backend | 463 unit/contract + 100 PostgreSQL integration passed |
| Next web | 308 passed, 11 intentional skips |
| Web database privileges | 9 passed |
| Contract drift, typecheck, lint, build | PASS |
| Production dependency audit | 0 vulnerabilities |
| Filesystem dependency/configuration/secret scan | 0 findings |

## Browser evidence

- Core route group: 16/16 passed.
- Authorization and Work group: 10/10 passed.
- Exact personas: Tenant Admin, Executive, Data Analyst, Farm Manager,
  Inventory Manager, Field Worker, and denied Supplier.
- Responsive/axe routes cover `375x812`, `768x1024`, `1024x768`,
  `1440x900`, and `844x390`.
- Big Data manifest proves profile `big-data`, quality `passed`, and
  1,050,000 Silver sensor facts.
- Each representative viewport enforces LCP `<= 2.5s`, INP `<= 200ms`, and
  CLS `<= 0.10`; Overview, Crop Health, and Data Quality also enforce bounded
  render time.
- Cache, CSRF, opaque-cookie, bearer-token, storage, mutation, CSP, and
  forbidden deep-link probes pass.
- Runtime teardown completes before
  `WEB_PLATFORM_E2E=PASS issuer=keycloak identity=spring-/me
  session=postgres browser=chrome`.

## Disk policy

Local C remained below the 8 GiB heavy-work floor, so the workstation gate was
not forced. Hosted CI used guarded ephemeral runner storage and completed with
more than 80 GiB free. Local npm, Playwright, Maven, and recovery paths stay on
ignored D storage.

## Review

No Critical or High browser, authorization, data-scope, accessibility, or
performance defect remains in Phase 11. Production RUM p75 is explicitly a
post-deployment follow-up, not hidden acceptance evidence.

## Unresolved questions

- None inside Phase 11. Protected external release controls remain in Phase 12.
