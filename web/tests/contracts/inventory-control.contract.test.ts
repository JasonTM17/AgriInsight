import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { beforeEach, describe, expect, it, vi } from "vitest";

import { InventoryAnalyticsPanels } from "@/features/inventory/components/inventory-analytics-panels";
import {
  getInventoryTransactionPage,
  getMaterialCatalog,
  getStockBalancePage,
  getStockLotPage,
  getSupplierCatalog,
  getVisibleWarehouses
} from "@/features/inventory/inventory-generated-client-adapter";
import {
  inventoryAnalyticsEnvelopeSchema,
  parseScopedInventoryAnalytics
} from "@/features/inventory/inventory-analytics-contract-schema";
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

const forecastEvidence = {
  asOfDate: "2027-01-01",
  modelVersion: "mean-daily-usage-90d-v1",
  coverageStatus: "ready",
  historyStartDate: "2026-07-06",
  historyEndDate: "2027-01-01",
  historyDays: 180,
  nonzeroDemandDays: 43,
  horizonDays: 30,
  forecastQuantity: 19,
  lowerQuantity: 17,
  upperQuantity: 23,
  backtestWindows: 9,
  backtestMae: 2.5,
  backtestWapePct: 12.75,
  forecastDaysOfSupply: 6.5,
  forecastSuggestedOrderQuantity: 41
} as const;

const inventoryStatus = {
  abcClass: "A",
  averageDailyUsage: 2.37,
  averageUnitCostVnd: 12_000,
  baseUnit: "KG",
  category: "Fertilizer",
  daysOfSupply: 50.8,
  daysToExpiry: null,
  farmCode: "FARM-01",
  farmName: "Nông trại Trung tâm",
  forecast: forecastEvidence,
  inventoryValueVnd: 1_500_000,
  materialCode: "FERT-001",
  materialName: "Phân NPK",
  nearestExpiryDate: "2027-12-31",
  predicted30dNeed: 71,
  recommendedOrderQuantity: 81,
  reorderPoint: 50,
  stockQuantity: 120.5,
  stockStatus: "healthy",
  targetStockLevel: 200,
  warehouseCode: "WH-01",
  warehouseName: "Kho trung tâm"
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
    forecastHealth: {
      ready: 0,
      noDemand: 0,
      insufficientHistory: 0,
      unavailable: 0,
      total: 0
    },
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

  it("treats empty submitted filter fields as absent", () => {
    const state = parseInventoryRouteState({
      warehouseId,
      materialId: "",
      lowStock: "",
      kind: "",
      from: "",
      to: "",
      balanceOffset: "",
      lotOffset: "",
      txOffset: ""
    });
    expect(state).toEqual({
      warehouseId,
      balanceOffset: 0,
      lotOffset: 0,
      txOffset: 0,
      filters: {
        materialId: undefined,
        lowStock: undefined,
        kind: undefined,
        from: undefined,
        to: undefined
      }
    });
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

describe("inventory forecast analytics contract", () => {
  it("accepts only the exact nested forecast evidence and aggregate health shape", () => {
    const result = inventoryAnalyticsEnvelopeSchema.safeParse({
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        forecastHealth: {
          ready: 1,
          noDemand: 2,
          insufficientHistory: 3,
          unavailable: 4,
          total: 10
        },
        items: [inventoryStatus],
        page: { ...analyticsEnvelope.payload.page, total: 10_001 }
      }
    });

    expect(result.success).toBe(true);
    if (!result.success) throw result.error;
    expect(result.data.payload.items[0]?.forecast).toEqual(forecastEvidence);
    expect(result.data.payload.forecastHealth).toEqual({
      ready: 1,
      noDemand: 2,
      insufficientHistory: 3,
      unavailable: 4,
      total: 10
    });
  });

  it("rejects foreign, invalid, nonfinite, and extra forecast evidence", () => {
    const scopedValue = {
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        forecastHealth: { ready: 1, noDemand: 0, insufficientHistory: 0, unavailable: 0, total: 1 },
        items: [inventoryStatus]
      }
    };

    expect(() => parseScopedInventoryAnalytics({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        items: [{ ...inventoryStatus, warehouseCode: "WH-OTHER" }]
      }
    }, "WH-01")).toThrow("Inventory analytics scope mismatch");
    expect(inventoryAnalyticsEnvelopeSchema.safeParse({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        items: [{
          ...inventoryStatus,
          forecast: { ...forecastEvidence, coverageStatus: "unknown" }
        }]
      }
    }).success).toBe(false);
    expect(inventoryAnalyticsEnvelopeSchema.safeParse({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        items: [{
          ...inventoryStatus,
          forecast: { ...forecastEvidence, forecastQuantity: Number.POSITIVE_INFINITY }
        }]
      }
    }).success).toBe(false);
    expect(inventoryAnalyticsEnvelopeSchema.safeParse({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        items: [{
          ...inventoryStatus,
          forecast: { ...forecastEvidence, untrusted: true }
        }]
      }
    }).success).toBe(false);
    for (const forecast of [
      { ...forecastEvidence, historyDays: 0 },
      { ...forecastEvidence, nonzeroDemandDays: 181 },
      { ...forecastEvidence, horizonDays: 31 },
      { ...forecastEvidence, backtestWindows: 10 },
      { ...forecastEvidence, modelVersion: "m".repeat(65) },
      { ...forecastEvidence, coverageStatus: "unavailable", forecastQuantity: 1 }
    ]) {
      expect(inventoryAnalyticsEnvelopeSchema.safeParse({
        ...scopedValue,
        payload: {
          ...scopedValue.payload,
          items: [{ ...inventoryStatus, forecast }]
        }
      }).success).toBe(false);
    }
    expect(inventoryAnalyticsEnvelopeSchema.safeParse({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        items: Array.from({ length: 101 }, () => inventoryStatus)
      }
    }).success).toBe(false);
    expect(inventoryAnalyticsEnvelopeSchema.safeParse({
      ...scopedValue,
      payload: {
        ...scopedValue.payload,
        forecastHealth: {
          ready: 1,
          noDemand: 0,
          insufficientHistory: 0,
          unavailable: 0,
          total: 2
        }
      }
    }).success).toBe(false);
  });
});

describe("inventory analytics rendering", () => {
  it("renders dynamic ABC shares without CSP-blocked style attributes", () => {
    const data = inventoryAnalyticsEnvelopeSchema.parse({
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        abc: [{
          abcClass: "A",
          category: "Fertilizer",
          cumulativeValueSharePct: 42.3,
          inventoryValueVnd: 1_500_000,
          materialCode: "FERT-001",
          materialName: "Phân NPK",
          stockLocations: 1,
          valueSharePct: 42.3
        }]
      }
    });
    const markup = renderToStaticMarkup(createElement(
      InventoryAnalyticsPanels,
      {
        analytics: { status: "ready", data },
        hasOperationalFilters: false,
        selectedWarehouseCode: "WH-01"
      }
    ));

    expect(markup).toContain("<progress");
    expect(markup).toContain('max="100"');
    expect(markup).toContain('value="42.3"');
    expect(markup).not.toContain("style=");
  });

  it("renders server-provided forecast evidence separately from legacy stock policy", () => {
    const data = inventoryAnalyticsEnvelopeSchema.parse({
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        forecastHealth: { ready: 1, noDemand: 0, insufficientHistory: 0, unavailable: 0, total: 1 },
        items: [inventoryStatus]
      }
    });
    const markup = renderToStaticMarkup(createElement(
      InventoryAnalyticsPanels,
      {
        analytics: { status: "ready", data },
        hasOperationalFilters: false,
        selectedWarehouseCode: "WH-01"
      }
    ));

    expect(markup).toContain("Bằng chứng dự báo nhu cầu");
    expect(markup).toContain("Độ mới: Hiện hành");
    expect(markup).toContain("Dự báo điểm");
    expect(markup).toContain("19 KG");
    expect(markup).toContain("17 KG – 23 KG");
    expect(markup).toContain("41 KG");
    expect(markup).toContain("6,5 ngày");
    expect(markup).toContain("Nhu cầu 30 ngày theo chính sách");
    expect(markup).toContain("71 KG");
    expect(markup).toContain("Đề xuất nhập theo chính sách");
    expect(markup).toContain("81 KG");
    expect(markup).toContain("mean-daily-usage-90d-v1");
    expect(markup).toContain("Đánh giá ngược");
    expect(markup).toContain(
      'aria-label="Bảng bằng chứng dự báo có thể cuộn"'
    );
    expect(markup).toContain(
      'aria-label="Bảng chính sách tồn kho có thể cuộn"'
    );
    expect(markup.match(/role="region" tabindex="0"/g)).toHaveLength(2);
  });

  it("names insufficient history and preserves a semantic no-status state", () => {
    const noHistoryForecast = {
      asOfDate: "2027-01-01",
      modelVersion: "mean-daily-usage-90d-v1",
      coverageStatus: "insufficientHistory",
      historyStartDate: "2026-12-30",
      historyEndDate: "2027-01-01",
      historyDays: 3,
      nonzeroDemandDays: 1,
      horizonDays: null,
      forecastQuantity: null,
      lowerQuantity: null,
      upperQuantity: null,
      backtestWindows: null,
      backtestMae: null,
      backtestWapePct: null,
      forecastDaysOfSupply: null,
      forecastSuggestedOrderQuantity: null
    } as const;
    const evidenceData = inventoryAnalyticsEnvelopeSchema.parse({
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        forecastHealth: { ready: 0, noDemand: 0, insufficientHistory: 1, unavailable: 0, total: 1 },
        items: [{ ...inventoryStatus, forecast: noHistoryForecast }]
      }
    });
    const evidenceMarkup = renderToStaticMarkup(createElement(
      InventoryAnalyticsPanels,
      {
        analytics: { status: "ready", data: evidenceData },
        hasOperationalFilters: false,
        selectedWarehouseCode: "WH-01"
      }
    ));
    const emptyData = inventoryAnalyticsEnvelopeSchema.parse(analyticsEnvelope);
    const emptyMarkup = renderToStaticMarkup(createElement(
      InventoryAnalyticsPanels,
      {
        analytics: { status: "ready", data: emptyData },
        hasOperationalFilters: false,
        selectedWarehouseCode: "WH-01"
      }
    ));

    expect(evidenceMarkup).toContain("Thiếu lịch sử");
    expect(evidenceMarkup).toContain("Chưa đủ lịch sử để đưa dự báo; không tự nội suy.");
    expect(emptyMarkup).toContain('role="status"');
    expect(emptyMarkup).toContain("Không có dòng tình trạng SKU-location trong snapshot này.");
  });

  it("explains unavailable evidence and translates every forecast freshness status", () => {
    const unavailableForecast = {
      ...forecastEvidence,
      asOfDate: null,
      modelVersion: null,
      coverageStatus: "unavailable",
      historyStartDate: null,
      historyEndDate: null,
      historyDays: null,
      nonzeroDemandDays: null,
      horizonDays: null,
      forecastQuantity: null,
      lowerQuantity: null,
      upperQuantity: null,
      backtestWindows: null,
      backtestMae: null,
      backtestWapePct: null,
      forecastDaysOfSupply: null,
      forecastSuggestedOrderQuantity: null
    } as const;
    const unavailableData = inventoryAnalyticsEnvelopeSchema.parse({
      ...analyticsEnvelope,
      payload: {
        ...analyticsEnvelope.payload,
        forecastHealth: { ready: 0, noDemand: 0, insufficientHistory: 0, unavailable: 1, total: 1 },
        items: [{ ...inventoryStatus, forecast: unavailableForecast }]
      }
    });
    const unavailableMarkup = renderToStaticMarkup(createElement(
      InventoryAnalyticsPanels,
      {
        analytics: { status: "ready", data: unavailableData },
        hasOperationalFilters: false,
        selectedWarehouseCode: "WH-01"
      }
    ));

    expect(unavailableMarkup).toContain("Không có dự báo");
    expect(unavailableMarkup).toContain(
      "Máy chủ không cung cấp bằng chứng dự báo cho SKU-location này."
    );
    for (const [dataStatus, label] of Object.entries({
      current: "Hiện hành",
      stale: "Đã cũ",
      partial: "Một phần",
      missing: "Thiếu dữ liệu"
    })) {
      const data = inventoryAnalyticsEnvelopeSchema.parse({
        ...analyticsEnvelope,
        freshness: { ...analyticsEnvelope.freshness, dataStatus }
      });
      const markup = renderToStaticMarkup(createElement(
        InventoryAnalyticsPanels,
        {
          analytics: { status: "ready", data },
          hasOperationalFilters: false,
          selectedWarehouseCode: "WH-01"
        }
      ));

      expect(markup).toContain(`Độ mới: ${label}`);
    }
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

  it("keeps the analytics request warehouse-scoped and degrades an invalid forecast response", async () => {
    vi.mocked(executeAllowedOperation).mockImplementation(async (_env, operation) => {
      if (operation === "warehouseCatalog") return Response.json(fixedPage([warehouse]));
      if (operation === "inventoryBalances") return Response.json(boundedPage([stockBalance], 0));
      if (operation === "inventoryLots") return Response.json(boundedPage([stockLot], 0));
      if (operation === "inventoryTransactions") {
        return Response.json(boundedPage([inventoryTransaction], 0));
      }
      if (operation === "analyticsInventory") {
        return Response.json({
          ...analyticsEnvelope,
          payload: {
            ...analyticsEnvelope.payload,
            forecastHealth: { ready: 1, noDemand: 0, insufficientHistory: 0, unavailable: 0, total: 1 },
            items: [{
              ...inventoryStatus,
              forecast: { ...forecastEvidence, coverageStatus: "not-a-coverage-status" }
            }]
          }
        });
      }
      throw new Error(`Unexpected operation: ${operation}`);
    });

    const state = parseInventoryRouteState({ warehouseId })!;
    const result = await loadInventoryViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-invalid-forecast",
      state,
      canManage: false
    });

    if (result.kind !== "ready") throw new Error("Expected a ready inventory view");
    expect(executeAllowedOperation).toHaveBeenCalledWith(
      expect.anything(),
      "analyticsInventory",
      "server-token",
      "correlation-invalid-forecast",
      { limit: 50, offset: 0, warehouse_code: "WH-01" }
    );
    expect(result.analytics).toMatchObject({
      status: "failed",
      message: "Không thể tải phân tích Gold cho kho này."
    });
    expect(result.balances.status).toBe("ready");
    expect(result.lots.status).toBe("ready");
    expect(result.transactions.status).toBe("ready");
    expect(result.partial).toBe(true);
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
