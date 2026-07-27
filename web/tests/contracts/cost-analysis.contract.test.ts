import { describe, expect, it } from "vitest";

import {
  costAnalysisHref,
  parseCostFilterState,
  resolveCostDateRange
} from "@/features/costs/cost-filter-schema";

const FARM_ID = "41000000-0000-0000-0000-000000000001";
const SEASON_ID = "41000000-0000-0000-0000-000000000006";
const ACTIVITY_ID = "41000000-0000-0000-0000-000000000009";

describe("cost analysis route contract", () => {
  it.each(["operating", "procurement"] as const)(
    "accepts the %s lens",
    (lens) => {
      expect(parseCostFilterState({ lens })).toEqual({
        lens,
        filters: {}
      });
    }
  );

  it.each([
    {},
    { lens: "inventory" },
    { lens: "" },
    { lens: ["operating", "procurement"] },
    { lens: "operating", categoryId: "legacy-category" },
    { lens: "operating", unexpected: "value" }
  ])("rejects an unsafe or non-canonical route shape %#", (input) => {
    expect(parseCostFilterState(input)).toBeNull();
  });

  it("normalizes blank optional values to absent", () => {
    expect(
      parseCostFilterState({
        lens: "operating",
        from: " ",
        to: "",
        farmId: " "
      })
    ).toEqual({ lens: "operating", filters: {} });
  });

  it("accepts the complete operating filter set", () => {
    expect(
      parseCostFilterState({
        lens: "operating",
        from: "2026-01-01",
        to: "2026-12-31",
        farmId: FARM_ID,
        seasonId: SEASON_ID,
        activityId: ACTIVITY_ID,
        category: "LABOR"
      })
    ).toEqual({
      lens: "operating",
      filters: {
        from: "2026-01-01",
        to: "2026-12-31",
        farmId: FARM_ID,
        seasonId: SEASON_ID,
        activityId: ACTIVITY_ID,
        category: "LABOR"
      }
    });
  });

  it.each([
    { lens: "procurement", seasonId: SEASON_ID },
    { lens: "procurement", activityId: ACTIVITY_ID },
    { lens: "procurement", category: "LABOR" }
  ])("rejects operating-only filters from procurement %#", (input) => {
    expect(parseCostFilterState(input)).toBeNull();
  });

  it.each([
    { lens: "operating", from: "2026-02-30" },
    { lens: "operating", from: "2026-12-31", to: "2026-01-01" },
    { lens: "operating", from: "2025-01-01", to: "2026-01-02" },
    { lens: "operating", farmId: "farm-code-is-not-a-uuid" },
    { lens: "operating", category: "PROCUREMENT" }
  ])("rejects invalid operating filter values %#", (input) => {
    expect(parseCostFilterState(input)).toBeNull();
  });

  it("resolves missing dates to a bounded trailing-year UTC range", () => {
    expect(
      resolveCostDateRange(
        {},
        new Date("2026-07-27T12:00:00Z")
      )
    ).toEqual({ from: "2025-07-27", to: "2026-07-27" });
  });

  it("builds deterministic lens-safe hrefs", () => {
    expect(
      costAnalysisHref({
        lens: "operating",
        filters: {
          from: "2026-01-01",
          to: "2026-12-31",
          farmId: FARM_ID,
          category: "MATERIAL"
        }
      })
    ).toBe(
      `/costs?lens=operating&from=2026-01-01&to=2026-12-31&farmId=${FARM_ID}&category=MATERIAL`
    );
    expect(
      costAnalysisHref({ lens: "procurement", filters: {} })
    ).toBe("/costs?lens=procurement");
  });
});
