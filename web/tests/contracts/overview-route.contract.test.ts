import { describe, expect, it } from "vitest";

import {
  assertCurrentAnalyticsFilterSupport,
  parseOverviewFilters,
  toFilterQuery,
  UnsupportedAnalyticsFilterError
} from "@/features/overview/overview-filter-schema";
import {
  mergeFarmAnalyticsByCode,
  resolveFarmCode,
  ScopeResolutionError
} from "@/features/overview/resolve-analytics-codes";
import { allowlistedReturnPath } from "@/server/auth/request-policy";

const farms = [
  {
    id: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
    code: "FARM-001",
    displayName: "Nông trại An Phú",
    active: true,
    version: 4
  },
  {
    id: "99f61bb0-6295-40f0-a3a2-0443956b7d6b",
    code: "FARM-002",
    displayName: "Nông trại Bình Minh",
    active: false,
    version: 2
  }
] as const;

describe("overview and farm route contracts", () => {
  it("parses only canonical URL filters and preserves stable links", () => {
    const filters = parseOverviewFilters({
      farmId: farms[0].id,
      status: "all",
      sort: "profit_desc",
      search: " An Phú "
    });
    expect(filters.search).toBe("An Phú");
    expect(toFilterQuery(filters).toString()).toBe(
      `farmId=${farms[0].id}&search=An+Ph%C3%BA&status=all&sort=profit_desc`
    );
    expect(toFilterQuery(filters, { page: 2 }).toString()).toBe(
      `farmId=${farms[0].id}&search=An+Ph%C3%BA&status=all&sort=profit_desc&page=2`
    );
    expect(() => parseOverviewFilters({ status: "deleted" })).toThrow();
    expect(() => parseOverviewFilters({ tenantId: farms[0].id })).toThrow();
  });

  it("fails closed when the current analytics contract cannot honor a filter", () => {
    const filters = parseOverviewFilters({
      cropId: "d9c12487-3eb9-4f41-a476-f51be3e48be7"
    });
    expect(() => assertCurrentAnalyticsFilterSupport(filters)).toThrow(
      UnsupportedAnalyticsFilterError
    );
  });

  it("resolves a scoped UUID to its Spring canonical code", () => {
    expect(resolveFarmCode(farms, farms[0].id).code).toBe("FARM-001");
    expect(() => resolveFarmCode(farms, farms[1].id)).toThrow(ScopeResolutionError);
    expect(() =>
      resolveFarmCode(farms, "87c1359d-e184-4cb3-98c9-afeb888663ae")
    ).toThrow(ScopeResolutionError);
  });

  it("joins analytics on canonical code and never on UUID or tenantId", () => {
    const merged = mergeFarmAnalyticsByCode(farms, [
      { farmCode: "FARM-001", profitVnd: 125_000_000 },
      { farmCode: "OUT-OF-SCOPE", profitVnd: 1 }
    ]);
    expect(merged[0].analytics?.profitVnd).toBe(125_000_000);
    expect(merged[1].analytics).toBeNull();
    expect(JSON.stringify(merged)).not.toContain("tenantId");
    expect(JSON.stringify(merged)).not.toContain("OUT-OF-SCOPE");
  });

  it("preserves only scoped farm-detail return paths", () => {
    expect(allowlistedReturnPath(`/farms/${farms[0].id}?status=all`)).toBe(
      `/farms/${farms[0].id}?status=all`
    );
    expect(allowlistedReturnPath("/farms/not-a-uuid")).toBe("/overview");
    expect(allowlistedReturnPath("//evil.example/farms/" + farms[0].id)).toBe(
      "/overview"
    );
  });
});
