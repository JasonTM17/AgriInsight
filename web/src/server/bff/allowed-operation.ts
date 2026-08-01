import type { paths as AnalyticsPaths } from "@/server/generated/analytics/schema";
import type { paths as BackendPaths } from "@/server/generated/backend/schema";

export type UpstreamService = "analytics" | "backend";
export type PathParameterKind = "role-code" | "uuid";

type GetPath<ContractPaths> = Extract<
  {
    [Path in keyof ContractPaths]: ContractPaths[Path] extends {
      readonly get: unknown;
    }
      ? Path
      : never;
  }[keyof ContractPaths],
  `/${string}`
>;

type PostPath<ContractPaths> = Extract<
  {
    [Path in keyof ContractPaths]: ContractPaths[Path] extends {
      readonly post: unknown;
    }
      ? Path
      : never;
  }[keyof ContractPaths],
  `/${string}`
>;

type AllowedReadOperation =
  | Readonly<{
      method: "GET";
      path: GetPath<AnalyticsPaths>;
      pathParameters?: readonly string[];
      pathParameterKinds?: Readonly<Record<string, PathParameterKind>>;
      queryParameters?: readonly string[];
      service: "analytics";
    }>
  | Readonly<{
      method: "GET";
      path: GetPath<BackendPaths>;
      pathParameters?: readonly string[];
      pathParameterKinds?: Readonly<Record<string, PathParameterKind>>;
      queryParameters?: readonly string[];
      service: "backend";
    }>;

type AllowedMutation = Readonly<{
  method: "POST";
  path: PostPath<BackendPaths>;
  pathParameters: readonly string[];
  pathParameterKinds?: Readonly<Record<string, PathParameterKind>>;
  requiresIfMatch?: boolean;
  service: "backend";
}>;

type AllowedAnalyticsCommand = Readonly<{
  method: "POST";
  path: PostPath<AnalyticsPaths>;
  service: "analytics";
}>;

export const ALLOWED_OPERATIONS = Object.freeze({
  analyticsCatalog: {
    method: "GET",
    path: "/internal/v1/catalog",
    service: "analytics"
  },
  analyticsCosts: {
    method: "GET",
    path: "/internal/v1/costs",
    service: "analytics"
  },
  analyticsCostExport: {
    method: "GET",
    path: "/internal/v1/costs/export",
    queryParameters: [
      "activity",
      "crop",
      "farm",
      "format",
      "month_from",
      "month_to",
      "scope",
      "season",
      "supplier",
      "top_n"
    ],
    service: "analytics"
  },
  analyticsProcurementCosts: {
    method: "GET",
    path: "/internal/v1/costs/procurement",
    queryParameters: [
      "farm_code",
      "limit",
      "month_from",
      "month_to",
      "offset"
    ],
    service: "analytics"
  },
  analyticsCropHealth: {
    method: "GET",
    path: "/internal/v1/crop-health",
    queryParameters: ["farm_code", "field_code", "limit", "offset"],
    service: "analytics"
  },
  analyticsDataQuality: {
    method: "GET",
    path: "/internal/v1/data-quality",
    service: "analytics"
  },
  analyticsFarms: {
    method: "GET",
    path: "/internal/v1/farms",
    queryParameters: [
      "farm_code",
      "field_code",
      "crop_code",
      "season_code",
      "date_preset",
      "limit",
      "offset",
      "sort"
    ],
    service: "analytics"
  },
  analyticsYieldForecast: {
    method: "GET",
    path: "/internal/v1/yield-forecast",
    queryParameters: [
      "farm_code",
      "field_code",
      "crop_code",
      "season_code",
      "limit",
      "offset"
    ],
    service: "analytics"
  },
  analyticsInventory: {
    method: "GET",
    path: "/internal/v1/inventory",
    queryParameters: ["warehouse_code", "limit", "offset"],
    service: "analytics"
  },
  analyticsOverview: {
    method: "GET",
    path: "/internal/v1/overview",
    queryParameters: [
      "farm_code",
      "field_code",
      "crop_code",
      "season_code",
      "date_preset"
    ],
    service: "analytics"
  },
  activityAssignments: {
    method: "GET",
    path: "/api/v1/activities/{id}/assignments",
    pathParameters: ["id"],
    queryParameters: ["limit", "offset"],
    service: "backend"
  },
  activityById: {
    method: "GET",
    path: "/api/v1/activities/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  activityCatalog: {
    method: "GET",
    path: "/api/v1/activities",
    queryParameters: [
      "activityType",
      "farmId",
      "fieldId",
      "limit",
      "offset",
      "search",
      "seasonId",
      "status"
    ],
    service: "backend"
  },
  activityLogHistory: {
    method: "GET",
    path: "/api/v1/activities/{id}/logs/{logId}/history",
    pathParameters: ["id", "logId"],
    queryParameters: ["limit", "offset"],
    service: "backend"
  },
  activityLogs: {
    method: "GET",
    path: "/api/v1/activities/{id}/logs",
    pathParameters: ["id"],
    queryParameters: ["limit", "offset"],
    service: "backend"
  },
  adminAuditEvents: {
    method: "GET",
    path: "/api/v1/audit-events",
    queryParameters: [
      "action",
      "actorProfileId",
      "limit",
      "offset",
      "outcome",
      "targetId",
      "targetType"
    ],
    service: "backend"
  },
  adminFarmAssignments: {
    method: "GET",
    path: "/api/v1/farm-assignments",
    queryParameters: [
      "active",
      "farmId",
      "limit",
      "offset",
      "userProfileId"
    ],
    service: "backend"
  },
  adminUserById: {
    method: "GET",
    path: "/api/v1/users/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  adminUserExternalIdentities: {
    method: "GET",
    path: "/api/v1/users/{id}/external-identities",
    pathParameters: ["id"],
    queryParameters: ["active", "limit", "offset"],
    service: "backend"
  },
  adminUserRoles: {
    method: "GET",
    path: "/api/v1/users/{id}/roles",
    pathParameters: ["id"],
    queryParameters: ["active", "limit", "offset"],
    service: "backend"
  },
  adminUsers: {
    method: "GET",
    path: "/api/v1/users",
    queryParameters: ["active", "limit", "offset", "search"],
    service: "backend"
  },
  adminWarehouseAssignments: {
    method: "GET",
    path: "/api/v1/warehouse-assignments",
    queryParameters: [
      "active",
      "limit",
      "offset",
      "userProfileId",
      "warehouseId"
    ],
    service: "backend"
  },
  currentUser: {
    method: "GET",
    path: "/api/v1/me",
    service: "backend"
  },
  cropById: {
    method: "GET",
    path: "/api/v1/crops/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  farmById: {
    method: "GET",
    path: "/api/v1/farms/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  farmCatalog: {
    method: "GET",
    path: "/api/v1/farms",
    queryParameters: ["limit", "offset", "active", "search"],
    service: "backend"
  },
  fieldById: {
    method: "GET",
    path: "/api/v1/fields/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  inventoryBalances: {
    method: "GET",
    path: "/api/v1/inventory/balances",
    queryParameters: ["limit", "lowStock", "materialId", "offset", "warehouseId"],
    service: "backend"
  },
  inventoryLots: {
    method: "GET",
    path: "/api/v1/inventory/lots",
    queryParameters: [
      "expiringBefore",
      "includeDepleted",
      "limit",
      "materialId",
      "offset",
      "warehouseId"
    ],
    service: "backend"
  },
  inventoryTransactionById: {
    method: "GET",
    path: "/api/v1/inventory/transactions/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  inventoryTransactions: {
    method: "GET",
    path: "/api/v1/inventory/transactions",
    queryParameters: [
      "kind",
      "limit",
      "materialId",
      "occurredFrom",
      "occurredTo",
      "offset",
      "warehouseId"
    ],
    service: "backend"
  },
  operatingCostEntries: {
    method: "GET",
    path: "/api/v1/cost-entries",
    queryParameters: [
      "activityId",
      "category",
      "entryKind",
      "farmId",
      "fieldId",
      "limit",
      "occurredFrom",
      "occurredTo",
      "offset",
      "seasonId",
      "targetType"
    ],
    service: "backend"
  },
  operatingCostEntryById: {
    method: "GET",
    path: "/api/v1/cost-entries/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  operatingCostSummaries: {
    method: "GET",
    path: "/api/v1/cost-summaries",
    queryParameters: [
      "category",
      "farmId",
      "groupBy",
      "occurredFrom",
      "occurredTo",
      "seasonId"
    ],
    service: "backend"
  },
  realtimeAlerts: {
    method: "GET",
    path: "/api/v1/realtime/alerts",
    service: "backend"
  },
  materialCatalog: {
    method: "GET",
    path: "/api/v1/materials",
    queryParameters: ["limit", "offset", "active", "search"],
    service: "backend"
  },
  seasonById: {
    method: "GET",
    path: "/api/v1/seasons/{id}",
    pathParameters: ["id"],
    service: "backend"
  },
  supplierCatalog: {
    method: "GET",
    path: "/api/v1/suppliers",
    queryParameters: ["limit", "offset", "active", "search"],
    service: "backend"
  },
  warehouseCatalog: {
    method: "GET",
    path: "/api/v1/warehouses",
    queryParameters: ["limit", "offset", "active", "search"],
    service: "backend"
  }
} as const satisfies Record<string, AllowedReadOperation>);

export const ALLOWED_MUTATIONS = Object.freeze({
  adminActivityAssignmentGrant: {
    method: "POST",
    path: "/api/v1/activities/{id}/assignments",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminActivityAssignmentRevoke: {
    method: "POST",
    path: "/api/v1/activities/{id}/assignments/{assignmentId}/revoke",
    pathParameters: ["id", "assignmentId"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminFarmAssignmentGrant: {
    method: "POST",
    path: "/api/v1/farm-assignments",
    pathParameters: [],
    requiresIfMatch: true,
    service: "backend"
  },
  adminFarmAssignmentRevoke: {
    method: "POST",
    path: "/api/v1/farm-assignments/{id}/revoke",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminRoleGrant: {
    method: "POST",
    path: "/api/v1/users/{id}/roles",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminRoleRevoke: {
    method: "POST",
    path: "/api/v1/users/{id}/roles/{roleCode}/revoke",
    pathParameterKinds: { id: "uuid", roleCode: "role-code" },
    pathParameters: ["id", "roleCode"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminUserCreate: {
    method: "POST",
    path: "/api/v1/users",
    pathParameters: [],
    service: "backend"
  },
  adminUserDeactivate: {
    method: "POST",
    path: "/api/v1/users/{id}/deactivate",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminUserLinkIdentity: {
    method: "POST",
    path: "/api/v1/users/{id}/external-identities",
    pathParameters: ["id"],
    service: "backend"
  },
  adminUserReactivate: {
    method: "POST",
    path: "/api/v1/users/{id}/reactivate",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  adminUserUnlinkIdentity: {
    method: "POST",
    path: "/api/v1/users/{id}/external-identities/{identityId}/unlink",
    pathParameters: ["id", "identityId"],
    service: "backend"
  },
  adminWarehouseAssignmentGrant: {
    method: "POST",
    path: "/api/v1/warehouse-assignments",
    pathParameters: [],
    requiresIfMatch: true,
    service: "backend"
  },
  adminWarehouseAssignmentRevoke: {
    method: "POST",
    path: "/api/v1/warehouse-assignments/{id}/revoke",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  activityLogAppend: {
    method: "POST",
    path: "/api/v1/activities/{id}/logs",
    pathParameters: ["id"],
    service: "backend"
  },
  activityLogCorrection: {
    method: "POST",
    path: "/api/v1/activities/{id}/logs/{logId}/corrections",
    pathParameters: ["id", "logId"],
    service: "backend"
  },
  inventoryTransactionPost: {
    method: "POST",
    path: "/api/v1/inventory/transactions",
    pathParameters: [],
    service: "backend"
  },
  inventoryTransactionReversal: {
    method: "POST",
    path: "/api/v1/inventory/transactions/{id}/reversals",
    pathParameters: ["id"],
    requiresIfMatch: true,
    service: "backend"
  },
  operatingCostCorrection: {
    method: "POST",
    path: "/api/v1/cost-entries/{id}/corrections",
    pathParameters: ["id"],
    service: "backend"
  },
  operatingCostPost: {
    method: "POST",
    path: "/api/v1/cost-entries",
    pathParameters: [],
    service: "backend"
  },
  realtimeAlertAcknowledge: {
    method: "POST",
    path: "/api/v1/realtime/alerts/{id}/acknowledgements",
    pathParameters: ["id"],
    service: "backend"
  }
} as const satisfies Record<string, AllowedMutation>);

export const ALLOWED_ANALYTICS_COMMANDS = Object.freeze({
  analyticsAssistantQuery: {
    method: "POST",
    path: "/internal/v1/assistant/query",
    service: "analytics"
  }
} as const satisfies Record<string, AllowedAnalyticsCommand>);

export type AllowedOperationName = keyof typeof ALLOWED_OPERATIONS;
export type AllowedMutationName = keyof typeof ALLOWED_MUTATIONS;
export type AllowedAnalyticsCommandName =
  keyof typeof ALLOWED_ANALYTICS_COMMANDS;

export function resolveAllowedOperation(candidate: string): AllowedReadOperation {
  if (!Object.hasOwn(ALLOWED_OPERATIONS, candidate)) {
    throw new Error("Upstream operation is not allowlisted");
  }
  return ALLOWED_OPERATIONS[candidate as AllowedOperationName];
}

export function resolveAllowedMutation(candidate: string): AllowedMutation {
  if (!Object.hasOwn(ALLOWED_MUTATIONS, candidate)) {
    throw new Error("Upstream mutation is not allowlisted");
  }
  return ALLOWED_MUTATIONS[candidate as AllowedMutationName];
}

export function resolveAllowedAnalyticsCommand(
  candidate: string
): AllowedAnalyticsCommand {
  if (!Object.hasOwn(ALLOWED_ANALYTICS_COMMANDS, candidate)) {
    throw new Error("Upstream analytics command is not allowlisted");
  }
  return ALLOWED_ANALYTICS_COMMANDS[
    candidate as AllowedAnalyticsCommandName
  ];
}
