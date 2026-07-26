import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  getInventoryTransactionPage,
  getMaterialCatalog,
  getStockBalancePage,
  getStockLotPage,
  getSupplierCatalog,
  getVisibleWarehouses
} from "@/features/inventory/inventory-generated-client-adapter";
import { loadInventoryViewModel } from "@/features/inventory/load-inventory-view-model";
import {
  inventoryHref,
  parseInventoryRouteState
} from "@/features/inventory/inventory-route-state";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedOperation: vi.fn() };
});

const warehouseId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
const otherWarehouseId = "41d9e7a6-ad44-47a8-8c63-a7bac7d60e6a";
const materialId = "4fc03f21-71ee-46dc-a271-8d67ad4369c5";
const supplierId = "5ad14032-82ff-47ed-b382-9e78be547ad6";
const context = {
  env: {} as never,
  accessToken: "server-token",
  correlationId: "correlation-inventory"
};

const warehouse = {
  id: warehouseId,
  code: "WH-01",
  displayName: "Kho trung tâm",
  locationText: "Khu A",
  active: true,
  version: 3
} as const;

const stockBalance = {
  id: "66050634-6a22-45c6-a896-5a83602caf45",
  warehouseId,
  warehouseCode: "WH-01",
  materialId,
  materialCode: "FERT-001",
  materialName: "Phân NPK",
  unit: "KG",
  quantityOnHand: 120.5,
  inventoryValueVnd: 1_500_000,
  minimumStockQuantity: 50,
  lowStock: false,
  version: 2
} as const;

const stockLot = {
  id: "7935a09b-1f9f-4d08-a58a-a45bdd4e449d",
  warehouseId,
  warehouseCode: "WH-01",
  materialId,
  materialCode: "FERT-001",
  materialName: "Phân NPK",
  supplierId,
  supplierCode: "SUP-01",
  originalReceiptId: "80095c25-b595-4691-b98b-ce84fa3e2bfd",
  batchCode: "BATCH-01",
  expiryDate: "2027-12-31",
  receivedAt: "2027-01-01T08:00:00Z",
  unit: "KG",
  receivedQuantity: 100,
  availableQuantity: 80,
  unitCostVnd: 12_000,
  expired: false,
  expiringSoon: false,
  version: 1
} as const;

const inventoryTransaction = {
  id: "9b2bd575-194d-42c9-9f72-2038ad623c7a",
  warehouseId,
  materialId,
  kind: "RECEIPT",
  unit: "KG",
  quantityBase: 100,
  signedQuantityEffect: 100,
  procurementEffectVnd: 1_200_000,
  occurredAt: "2027-01-01T08:00:00Z",
  recordedByProfileId: "a4b5d235-1d78-49ea-924f-a2f865c73238",
  version: 0
} as const;

const material = {
  id: materialId,
  code: "FERT-001",
  displayName: "Phân NPK",
  baseUnit: "KG",
  minimumStockQuantity: 50,
  active: true,
  version: 1
} as const;

const supplier = {
  id: supplierId,
  code: "SUP-01",
  displayName: "Công ty Phân Bón ABC",
  active: true,
  version: 1
} as const;

const analyticsEnvelope = {
  freshness: {
    artifactAgeHours: 1,
    dataStatus: "current",
    maxAgeHours: 24
  },
  lineage: {
    asOf: "2027-01-01",
    contractVersion: "1.0.0",
    generatedAt: "2027-01-01T00:00:00Z",
    manifestFingerprint: "manifest-fingerprint",
    runId: "run-1"
  },
  payload: {
    abc: [],
    alerts: [],
    items: [],
    page: { hasMore: false, limit: 50, offset: 0, total: 0 },
    summary: {
      averageDaysOfSupply: null,
      criticalAlerts: 0,
      expiring30dSkus: 0,
      lowStockSkus: 0,
      materialSkus: 0,
      overstockSkus: 0,
      skuLocations: 0,
      stockoutSkus: 0,
      totalInventoryValueVnd: 0
    }
  },
  scope: {
    tenantId: "b4b5d235-1d78-49ea-924f-a2f865c73238",
    tenantWide: false,
    warehouseCodes: ["WH-01"]
  }
};

function fixedPage<Item>(items: readonly Item[]) {
  return { items, limit: 100, offset: 0, hasMore: false };
}

function boundedPage<Item>(items: readonly Item[], offset: number, hasMore = false) {
  return { items, limit: 50, offset, hasMore };
}

describe("inventory generated-client adapter", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
  });

  it("requests the visible warehouse catalog with only allowlisted values", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(fixedPage([warehouse]))
    );
    await getVisibleWarehouses(context);
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      context.env,
      "warehouseCatalog",
      context.accessToken,
      context.correlationId,
      { active: true, limit: 100, offset: 0 }
    );
  });

  it("requests stock balances scoped to the warehouse with optional filters", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(boundedPage([stockBalance], 0))
    );
    await getStockBalancePage(context, warehouseId, 0, { lowStock: true, materialId });
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      context.env,
      "inventoryBalances",
      context.accessToken,
      context.correlationId,
      { limit: 50, lowStock: true, materialId, offset: 0, warehouseId }
    );
  });

  it("requests stock lots without emitting undefined filter keys", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(boundedPage([stockLot], 0))
    );
    await getStockLotPage(context, warehouseId, 0);
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      context.env,
      "inventoryLots",
      context.accessToken,
      context.correlationId,
      { limit: 50, offset: 0, warehouseId }
    );
  });

  it("requests inventory transactions with kind and instant range filters", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(boundedPage([inventoryTransaction], 0))
    );
    await getInventoryTransactionPage(context, warehouseId, 0, {
      kind: "RECEIPT",
      materialId,
      occurredFrom: "2027-01-01T00:00:00Z",
      occurredTo: "2027-01-31T23:59:59.999Z"
    });
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      context.env,
      "inventoryTransactions",
      context.accessToken,
      context.correlationId,
      {
        kind: "RECEIPT",
        limit: 50,
        materialId,
        occurredFrom: "2027-01-01T00:00:00Z",
        occurredTo: "2027-01-31T23:59:59.999Z",
        offset: 0,
        warehouseId
      }
    );
  });

  it("requests supplier and material catalogs scoped to active records", async () => {
    vi.mocked(executeAllowedOperation)
      .mockResolvedValueOnce(Response.json(fixedPage([supplier])))
      .mockResolvedValueOnce(Response.json(fixedPage([material])));
    await getSupplierCatalog(context);
    await getMaterialCatalog(context);
    expect(executeAllowedOperation).toHaveBeenNthCalledWith(
      1,
      context.env,
      "supplierCatalog",
      context.accessToken,
      context.correlationId,
      { active: true, limit: 100, offset: 0 }
    );
    expect(executeAllowedOperation).toHaveBeenNthCalledWith(
      2,
      context.env,
      "materialCatalog",
      context.accessToken,
      context.correlationId,
      { active: true, limit: 100, offset: 0 }
    );
  });

  it("fails closed when the upstream echoes a mismatched page window", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(boundedPage([stockBalance], 50))
    );
    await expect(
      getStockBalancePage(context, warehouseId, 0)
    ).rejects.toThrow("Không thể tải dữ liệu kho.");
  });
});

describe("inventory route state", () => {
  it("accepts a fully scoped warehouse selection and builds a canonical href", () => {
    const state = parseInventoryRouteState({
      warehouseId,
      materialId,
      lowStock: "true",
      kind: "RECEIPT",
      from: "2027-01-01",
      to: "2027-01-31",
      balanceOffset: "50",
      lotOffset: "0",
      txOffset: "100"
    });
    expect(state).not.toBeNull();
    expect(state && inventoryHref(state)).toBe(
      `/inventory?warehouseId=${warehouseId}&materialId=${materialId}`
      + "&lowStock=true&kind=RECEIPT&from=2027-01-01&to=2027-01-31"
      + "&balanceOffset=50&txOffset=100"
    );
  });

  it("returns null for a malformed warehouseId", () => {
    expect(parseInventoryRouteState({ warehouseId: "not-a-uuid" })).toBeNull();
  });

  it("returns null when a dependent filter is present without a warehouse", () => {
    expect(parseInventoryRouteState({ materialId })).toBeNull();
    expect(parseInventoryRouteState({ lowStock: "true" })).toBeNull();
    expect(parseInventoryRouteState({ balanceOffset: "50" })).toBeNull();
  });

  it("returns null for offsets that are not bounded multiples of 50", () => {
    expect(
      parseInventoryRouteState({ warehouseId, balanceOffset: "49" })
    ).toBeNull();
    expect(
      parseInventoryRouteState({ warehouseId, lotOffset: "10050" })
    ).toBeNull();
  });

  it("returns null when the from date is after the to date", () => {
    expect(
      parseInventoryRouteState({ warehouseId, from: "2027-02-01", to: "2027-01-01" })
    ).toBeNull();
  });

  it("returns null for a lowStock value other than the literal true", () => {
    expect(parseInventoryRouteState({ warehouseId, lowStock: "1" })).toBeNull();
  });

  it("drops dependent params from the href when the warehouse is absent", () => {
    const state = parseInventoryRouteState({});
    expect(state).not.toBeNull();
    expect(state && inventoryHref(state)).toBe("/inventory");
  });
});

describe("inventory view-model loader", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
  });

  it("returns a picker state without a selected warehouse", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({})!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-picker",
      state,
      canManage: false
    });
    expect(result).toEqual({ kind: "picker", warehouses: [warehouse] });
    expect(executeAllowedOperation).toHaveBeenCalledTimes(1);
  });

  it("rejects a warehouse outside the server-visible set before any data fetch", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId: otherWarehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-foreign",
      state,
      canManage: false
    });
    expect(result).toEqual({ kind: "foreign_warehouse" });
    expect(executeAllowedOperation).toHaveBeenCalledTimes(1);
  });

  it("degrades analytics independently while keeping operational sections ready", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return new Response(null, { status: 403 });
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-analytics",
      state,
      canManage: false
    });
    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(result.partial).toBe(true);
    expect(result.analytics).toMatchObject({
      status: "failed",
      message: "Không thể tải phân tích Gold cho kho này."
    });
    expect(result.balances).toMatchObject({
      status: "ready",
      data: { items: [stockBalance] }
    });
  });

  it("preserves the server-provided lot order verbatim", async () => {
    const lotA = { ...stockLot, id: "a4b5d235-1d78-49ea-924f-a2f865c73238" };
    const lotB = { ...stockLot, id: "b4b5d235-1d78-49ea-924f-a2f865c73238" };
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([lotB, lotA], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return Response.json(analyticsEnvelope);
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-order",
      state,
      canManage: false
    });
    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(result.lots.status).toBe("ready");
    if (result.lots.status !== "ready") throw new Error("Expected lots");
    expect(result.lots.data.items.map((lot) => lot.id)).toEqual([
      lotB.id,
      lotA.id
    ]);
  });

  it("maps from/to route filters to exact occurredFrom/occurredTo instants", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return Response.json(analyticsEnvelope);
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({
      warehouseId,
      from: "2027-01-01",
      to: "2027-01-31"
    })!;
    await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-dates",
      state,
      canManage: false
    });
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      expect.anything(),
      "inventoryTransactions",
      "server-token",
      "correlation-dates",
      expect.objectContaining({
        occurredFrom: "2027-01-01T00:00:00Z",
        occurredTo: "2027-01-31T23:59:59.999Z"
      })
    );
  });

  it("degrades one operational section without hiding the others", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return new Response(null, { status: 403 });
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return Response.json(analyticsEnvelope);
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-fail-closed",
      state,
      canManage: false
    });
    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(result.balances.status).toBe("failed");
    expect(result.lots.status).toBe("ready");
    expect(result.transactions.status).toBe("ready");
    expect(result.partial).toBe(true);
  });

  it("loads supplier and material masters only when the caller can manage inventory", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return Response.json(analyticsEnvelope);
      if (operation === "supplierCatalog") return Response.json(fixedPage([supplier]));
      if (operation === "materialCatalog") return Response.json(fixedPage([material]));
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-masters",
      state,
      canManage: true
    });
    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(result.mastersAvailable).toBe(true);
    expect(result.masters).toMatchObject({
      status: "ready",
      data: { suppliers: [supplier], materials: [material] }
    });
    expect(result.partial).toBe(false);
  });

  it("disables management forms when master catalogs fail without flagging analytics as partial", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") return Response.json(analyticsEnvelope);
      if (operation === "supplierCatalog") return new Response(null, { status: 403 });
      if (operation === "materialCatalog") return Response.json(fixedPage([material]));
      throw new Error(`Unexpected operation: ${operation}`);
    });
    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-masters-denied",
      state,
      canManage: true
    });
    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(result.mastersAvailable).toBe(false);
    expect(result.masters).toMatchObject({ status: "failed" });
    expect(result.partial).toBe(false);
  });
});
