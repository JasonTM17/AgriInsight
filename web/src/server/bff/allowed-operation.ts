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

type AllowedOperation =
  | Readonly<{
      method: "GET";
      path: GetPath<AnalyticsPaths>;
      service: "analytics";
    }>
  | Readonly<{
      method: "GET";
      path: GetPath<BackendPaths>;
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
    service: "analytics"
  },
  analyticsInventory: {
    method: "GET",
    path: "/internal/v1/inventory",
    service: "analytics"
  },
  analyticsOverview: {
    method: "GET",
    path: "/internal/v1/overview",
    service: "analytics"
  },
  currentUser: {
    method: "GET",
    path: "/api/v1/me",
    service: "backend"
  },
  farmCatalog: {
    method: "GET",
    path: "/api/v1/farms",
    service: "backend"
  },
  warehouseCatalog: {
    method: "GET",
    path: "/api/v1/warehouses",
    service: "backend"
  }
} as const satisfies Record<string, AllowedOperation>);

export type AllowedOperationName = keyof typeof ALLOWED_OPERATIONS;

export function resolveAllowedOperation(candidate: string): AllowedOperation {
  if (!Object.hasOwn(ALLOWED_OPERATIONS, candidate)) {
    throw new Error("Upstream operation is not allowlisted");
  }
  return ALLOWED_OPERATIONS[candidate as AllowedOperationName];
}
