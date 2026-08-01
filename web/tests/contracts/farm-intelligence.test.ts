import { beforeEach, describe, expect, it, vi } from "vitest";

import { loadFarmDetailViewModel, loadFarmListViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import { loadOverviewViewModel } from "@/features/overview/load-overview-view-model";
import { parseOverviewFilters } from "@/features/overview/overview-filter-schema";
import { getAnalyticsPayload } from "@/server/clients/analytics";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

vi.mock("@/server/clients/analytics", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/server/clients/analytics")>();
  return { ...original, getAnalyticsPayload: vi.fn() };
});

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/server/bff/upstream-client")>();
  return { ...original, executeAllowedOperation: vi.fn() };
});

const operationalFarm = {
  id: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
  code: "FARM-001",
  displayName: "Nông trại An Phú",
  active: true,
  version: 4
};

const analyticsFarm = {
  farmCode: "FARM-001",
  farmName: "Tên từ Gold không thay thế Spring",
  cultivatedAreaHa: 42,
  harvestedAreaHa: 40,
  harvestQuantityKg: 120_000,
  yieldKgPerHa: 3_000,
  totalRevenueVnd: 2_000_000_000,
  totalCostVnd: 1_250_000_000,
  profitVnd: 750_000_000,
  profitMarginPct: 37.5,
  costVndPerHa: 29_761_904
};

const metadata = {
  scope: { tenantId: "hidden-upstream-only", tenantWide: true },
  freshness: { artifactAgeHours: 2, dataStatus: "current", maxAgeHours: 24 },
  lineage: {
    asOf: "2026-07-18",
    contractVersion: "1.0.0",
    generatedAt: "2026-07-18T01:00:00Z",
    manifestFingerprint: "a".repeat(64),
    runId: "run-1"
  }
} as const;

const farmEnvelope = {
  ...metadata,
  payload: {
    items: [analyticsFarm],
    cropProfitability: [],
    page: { hasMore: false, limit: 100, offset: 0, total: 1 }
  }
};

const overviewEnvelope = {
  ...metadata,
  payload: {
    insights: [],
    monthlyTrend: [],
    summary: {
      activeSeasons: 2,
      cropHealthRiskAlerts: 1,
      cultivatedAreaHa: 42,
      harvestQuantityKg: 120_000,
      inventoryRiskAlerts: 0,
      profitMarginPct: 37.5,
      profitVnd: 750_000_000,
      riskAlerts: 1,
      seasonRiskAlerts: 0,
      totalCostVnd: 1_250_000_000,
      totalRevenueVnd: 2_000_000_000
    },
    topRisks: []
  }
};

const yieldForecastEnvelope = {
  freshness: metadata.freshness,
  lineage: metadata.lineage,
  payload: {
    forecastHealth: { ready: 1, insufficientHistory: 0, total: 1 },
    items: [{
      asOfDate: "2026-07-18",
      farmCode: "FARM-001",
      fieldCode: "FIELD-001",
      seasonCode: "SEASON-2026-001",
      cropCode: "COFFEE",
      modelVersion: "crop-median-yield-per-ha-v1",
      forecastStatus: "ready",
      forecastOriginDate: "2026-03-01",
      expectedHarvestDate: "2026-10-01",
      seasonAreaHa: 12.5,
      targetYieldKg: 42_000,
      historyStartAt: "2021-03-01T00:00:00Z",
      historyEndAt: "2025-11-01T00:00:00Z",
      historySeasons: 5,
      backtestOrigins: 2,
      backtestSeasons: 3,
      forecastYieldKgPerHa: 3_100,
      observedMinYieldKgPerHa: 2_800,
      observedMaxYieldKgPerHa: 3_400,
      forecastQuantityKg: 38_750,
      observedMinQuantityKg: 35_000,
      observedMaxQuantityKg: 42_500,
      backtestMaeKgPerHa: 125,
      backtestWapePct: 4.2
    }],
    page: { hasMore: true, limit: 50, offset: 50, total: 101 }
  },
  scope: {
    appliedFilter: null,
    farmCodes: ["FARM-001"],
    tenantId: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
    tenantWide: false,
    warehouseCodes: []
  }
};

describe("overview and farm loaders", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(getAnalyticsPayload).mockReset();
    vi.mocked(executeAllowedOperation).mockResolvedValue(
      Response.json({
        items: [operationalFarm],
        hasMore: false,
        limit: 100,
        offset: 0
      })
    );
  });

  it("keeps overview usable when Gold fails", async () => {
    vi.mocked(getAnalyticsPayload).mockRejectedValue(new Error("analytics down"));
    const result = await loadOverviewViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-1",
      filters: parseOverviewFilters({})
    });
    expect(result.partial).toBe(true);
    expect(result.farms.status).toBe("ready");
    expect(result.analytics).toMatchObject({
      status: "failed",
      correlationId: "correlation-1"
    });
  });

  it("merges farm analytics strictly by canonical code", async () => {
    vi.mocked(getAnalyticsPayload).mockResolvedValue(farmEnvelope as never);
    const result = await loadFarmListViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-2",
      filters: parseOverviewFilters({})
    });
    expect(result.farms.status).toBe("ready");
    if (result.farms.status !== "ready") throw new Error("Expected ready farms");
    expect(result.farms.data[0].farm.displayName).toBe("Nông trại An Phú");
    expect(result.farms.data[0].analytics?.profitVnd).toBe(750_000_000);
  });

  it("keeps verified farm rows when analytics is unavailable", async () => {
    vi.mocked(getAnalyticsPayload).mockRejectedValue(new Error("analytics down"));
    const result = await loadFarmListViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-spring-only",
      filters: parseOverviewFilters({})
    });
    expect(result.partial).toBe(true);
    expect(result.analyticsMetadata).toBeNull();
    expect(result.farms.status).toBe("ready");
    if (result.farms.status !== "ready") throw new Error("Expected ready farms");
    expect(result.farms.data[0]).toMatchObject({
      farm: operationalFarm,
      analytics: null
    });
  });

  it("never renders analytics rows when the scoped farm catalog fails", async () => {
    vi.mocked(executeAllowedOperation).mockRejectedValue(new Error("backend down"));
    vi.mocked(getAnalyticsPayload).mockResolvedValue(farmEnvelope as never);
    const result = await loadFarmListViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-gold-only",
      filters: parseOverviewFilters({})
    });
    expect(result.partial).toBe(true);
    expect(result.analyticsMetadata).toBeNull();
    expect(result.farms).toMatchObject({
      status: "failed",
      correlationId: "correlation-gold-only"
    });
  });

  it("applies profit ordering after the canonical join", async () => {
    const secondFarm = {
      ...operationalFarm,
      id: "41d9e7a6-ad44-47a8-8c63-a7bac7d60e6a",
      code: "FARM-002",
      displayName: "Nông trại Bình Minh"
    };
    vi.mocked(executeAllowedOperation).mockResolvedValue(
      Response.json({
        items: [operationalFarm, secondFarm],
        hasMore: false,
        limit: 100,
        offset: 0
      })
    );
    vi.mocked(getAnalyticsPayload).mockResolvedValue({
      ...farmEnvelope,
      payload: {
        ...farmEnvelope.payload,
        items: [
          analyticsFarm,
          {
            ...analyticsFarm,
            farmCode: "FARM-002",
            farmName: "Không dùng tên này",
            profitVnd: 950_000_000
          }
        ],
        page: { hasMore: false, limit: 100, offset: 0, total: 2 }
      }
    } as never);
    const result = await loadFarmListViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-profit-sort",
      filters: parseOverviewFilters({ sort: "profit_desc" })
    });
    if (result.farms.status !== "ready") throw new Error("Expected ready farms");
    expect(result.farms.data.map((item) => item.farm.code)).toEqual([
      "FARM-002",
      "FARM-001"
    ]);
  });

  it("resolves UUID before sending a canonical farm code to analytics", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(operationalFarm)
    );
    vi.mocked(getAnalyticsPayload)
      .mockResolvedValueOnce(farmEnvelope as never)
      .mockResolvedValueOnce(yieldForecastEnvelope as never);
    const result = await loadFarmDetailViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-3",
      farmId: operationalFarm.id,
      filters: parseOverviewFilters({}),
      forecastOffset: 50
    });
    expect(result.farm.code).toBe("FARM-001");
    expect(vi.mocked(getAnalyticsPayload)).toHaveBeenNthCalledWith(
      1,
      expect.anything(),
      "analyticsFarms",
      "server-token",
      "correlation-3",
      expect.objectContaining({ farm_code: "FARM-001" })
    );
    expect(vi.mocked(getAnalyticsPayload)).toHaveBeenNthCalledWith(
      2,
      expect.anything(),
      "analyticsYieldForecast",
      "server-token",
      "correlation-3",
      { farm_code: "FARM-001", limit: 50, offset: 50 }
    );
    expect(result.forecast).toMatchObject({ status: "ready" });
  });

  it("keeps farm identity and realized analytics when forecast evidence fails", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json(operationalFarm)
    );
    vi.mocked(getAnalyticsPayload)
      .mockResolvedValueOnce(farmEnvelope as never)
      .mockRejectedValueOnce(new Error("yield forecast unavailable"));

    const result = await loadFarmDetailViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-yield-partial",
      farmId: operationalFarm.id,
      filters: parseOverviewFilters({}),
      forecastOffset: 0
    });

    expect(result.farm).toMatchObject({ code: "FARM-001" });
    expect(result.analytics).toMatchObject({ status: "ready" });
    expect(result.forecast).toMatchObject({
      status: "failed",
      correlationId: "correlation-yield-partial"
    });
  });

  it("sends resolved field, crop, and season codes to scoped forecast reads", async () => {
    const ids = {
      crop: "d9c12487-3eb9-4f41-a476-f51be3e48be7",
      field: "2d53de92-86f5-4bba-9726-e59c42d0ae24",
      season: "c4984351-3528-41d9-97a0-60c67e43e24a"
    };
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        const masters = {
          cropById: {
            active: true,
            code: "COFFEE",
            displayName: "Cà phê",
            id: ids.crop,
            version: 3
          },
          farmById: operationalFarm,
          fieldById: {
            active: true,
            code: "FIELD-001",
            displayName: "Khu vực 1",
            farmId: operationalFarm.id,
            id: ids.field,
            version: 2
          },
          seasonById: {
            code: "SEASON-2026-001",
            cropId: ids.crop,
            displayName: "Mùa cà phê 2026",
            farmId: operationalFarm.id,
            fieldId: ids.field,
            id: ids.season,
            status: "ACTIVE",
            version: 2
          }
        } as const;
        return Response.json(masters[operation as keyof typeof masters]);
      }
    );
    vi.mocked(getAnalyticsPayload)
      .mockResolvedValueOnce(farmEnvelope as never)
      .mockResolvedValueOnce(yieldForecastEnvelope as never);

    await loadFarmDetailViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-yield-scope",
      farmId: operationalFarm.id,
      filters: parseOverviewFilters({
        cropId: ids.crop,
        fieldId: ids.field,
        seasonId: ids.season
      }),
      forecastOffset: 0
    });

    expect(vi.mocked(getAnalyticsPayload)).toHaveBeenNthCalledWith(
      2,
      expect.anything(),
      "analyticsYieldForecast",
      "server-token",
      "correlation-yield-scope",
      {
        crop_code: "COFFEE",
        farm_code: "FARM-001",
        field_code: "FIELD-001",
        limit: 50,
        offset: 0,
        season_code: "SEASON-2026-001"
      }
    );
  });

  it("loads the tenant overview without exposing browser-side aggregation inputs", async () => {
    vi.mocked(getAnalyticsPayload).mockResolvedValue(overviewEnvelope as never);
    const result = await loadOverviewViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-4",
      filters: parseOverviewFilters({})
    });
    expect(result.partial).toBe(false);
    expect(result.analytics.status).toBe("ready");
  });

  it("sends resolved crop code and date preset to overview analytics", async () => {
    const cropId = "d9c12487-3eb9-4f41-a476-f51be3e48be7";
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        if (operation === "cropById") {
          return Response.json({
            id: cropId,
            code: "COFFEE",
            displayName: "Cà phê",
            active: true,
            version: 3
          });
        }
        return Response.json({
          items: [operationalFarm],
          hasMore: false,
          limit: 100,
          offset: 0
        });
      }
    );
    vi.mocked(getAnalyticsPayload).mockResolvedValue(overviewEnvelope as never);

    await loadOverviewViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-filtered-overview",
      filters: parseOverviewFilters({
        cropId,
        datePreset: "last-30-days"
      })
    });

    expect(vi.mocked(getAnalyticsPayload)).toHaveBeenCalledWith(
      expect.anything(),
      "analyticsOverview",
      "server-token",
      "correlation-filtered-overview",
      {
        crop_code: "COFFEE",
        date_preset: "last-30-days"
      }
    );
  });
});
