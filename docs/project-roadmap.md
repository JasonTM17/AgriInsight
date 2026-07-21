# Project Roadmap

This roadmap reflects the current state of the repository, not an assumed release plan.

## Now

| Track | Status | Notes |
|---|---|---|
| Analytics MVP | Active and regression-verified | Bronze/Silver/Gold pipeline, reporting, dashboard |
| Backend phase 1 | Completed and regression-verified | Java 21 foundation, PostgreSQL/Flyway, probes, non-root image smoke |
| Backend phase 2 | Completed and regression-verified | OIDC identity/security boundary, exact bootstrap, route inventory, local image smoke |
| Backend phase 3 | Completed and regression-verified | Restricted roles, tenant RBAC/context, provisioning, FORCE RLS, idempotency, tenant administration |
| Backend phase 4 | In progress | Farm slice verified; field, crop, season, workforce, activity, log, harvest, and assignment boundaries remain |

## Next backend phases

| Phase | Goal | Dependency |
|---|---|---|
| Phase 1 | Backend foundation and contracts | Accepted 2026-07-19 |
| Phase 2 | OIDC identity and security boundary | Accepted 2026-07-20 |
| Phase 3 | Tenant RBAC and PostgreSQL RLS | Accepted 2026-07-20 |
| Phase 4 | Farm, season, workforce, and activity APIs | In progress; farm slice verified |
| Phase 5 | Inventory and procurement APIs | Dependency unblocked; sequentially follows Phase 4 |
| Phase 6 | Cost management and reporting boundary | Phases 4-5 accepted |
| Phase 7 | Outbox operations and release hardening | Phases 4-6 accepted |

## Follow-on frontend

The frontend follow-up brief, persisted CK FE master/page overrides, and reviewed Overview, Farms, Work, Cost Analysis, and Inventory prototypes are ready for detailed planning. Backend phases 1-3 now stabilize the identity, tenant authorization, and tenant-administration OpenAPI boundary, so the CK FE planning/design track may proceed. Production screens that depend on farms, work, inventory, or cost mutations still wait for their Phase 4-6 APIs. See [design guidelines](./design-guidelines.md).

## Deferred until later

- Docker Hub namespace and image publication details
- Backend CI enforcement
- Registry digest verification, image scanning, and SBOM/provenance checks
- Public web application deployment
- Any claim that Phase 3 alone makes the full product production-ready

## Roadmap rule

When status changes, update the roadmap and the relevant phase plan together. Do not mark a phase complete in docs if the integration gates still say otherwise.
