# Authorization matrix

Status: normative planning contract for phases 2-6
Default: deny. A route missing from this file/registry is not reachable. A permission never replaces tenant/domain scope.

## Permission catalog

| Code | Meaning |
|---|---|
| `IDENTITY_USER_MANAGE` | Provision/deactivate tenant profiles and link/unlink external identities. |
| `IDENTITY_ROLE_MANAGE` | Grant/revoke fixed tenant roles while preserving the last-admin invariant. |
| `FARM_READ` | Read farm, field, and crop data within effective farm scope. |
| `FARM_MANAGE` | Create/update/deactivate farm, field, and crop masters within effective farm scope. |
| `FARM_ASSIGNMENT_MANAGE` | Grant/revoke manager farm assignments. |
| `SEASON_READ` | Read seasons and budget comparison inputs within farm scope. |
| `SEASON_MANAGE` | Create/update/lifecycle-manage seasons within farm scope. |
| `WORKFORCE_MANAGE` | Manage full employee master records for the tenant. |
| `WORKFORCE_PICKER_READ` | Read the redacted active-employee assignment picker only. |
| `ACTIVITY_READ` | Read activities/logs within tenant, farm, or assignee scope. |
| `ACTIVITY_MANAGE` | Create, assign, and transition activity tasks within farm scope. |
| `ACTIVITY_LOG_APPEND` | Append/correct immutable logs within manager-farm or worker-assignee scope. |
| `HARVEST_READ` | Read harvest facts within farm scope. |
| `HARVEST_MANAGE` | Post/correct harvest facts within farm scope. |
| `INVENTORY_READ` | Read warehouse, material, supplier-safe, lot, balance, and movement views within warehouse scope. |
| `INVENTORY_MANAGE` | Manage inventory masters and post/correct movements within warehouse scope. |
| `INVENTORY_ASSIGNMENT_MANAGE` | Grant/revoke user warehouse assignments. |
| `COST_READ` | Read operating-cost entries/summaries within tenant or farm scope. |
| `COST_MANAGE` | Post/correct operating-cost entries within tenant scope. |

Codes are fixed seed data. Unknown codes never elevate access. Adding or renaming a code requires a versioned migration, route-registry update, this matrix update, and contract tests.

## Fixed role grants

| Role | Granted permissions | Effective scope |
|---|---|---|
| `TENANT_ADMIN` | All permission codes above | Current tenant; assignment-management commands still validate target tenant. |
| `EXECUTIVE` | `FARM_READ`, `SEASON_READ`, `ACTIVITY_READ`, `HARVEST_READ`, `INVENTORY_READ`, `COST_READ` | Current tenant. No user/workforce mutation and no writes. |
| `FARM_MANAGER` | `FARM_READ`, `FARM_MANAGE`, `SEASON_READ`, `SEASON_MANAGE`, `WORKFORCE_PICKER_READ`, `ACTIVITY_READ`, `ACTIVITY_MANAGE`, `ACTIVITY_LOG_APPEND`, `HARVEST_READ`, `HARVEST_MANAGE`, `INVENTORY_READ`, `COST_READ` | Assigned farms; inventory additionally requires assigned warehouse. No cost mutation. |
| `INVENTORY_MANAGER` | `INVENTORY_READ`, `INVENTORY_MANAGE` | Assigned warehouses only. |
| `DATA_ANALYST` | `FARM_READ`, `SEASON_READ`, `ACTIVITY_READ`, `HARVEST_READ`, `INVENTORY_READ`, `COST_READ` | Current tenant, read-only; no full employee/profile data. |
| `FIELD_WORKER` | `ACTIVITY_READ`, `ACTIVITY_LOG_APPEND` | Activities assigned to the linked employee; log mutation limited to own logs/corrections. |
| `SUPPLIER` | None in this milestone | Deny all business routes. A supplier portal requires a separate threat model and role contract. |

Role grants are DB-backed allowlists. JWT role/scope claims are ignored for authorization. Role/assignment changes apply on the next request because permissions are loaded from PostgreSQL during authentication.

## Route registry

`{id}` means a Spring `PathPattern` UUID variable. Every row also requires authenticated OIDC, active tenant/profile, a registered HTTP method + route template, and the listed service scope. Every `POST`/`PATCH` mutation requires `Idempotency-Key` and creates a phase-3 command record; this applies even where a row below omits the repeated word “idempotent.” No business `DELETE` route exists.

### Identity and tenant administration

| Method + route template | Minimum permission | Service scope/rule |
|---|---|---|
| `GET /api/v1/me` | Authenticated identity; no business permission | Return only the enriched current principal; never accept a tenant parameter. |
| `GET /api/v1/users` | `IDENTITY_USER_MANAGE` | Current tenant; bounded list/filter. |
| `POST /api/v1/users` | `IDENTITY_USER_MANAGE` | Current tenant; exact configured issuer/subject. |
| `GET /api/v1/users/{id}` | `IDENTITY_USER_MANAGE` | Current tenant; hidden cross-tenant UUID is 404. |
| `POST /api/v1/users/{id}/deactivate` | `IDENTITY_USER_MANAGE` | Current tenant; optimistic version; cannot remove final active admin path. |
| `POST /api/v1/users/{id}/reactivate` | `IDENTITY_USER_MANAGE` | Current tenant; optimistic version. |
| `POST /api/v1/users/{id}/external-identities` | `IDENTITY_USER_MANAGE` | Current tenant; exact issuer/subject; global pair uniqueness; audited/idempotent. |
| `POST /api/v1/users/{id}/external-identities/{identityId}/unlink` | `IDENTITY_USER_MANAGE` | Current tenant; cannot remove final active admin path. |
| `POST /api/v1/users/{id}/roles` | `IDENTITY_ROLE_MANAGE` | Fixed role codes only; audited/idempotent. |
| `POST /api/v1/users/{id}/roles/{roleCode}/revoke` | `IDENTITY_ROLE_MANAGE` | Current tenant; cannot revoke final active tenant admin. |

There is no HTTP route for first-admin bootstrap, tenant creation, password handling, JIT provisioning, or role/permission-code creation.

### Farm, season, workforce, activities, and harvest

| Method + route template | Minimum permission | Service scope/rule |
|---|---|---|
| `GET /api/v1/farms` | `FARM_READ` | Tenant-wide reader or assigned farms; scope predicate applies before paging. |
| `GET /api/v1/farms/{id}` | `FARM_READ` | Tenant-wide reader or assigned farm. |
| `POST /api/v1/farms` | `FARM_MANAGE` | Tenant-wide scope required; assigned managers cannot create an unscoped farm. |
| `PATCH /api/v1/farms/{id}` | `FARM_MANAGE` | Tenant-wide or assigned-farm scope + `If-Match`. |
| `POST /api/v1/farms/{id}/deactivate` | `FARM_MANAGE` | Tenant-wide scope required; lifecycle/reference checks; no physical delete. |
| `POST /api/v1/farms/{id}/reactivate` | `FARM_MANAGE` | Tenant-wide scope required; code remains reserved. |
| `GET /api/v1/fields` | `FARM_READ` | Tenant-wide reader or assigned farms; scope predicate applies before paging. |
| `GET /api/v1/fields/{id}` | `FARM_READ` | Tenant-wide reader or assigned parent farm. |
| `POST /api/v1/farms/{id}/fields` | `FARM_MANAGE` | Tenant-wide or assigned parent-farm scope. |
| `PATCH /api/v1/fields/{id}` | `FARM_MANAGE` | Tenant-wide or assigned parent-farm scope + `If-Match`. |
| `POST /api/v1/fields/{id}/deactivate` | `FARM_MANAGE` | Tenant-wide or assigned parent-farm scope; reference checks. |
| `POST /api/v1/fields/{id}/reactivate` | `FARM_MANAGE` | Tenant-wide or assigned parent-farm scope; code remains reserved. |
| `GET /api/v1/crops` | `FARM_READ` | Current tenant catalog; bounded list. |
| `GET /api/v1/crops/{id}` | `FARM_READ` | Current tenant catalog. |
| `POST /api/v1/crops` | `FARM_MANAGE` | Tenant-wide scope required; farm assignment alone is insufficient. |
| `PATCH /api/v1/crops/{id}` | `FARM_MANAGE` | Tenant-wide scope required + `If-Match`. |
| `POST /api/v1/crops/{id}/deactivate` | `FARM_MANAGE` | Tenant-wide scope required; reference checks. |
| `POST /api/v1/crops/{id}/reactivate` | `FARM_MANAGE` | Tenant-wide scope required; code remains reserved. |
| `POST /api/v1/farm-assignments` | `FARM_ASSIGNMENT_MANAGE` | Tenant-wide scope; tenant-safe user/farm FKs. |
| `POST /api/v1/farm-assignments/{id}/revoke` | `FARM_ASSIGNMENT_MANAGE` | Tenant-wide scope; audited revoke. |
| `GET /api/v1/seasons` | `SEASON_READ` | Tenant-wide reader or assigned farms; scope before paging. |
| `GET /api/v1/seasons/{id}` | `SEASON_READ` | Tenant-wide reader or assigned farm. |
| `POST /api/v1/seasons` | `SEASON_MANAGE` | Tenant-wide or assigned parent-farm scope; date/budget invariants. |
| `PATCH /api/v1/seasons/{id}` | `SEASON_MANAGE` | Tenant-wide or assigned farm scope + `If-Match`. |
| `POST /api/v1/seasons/{id}/transition` | `SEASON_MANAGE` | Tenant-wide or assigned farm scope; state machine enforced. |
| `GET /api/v1/employees` | `WORKFORCE_MANAGE` | Tenant-wide scope; full employee master list. |
| `GET /api/v1/employees/{id}` | `WORKFORCE_MANAGE` | Tenant-wide scope; full employee master. |
| `POST /api/v1/employees` | `WORKFORCE_MANAGE` | Tenant-wide scope. |
| `PATCH /api/v1/employees/{id}` | `WORKFORCE_MANAGE` | Tenant-wide scope + `If-Match`. |
| `POST /api/v1/employees/{id}/deactivate` | `WORKFORCE_MANAGE` | Tenant-wide scope; assignment/reference checks. |
| `POST /api/v1/employees/{id}/reactivate` | `WORKFORCE_MANAGE` | Tenant-wide scope; code remains reserved. |
| `GET /api/v1/employees/eligible` | `WORKFORCE_PICKER_READ` | Redacted code/display/active projection only; bounded search. |
| `GET /api/v1/activities` | `ACTIVITY_READ` | Tenant-wide reader, assigned farm manager, or assigned worker; scope before paging. |
| `GET /api/v1/activities/{id}` | `ACTIVITY_READ` | Tenant-wide reader, assigned farm manager, or assigned worker. |
| `POST /api/v1/activities` | `ACTIVITY_MANAGE` | Tenant-wide or assigned farm scope. |
| `PATCH /api/v1/activities/{id}` | `ACTIVITY_MANAGE` | Tenant-wide or assigned farm scope + `If-Match`; only mutable task metadata before terminal state. |
| `POST /api/v1/activities/{id}/assignments` | `ACTIVITY_MANAGE` | Tenant-wide or assigned farm scope; target employee active. |
| `POST /api/v1/activities/{id}/assignments/{assignmentId}/revoke` | `ACTIVITY_MANAGE` | Tenant-wide or assigned farm scope; audited unassignment; no history deletion. |
| `POST /api/v1/activities/{id}/transition` | `ACTIVITY_MANAGE` | Tenant-wide or assigned farm scope; state machine enforced. |
| `POST /api/v1/activities/{id}/logs` | `ACTIVITY_LOG_APPEND` | Tenant-wide/assigned manager, or worker assigned to this activity. |
| `POST /api/v1/activities/{id}/logs/{logId}/corrections` | `ACTIVITY_LOG_APPEND` | Manager scope or assigned worker correcting own log lineage. |
| `GET /api/v1/harvests` | `HARVEST_READ` | Tenant-wide reader or assigned farm manager; scope before paging. |
| `GET /api/v1/harvests/{id}` | `HARVEST_READ` | Tenant-wide reader or assigned farm manager. |
| `POST /api/v1/harvests` | `HARVEST_MANAGE` | Tenant-wide or assigned farm scope; no worker write. |
| `POST /api/v1/harvests/{id}/corrections` | `HARVEST_MANAGE` | Tenant-wide or assigned farm scope; linked immutable correction. |

### Inventory and procurement

| Method + route template | Minimum permission | Service scope/rule |
|---|---|---|
| `GET /api/v1/warehouses` | `INVENTORY_READ` | Tenant-wide reader or assigned warehouses; scope before paging. |
| `GET /api/v1/warehouses/{id}` | `INVENTORY_READ` | Tenant-wide reader or assigned warehouse. |
| `POST /api/v1/warehouses` | `INVENTORY_MANAGE` | Tenant-wide scope required; assigned inventory manager cannot create an unscoped warehouse. |
| `PATCH /api/v1/warehouses/{id}` | `INVENTORY_MANAGE` | Tenant-wide or assigned warehouse scope + `If-Match`. |
| `POST /api/v1/warehouses/{id}/deactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; balance/reference checks. |
| `POST /api/v1/warehouses/{id}/reactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; code remains reserved. |
| `GET /api/v1/materials` | `INVENTORY_READ` | Current tenant safe catalog; user needs tenant-wide read or at least one assigned warehouse. |
| `GET /api/v1/materials/{id}` | `INVENTORY_READ` | Current tenant safe catalog; same scope rule as list. |
| `POST /api/v1/materials` | `INVENTORY_MANAGE` | Tenant-wide scope required; warehouse assignment alone is insufficient. |
| `PATCH /api/v1/materials/{id}` | `INVENTORY_MANAGE` | Tenant-wide scope required + `If-Match`. |
| `POST /api/v1/materials/{id}/deactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; lot/reference checks. |
| `POST /api/v1/materials/{id}/reactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; code remains reserved. |
| `GET /api/v1/suppliers` | `INVENTORY_READ` | Safe tenant catalog; requires tenant-wide read or at least one assigned warehouse. |
| `GET /api/v1/suppliers/{id}` | `INVENTORY_READ` | Safe tenant DTO; no unrelated contact/finance fields. |
| `POST /api/v1/suppliers` | `INVENTORY_MANAGE` | Tenant-wide scope required; warehouse assignment alone is insufficient. |
| `PATCH /api/v1/suppliers/{id}` | `INVENTORY_MANAGE` | Tenant-wide scope required + `If-Match`. |
| `POST /api/v1/suppliers/{id}/deactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; receipt/reference checks. |
| `POST /api/v1/suppliers/{id}/reactivate` | `INVENTORY_MANAGE` | Tenant-wide scope required; code remains reserved. |
| `POST /api/v1/warehouse-assignments` | `INVENTORY_ASSIGNMENT_MANAGE` | Tenant-wide scope; tenant-safe user/warehouse FKs. |
| `POST /api/v1/warehouse-assignments/{id}/revoke` | `INVENTORY_ASSIGNMENT_MANAGE` | Tenant-wide scope; audited revoke. |
| `GET /api/v1/inventory/balances` | `INVENTORY_READ` | Tenant-wide or assigned warehouse scope; bounded filters. |
| `GET /api/v1/inventory/lots` | `INVENTORY_READ` | Tenant-wide or assigned warehouse scope; bounded filters. |
| `GET /api/v1/inventory/transactions` | `INVENTORY_READ` | Tenant-wide or assigned warehouse scope; bounded filters. |
| `GET /api/v1/inventory/transactions/{id}` | `INVENTORY_READ` | Tenant-wide or assigned warehouse scope. |
| `POST /api/v1/inventory/transactions` | `INVENTORY_MANAGE` | Tenant-wide scope or assigned warehouse; receipt/issue locks lot/aggregate rows. |
| `POST /api/v1/inventory/transactions/{id}/reversals` | `INVENTORY_MANAGE` | Tenant-wide scope or assigned warehouse; append-only bounded compensating movement; original/allocation invariants. |

### Operating cost

| Method + route template | Minimum permission | Service scope/rule |
|---|---|---|
| `GET /api/v1/cost-entries` | `COST_READ` | Tenant-wide reader or assigned farm manager; date/page caps required. |
| `GET /api/v1/cost-entries/{id}` | `COST_READ` | Tenant-wide reader or assigned farm manager. |
| `GET /api/v1/cost-summaries` | `COST_READ` | Tenant-wide reader or assigned farm manager; date/group caps required. |
| `POST /api/v1/cost-entries` | `COST_MANAGE` | Tenant-wide scope; immutable posting. |
| `POST /api/v1/cost-entries/{id}/corrections` | `COST_MANAGE` | Tenant-wide scope; service-generated reversal/new posting. |

## Enforcement and test invariants

1. Route authorization uses the enriched DB principal before `AuthorizationFilter`; service methods independently reapply tenant context and enforce scope.
2. Endpoint inventory compares every controller mapping to this registry. Missing, duplicate, broader, or method-mismatched entries fail CI.
3. Every role runs allow/deny tests for every route family. `SUPPLIER` and an authenticated user with zero roles are explicit deny fixtures.
4. Cross-tenant UUIDs and out-of-scope farm/warehouse/activity UUIDs are tested for list, detail, update, transition, correction, and assignment paths.
5. Repository queries apply tenant and assignment predicates before materialization. Post-load filtering is prohibited.
6. Hidden resources return 404 where existence is sensitive; authenticated permission failure without a resource lookup returns 403. Tests lock this distinction.
7. No client body/header/path value can select tenant, permission, role grant source, SQL sort/filter, or integration role.

## Unresolved questions

None for the MVP authorization contract. A future supplier portal or custom-role feature requires a new matrix and security review.
