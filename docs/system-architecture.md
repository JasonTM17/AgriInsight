# System Architecture

AgriInsight is split into two planes.

## Contents

- [Analytics plane](#analytics-plane) - current Bronze/Silver/Gold pipeline, artifacts, and dashboard.
- [Operational backend](#operational-backend) - separate Java Spring boundary for operational state.
- [Boundaries](#boundaries) - what each plane owns and what it must not touch.
- [Current status](#current-status) - what is verified today and what is still blocked.

## Analytics plane

The analytics plane is the current validated MVP.

- Python pipeline generates Bronze, Silver, quarantine, warehouse, Gold, and manifest artifacts.
- Streamlit reads Gold contracts and renders operational views for the analytics MVP.
- Reporting is derived from normalized Gold inputs and stays local/internal.

```mermaid
flowchart LR
    A["Source data"] --> B["Bronze"]
    B --> C["Validation and quarantine"]
    C --> D["Silver"]
    D --> E["Warehouse and Gold"]
    E --> F["Dashboard and reports"]
```

## Operational backend

The backend is a separate Java 21 Spring Boot project under `backend/`.

Foundation currently present in source:

- application bootstrap
- deny-by-default security
- correlation IDs
- problem-detail responses
- liveness/readiness split
- Flyway tenant anchor migration

The backend currently owns operational identity scaffolding, not business CRUD.

## Boundaries

| Plane | Owns | Does not own |
|---|---|---|
| Analytics | artifacts, Gold contracts, local reporting, dashboard views | PostgreSQL operational state, auth/RBAC, backend images |
| Backend | operational API boundary, health, tenant anchor, schema history | `artifacts/`, Gold CSVs, SQLite warehouse, report generation |

## Current status

| Area | Status |
|---|---|
| Analytics MVP | Verified by its existing regression suite |
| Backend phase 1 scaffold | In progress |
| Backend auth/RBAC | Not yet implemented |
| Docker Hub publication | Not yet claimed |
| Image verification | Not yet verified |

The right way to read the repo is: analytics is live today, backend is being added as a separate operational boundary. Do not collapse the two stories in docs or status reports.
