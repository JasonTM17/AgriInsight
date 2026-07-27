import { describe, expect, it } from "vitest";

import {
  ALLOWED_MUTATIONS,
  ALLOWED_OPERATIONS,
  resolveAllowedMutation,
  resolveAllowedOperation
} from "@/server/bff/allowed-operation";

describe("exact upstream allowlist", () => {
  it("contains only fixed relative HTTP paths and GET read operations", () => {
    for (const operation of Object.values(ALLOWED_OPERATIONS)) {
      expect(operation.method).toBe("GET");
      expect(operation.path).toMatch(
        /^\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\})(?:\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\}))*$/
      );
      expect(operation.path).not.toContain("..");
      expect(operation.path).not.toContain("\\");
    }
  });

  it("contains the exact Phase 6 activity read operations", () => {
    expect(ALLOWED_OPERATIONS.activityCatalog).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.activityById).toEqual({
      method: "GET",
      path: "/api/v1/activities/{id}",
      pathParameters: ["id"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.activityAssignments).toEqual({
      method: "GET",
      path: "/api/v1/activities/{id}/assignments",
      pathParameters: ["id"],
      queryParameters: ["limit", "offset"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.activityLogs).toEqual({
      method: "GET",
      path: "/api/v1/activities/{id}/logs",
      pathParameters: ["id"],
      queryParameters: ["limit", "offset"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.activityLogHistory).toEqual({
      method: "GET",
      path: "/api/v1/activities/{id}/logs/{logId}/history",
      pathParameters: ["id", "logId"],
      queryParameters: ["limit", "offset"],
      service: "backend"
    });
  });

  it("contains the exact inventory-control read operations", () => {
    expect(ALLOWED_OPERATIONS.inventoryBalances).toEqual({
      method: "GET",
      path: "/api/v1/inventory/balances",
      queryParameters: ["limit", "lowStock", "materialId", "offset", "warehouseId"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.inventoryLots).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.inventoryTransactions).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.inventoryTransactionById).toEqual({
      method: "GET",
      path: "/api/v1/inventory/transactions/{id}",
      pathParameters: ["id"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.materialCatalog).toEqual({
      method: "GET",
      path: "/api/v1/materials",
      queryParameters: ["limit", "offset", "active", "search"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.supplierCatalog).toEqual({
      method: "GET",
      path: "/api/v1/suppliers",
      queryParameters: ["limit", "offset", "active", "search"],
      service: "backend"
    });
  });

  it("contains the exact cost-analysis read operations", () => {
    expect(ALLOWED_OPERATIONS.analyticsCostExport).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.analyticsProcurementCosts).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.operatingCostEntries).toEqual({
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
    });
    expect(ALLOWED_OPERATIONS.operatingCostEntryById).toEqual({
      method: "GET",
      path: "/api/v1/cost-entries/{id}",
      pathParameters: ["id"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.operatingCostSummaries).toEqual({
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
    });
  });

  it("keeps POST operations in a separate exact allowlist", () => {
    expect(ALLOWED_MUTATIONS).toEqual({
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
      }
    });
    for (const mutation of Object.values(ALLOWED_MUTATIONS)) {
      expect(mutation.method).toBe("POST");
      expect(mutation.path).toMatch(
        /^\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\})(?:\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\}))*$/
      );
    }
  });

  it.each([
    "http://metadata.invalid/latest",
    "../admin",
    "/api/v1/me",
    "analyticsOverview%2f..%2fadmin"
  ])("rejects caller-controlled operation %s", (candidate) => {
    expect(() => resolveAllowedOperation(candidate)).toThrow(
      "not allowlisted"
    );
  });

  it("does not allow callers to cross the GET and POST operation classes", () => {
    expect(() => resolveAllowedOperation("activityLogAppend")).toThrow(
      "not allowlisted"
    );
    expect(() => resolveAllowedMutation("activityCatalog")).toThrow(
      "not allowlisted"
    );
  });
});
