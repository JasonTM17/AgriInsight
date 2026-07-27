import "server-only";

import {
  getAdminAuditPage,
  getAdminScopeCatalogs,
  getAdminUser,
  getAdminUserPage,
  getAdminUserRelations,
  type AdminReadContext
} from "./admin-resource-client";

const ROLE_LABELS: Readonly<Record<string, string>> = {
  DATA_ANALYST: "Chuyên viên dữ liệu",
  EXECUTIVE: "Điều hành",
  FARM_MANAGER: "Quản lý nông trại",
  FIELD_WORKER: "Nhân viên hiện trường",
  INVENTORY_MANAGER: "Quản lý kho",
  SUPPLIER: "Nhà cung cấp",
  TENANT_ADMIN: "Quản trị tenant"
};

export async function loadAdminDirectory(
  context: AdminReadContext,
  input: Readonly<{ active?: boolean; offset: number; search?: string }>
) {
  const page = await getAdminUserPage(context, input);
  return Object.freeze({
    ...page,
    users: page.items.map((user) => ({
      contactLabel: user.email ?? "Chưa có địa chỉ liên hệ",
      displayName: user.displayName,
      status: user.active ? "active" as const : "inactive" as const,
      userKey: user.id,
      version: user.version
    }))
  });
}

export async function loadAdminSubject(
  context: AdminReadContext,
  userKey: string
) {
  const [{ etag, user }, relations, catalogs] = await Promise.all([
    getAdminUser(context, userKey),
    getAdminUserRelations(context, userKey),
    getAdminScopeCatalogs(context)
  ]);
  const farmLabels = new Map(
    catalogs.farms.map((farm) => [farm.id, farm.displayName])
  );
  const warehouseLabels = new Map(
    catalogs.warehouses.map((warehouse) => [warehouse.id, warehouse.displayName])
  );
  return Object.freeze({
    assignments: [
      ...relations.farmAssignments.map((assignment) => ({
        assignmentKey: assignment.id,
        scopeKey: assignment.farmId,
        scopeLabel: farmLabels.get(assignment.farmId) ?? "Nông trại chưa còn trong danh mục",
        scopeType: "farm" as const,
        status: assignment.active ? "active" as const : "inactive" as const,
        version: assignment.version
      })),
      ...relations.warehouseAssignments.map((assignment) => ({
        assignmentKey: assignment.id,
        scopeKey: assignment.warehouseId,
        scopeLabel: warehouseLabels.get(assignment.warehouseId) ?? "Kho chưa còn trong danh mục",
        scopeType: "warehouse" as const,
        status: assignment.active ? "active" as const : "inactive" as const,
        version: assignment.version
      }))
    ],
    availableFarms: catalogs.farms.map((farm) => ({
      key: farm.id,
      label: farm.displayName
    })),
    availableWarehouses: catalogs.warehouses.map((warehouse) => ({
      key: warehouse.id,
      label: warehouse.displayName
    })),
    contactLabel: user.email ?? "Chưa có địa chỉ liên hệ",
    displayName: user.displayName,
    etag,
    providerLinks: relations.externalIdentities.map((identity) => ({
      identityKey: identity.id,
      providerLabel: "Nhà cung cấp OIDC đã xác minh",
      status: identity.active ? "active" as const : "inactive" as const,
      version: identity.version
    })),
    roles: relations.roles.map((role) => ({
      code: role.roleCode,
      label: ROLE_LABELS[role.roleCode] ?? role.roleCode,
      status: role.active ? "active" as const : "inactive" as const,
      version: role.version
    })),
    status: user.active ? "active" as const : "inactive" as const,
    userKey: user.id,
    version: user.version
  });
}

export async function loadAdminAudit(
  context: AdminReadContext,
  input: Readonly<{
    action?: string;
    offset: number;
    outcome?: "CONFLICT" | "DENIED" | "SUCCEEDED";
    targetType?: string;
  }>
) {
  const [page, users] = await Promise.all([
    getAdminAuditPage(context, input),
    getAdminUserPage(context, { offset: 0 })
  ]);
  const userLabels = new Map(
    users.items.map((user) => [user.id, user.displayName])
  );
  return Object.freeze({
    ...page,
    entries: page.items.map((entry) => ({
      actionLabel: humanizeCode(entry.action),
      actorLabel: entry.actorProfileId
        ? userLabels.get(entry.actorProfileId) ?? "Chủ thể tenant"
        : humanizeCode(entry.actorType),
      at: entry.occurredAt,
      correlationId: entry.correlationId ?? null,
      eventKey: entry.id,
      outcome: entry.outcome,
      reasonLabel: entry.reasonCode ? humanizeCode(entry.reasonCode) : null,
      targetLabel: entry.targetId
        ? userLabels.get(entry.targetId) ?? humanizeCode(entry.targetType)
        : humanizeCode(entry.targetType)
    }))
  });
}

function humanizeCode(value: string): string {
  return value
    .trim()
    .replace(/[_-]+/g, " ")
    .toLocaleLowerCase("vi-VN")
    .replace(/^./u, (character) => character.toLocaleUpperCase("vi-VN"));
}
