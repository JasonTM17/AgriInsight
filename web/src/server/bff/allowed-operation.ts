import type { paths as AnalyticsPaths } from "@/server/generated/analytics/schema";
import type { paths as BackendPaths } from "@/server/generated/backend/schema";

export type UpstreamService = "analytics" | "backend";

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
      queryParameters?: readonly string[];
      service: "analytics";
    }>
  | Readonly<{
      method: "GET";
      path: GetPath<BackendPaths>;
      pathParameters?: readonly string[];
      queryParameters?: readonly string[];
      service: "backend";
    }>;

type AllowedMutation = Readonly<{
  method: "POST";
  path: PostPath<BackendPaths>;
  pathParameters: readonly string[];
  requiresIfMatch?: boolean;
  service: "backend";
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
  analyticsCropHealth: {
    method: "GET",
    path: "/internal/v1/crop-health",
    queryParameters: ["farm_code", "limit", "offset"],
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
  }
} as const satisfies Record<string, AllowedMutation>);

export type AllowedOperationName = keyof typeof ALLOWED_OPERATIONS;
export type AllowedMutationName = keyof typeof ALLOWED_MUTATIONS;

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
