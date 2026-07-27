import "server-only";

import {
  ADMIN_PAGE_SIZE,
  adminAuditPageSchema,
  adminExternalIdentityPageSchema,
  adminFarmAssignmentPageSchema,
  adminFarmCatalogPageSchema,
  adminRolePageSchema,
  adminUserPageSchema,
  adminUserSchema,
  adminWarehouseAssignmentPageSchema,
  adminWarehouseCatalogPageSchema,
  type AdminAuditPage,
  type AdminExternalIdentity,
  type AdminFarmAssignment,
  type AdminNamedResource,
  type AdminRole,
  type AdminUser,
  type AdminUserPage,
  type AdminWarehouseAssignment
} from "./admin-contract-schemas";
import {
  AdminReadError,
  getAdminJson,
  requestAdminResource,
  type AdminReadContext
} from "./admin-resource-request";

export {
  AdminReadError,
  type AdminReadContext
} from "./admin-resource-request";

export async function getAdminUserPage(
  context: AdminReadContext,
  input: Readonly<{ active?: boolean; offset: number; search?: string }>
): Promise<AdminUserPage> {
  return getAdminJson(context, "adminUsers", adminUserPageSchema, {
    active: input.active,
    limit: ADMIN_PAGE_SIZE,
    offset: input.offset,
    search: input.search
  });
}

export async function getAdminUser(
  context: AdminReadContext,
  userKey: string
): Promise<Readonly<{ etag: string; user: AdminUser }>> {
  const response = await requestAdminResource(
    context,
    "adminUserById",
    {},
    { id: userKey }
  );
  const etag = response.headers.get("ETag");
  if (!etag || !/^"\d{1,19}"$/.test(etag)) {
    throw new AdminReadError("unavailable", 502);
  }
  return { etag, user: adminUserSchema.parse(await response.json()) };
}

export async function getAdminUserRelations(
  context: AdminReadContext,
  userKey: string
): Promise<Readonly<{
  externalIdentities: readonly AdminExternalIdentity[];
  farmAssignments: readonly AdminFarmAssignment[];
  roles: readonly AdminRole[];
  warehouseAssignments: readonly AdminWarehouseAssignment[];
}>> {
  const query = { limit: 100, offset: 0 };
  const [roles, externalIdentities, farmAssignments, warehouseAssignments] =
    await Promise.all([
      getAdminJson(
        context,
        "adminUserRoles",
        adminRolePageSchema,
        query,
        { id: userKey }
      ),
      getAdminJson(
        context,
        "adminUserExternalIdentities",
        adminExternalIdentityPageSchema,
        query,
        { id: userKey }
      ),
      getAdminJson(
        context,
        "adminFarmAssignments",
        adminFarmAssignmentPageSchema,
        { ...query, userProfileId: userKey }
      ),
      getAdminJson(
        context,
        "adminWarehouseAssignments",
        adminWarehouseAssignmentPageSchema,
        { ...query, userProfileId: userKey }
      )
    ]);
  assertSinglePage(roles);
  assertSinglePage(externalIdentities);
  assertSinglePage(farmAssignments);
  assertSinglePage(warehouseAssignments);
  return {
    externalIdentities: externalIdentities.items,
    farmAssignments: farmAssignments.items,
    roles: roles.items,
    warehouseAssignments: warehouseAssignments.items
  };
}

export async function getAdminScopeCatalogs(
  context: AdminReadContext
): Promise<Readonly<{
  farms: readonly AdminNamedResource[];
  warehouses: readonly AdminNamedResource[];
}>> {
  const query = { active: true, limit: 100, offset: 0 };
  const [farms, warehouses] = await Promise.all([
    getAdminJson(context, "farmCatalog", adminFarmCatalogPageSchema, query),
    getAdminJson(
      context,
      "warehouseCatalog",
      adminWarehouseCatalogPageSchema,
      query
    )
  ]);
  assertSinglePage(farms);
  assertSinglePage(warehouses);
  return { farms: farms.items, warehouses: warehouses.items };
}

export async function getAdminAuditPage(
  context: AdminReadContext,
  input: Readonly<{
    action?: string;
    offset: number;
    outcome?: "CONFLICT" | "DENIED" | "SUCCEEDED";
    targetType?: string;
  }>
): Promise<AdminAuditPage> {
  return getAdminJson(context, "adminAuditEvents", adminAuditPageSchema, {
    action: input.action,
    limit: ADMIN_PAGE_SIZE,
    offset: input.offset,
    outcome: input.outcome,
    targetType: input.targetType
  });
}

function assertSinglePage(page: Readonly<{ hasMore: boolean }>): void {
  if (page.hasMore) throw new AdminReadError("unavailable", 502);
}
