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
    vi.mocked(getAnalyticsPayload).mockResolvedValue(farmEnvelope as never);
    const result = await loadFarmDetailViewModel({
      env: {} as never,
      accessToken: "server-token",
      correlationId: "correlation-3",
      farmId: operationalFarm.id
    });
    expect(result.farm.code).toBe("FARM-001");
    expect(vi.mocked(getAnalyticsPayload)).toHaveBeenCalledWith(
      expect.anything(),
      "analyticsFarms",
      "server-token",
      "correlation-3",
      expect.objectContaining({ farm_code: "FARM-001" })
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
});
