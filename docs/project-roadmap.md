# Project Roadmap

This roadmap reflects the current state of the repository, not an assumed release plan.

## Now

| Track | Status | Notes |
|---|---|---|
| Analytics MVP | Active and regression-verified | Bronze/Silver/Gold pipeline, reporting, dashboard |
| Backend phase 1 | In progress | Foundation scaffold exists, but acceptance is blocked by integration gates |

## Next backend phases

| Phase | Goal | Dependency |
|---|---|---|
| Phase 1 | Backend foundation and contracts | Current work |
| Phase 2 | OIDC identity and security boundary | Phase 1 accepted |
| Phase 3 | Tenant RBAC and PostgreSQL RLS | Phases 1-2 accepted |
| Phase 4 | Farm, season, workforce, and activity APIs | Phase 3 accepted |
| Phase 5 | Inventory and procurement APIs | Phase 3 accepted |
| Phase 6 | Cost management and reporting boundary | Phases 4-5 accepted |
| Phase 7 | Outbox operations and release hardening | Phases 4-6 accepted |

## Follow-on frontend

The frontend follow-up brief, persisted CK FE master/page overrides, and reviewed Overview, Farms, and Work prototypes are ready for detailed planning. Inventory now has a Gold-backed source prototype with static gates complete; browser/responsive approval remains pending while the C-drive disk guard is below PASS. Production implementation remains queued until backend phases 1-3 stabilize the auth and OpenAPI boundary. See [design guidelines](./design-guidelines.md).

## Deferred until later

- Docker Hub namespace and image publication details
- Backend CI enforcement
- Image verification and SBOM/provenance checks
- Public web application deployment
- Any claim that auth/RBAC is already live

## Roadmap rule

When status changes, update the roadmap and the relevant phase plan together. Do not mark a phase complete in docs if the integration gates still say otherwise.
