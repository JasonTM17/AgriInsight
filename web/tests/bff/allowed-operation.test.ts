import { describe, expect, it } from "vitest";

import {
  ALLOWED_ANALYTICS_COMMANDS,
  ALLOWED_MUTATIONS,
  ALLOWED_OPERATIONS,
  resolveAllowedAnalyticsCommand,
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

  it("contains the exact scoped yield forecast read operation", () => {
    expect(ALLOWED_OPERATIONS.analyticsYieldForecast).toEqual({
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
    });
  });

  it("contains only the exact realtime alert read and acknowledgement operations", () => {
    expect(ALLOWED_OPERATIONS.realtimeAlerts).toEqual({
      method: "GET",
      path: "/api/v1/realtime/alerts",
      service: "backend"
    });
    expect(ALLOWED_MUTATIONS.realtimeAlertAcknowledge).toEqual({
      method: "POST",
      path: "/api/v1/realtime/alerts/{id}/acknowledgements",
      pathParameters: ["id"],
      service: "backend"
    });
  });

  it("contains only the frozen tenant-administration resource families", () => {
    expect(ALLOWED_OPERATIONS.adminUsers).toEqual({
      method: "GET",
      path: "/api/v1/users",
      queryParameters: ["active", "limit", "offset", "search"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.adminUserById).toEqual({
      method: "GET",
      path: "/api/v1/users/{id}",
      pathParameters: ["id"],
      service: "backend"
    });
    expect(ALLOWED_OPERATIONS.adminUserRoles.path).toBe(
      "/api/v1/users/{id}/roles"
    );
    expect(ALLOWED_OPERATIONS.adminUserExternalIdentities.path).toBe(
      "/api/v1/users/{id}/external-identities"
    );
    expect(ALLOWED_OPERATIONS.adminFarmAssignments.path).toBe(
      "/api/v1/farm-assignments"
    );
    expect(ALLOWED_OPERATIONS.adminWarehouseAssignments.path).toBe(
      "/api/v1/warehouse-assignments"
    );
    expect(ALLOWED_OPERATIONS.adminAuditEvents.path).toBe(
      "/api/v1/audit-events"
    );
    expect(Object.values(ALLOWED_OPERATIONS).map(({ path }) => path)).not.toContain(
      expect.stringContaining("/api/v1/admin")
    );
  });

  it("keeps POST operations in a separate exact allowlist", () => {
    expect(ALLOWED_MUTATIONS).toEqual({
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
    });
    for (const mutation of Object.values(ALLOWED_MUTATIONS)) {
      expect(mutation.method).toBe("POST");
      expect(mutation.path).toMatch(
        /^\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\})(?:\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\}))*$/
      );
    }
  });

  it("keeps the assistant in a dedicated analytics POST allowlist", () => {
    expect(ALLOWED_ANALYTICS_COMMANDS).toEqual({
      analyticsAssistantQuery: {
        method: "POST",
        path: "/internal/v1/assistant/query",
        service: "analytics"
      }
    });
    expect(resolveAllowedAnalyticsCommand("analyticsAssistantQuery")).toEqual(
      ALLOWED_ANALYTICS_COMMANDS.analyticsAssistantQuery
    );
    expect(() => resolveAllowedMutation("analyticsAssistantQuery")).toThrow(
      "not allowlisted"
    );
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
    expect(() => resolveAllowedAnalyticsCommand("activityLogAppend")).toThrow(
      "not allowlisted"
    );
  });
});
