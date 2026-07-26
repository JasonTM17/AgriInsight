import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  resolveOperationalAnalyticsMasters,
  toAnalyticsFilterQuery
} from "@/features/overview/load-operational-analytics-masters";
import { parseOverviewFilters } from "@/features/overview/overview-filter-schema";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

vi.mock("@/server/bff/upstream-client", async (importOriginal) => {
  const original = await importOriginal<
    typeof import("@/server/bff/upstream-client")
  >();
  return { ...original, executeAllowedOperation: vi.fn() };
});

const ids = {
  farm: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
  field: "2d53de92-86f5-4bba-9726-e59c42d0ae24",
  crop: "d9c12487-3eb9-4f41-a476-f51be3e48be7",
  season: "c4984351-3528-41d9-97a0-60c67e43e24a"
};
const context = {
  env: {} as never,
  accessToken: "server-token",
  correlationId: "correlation-master"
};

describe("operational analytics master resolution", () => {
  beforeEach(() => {
    vi.mocked(executeAllowedOperation).mockReset();
    vi.mocked(executeAllowedOperation).mockImplementation(
      async (_env, operation) => {
        const payloads = {
          seasonById: {
            id: ids.season,
            farmId: ids.farm,
            fieldId: ids.field,
            cropId: ids.crop,
            code: "SEASON-2025-0001",
            displayName: "Mùa cà phê 2025",
            status: "COMPLETED",
            version: 2
          },
          fieldById: {
            id: ids.field,
            farmId: ids.farm,
            code: "FIELD-0001",
            displayName: "Khu vực 1.1",
            active: true,
            version: 2
          },
          farmById: {
            id: ids.farm,
            code: "FARM-001",
            displayName: "Nông trại An Phú",
            active: true,
            version: 4
          },
          cropById: {
            id: ids.crop,
            code: "COFFEE",
            displayName: "Cà phê",
            active: true,
            version: 3
          }
        } as const;
        return Response.json(payloads[operation as keyof typeof payloads]);
      }
    );
  });

  it("derives and verifies every parent from one season UUID", async () => {
    const filters = parseOverviewFilters({
      seasonId: ids.season,
      datePreset: "season-to-date"
    });
    const resolved = await resolveOperationalAnalyticsMasters(context, filters);
    expect(resolved).toMatchObject({
      farmCode: "FARM-001",
      fieldCode: "FIELD-0001",
      cropCode: "COFFEE",
      seasonCode: "SEASON-2025-0001"
    });
    expect(toAnalyticsFilterQuery(filters, resolved)).toEqual({
      farm_code: "FARM-001",
      field_code: "FIELD-0001",
      crop_code: "COFFEE",
      season_code: "SEASON-2025-0001",
      date_preset: "season-to-date"
    });
  });

  it("rejects an explicit parent that conflicts with the season", async () => {
    await expect(
      resolveOperationalAnalyticsMasters(
        context,
        parseOverviewFilters({
          farmId: "41d9e7a6-ad44-47a8-8c63-a7bac7d60e6a",
          seasonId: ids.season
        })
      )
    ).rejects.toMatchObject({
      reason: "conflict"
    });
  });

  it("rejects inactive masters before analytics access", async () => {
    vi.mocked(executeAllowedOperation).mockResolvedValueOnce(
      Response.json({
        id: ids.crop,
        code: "COFFEE",
        displayName: "Cà phê",
        active: false,
        version: 3
      })
    );
    await expect(
      resolveOperationalAnalyticsMasters(
        context,
        parseOverviewFilters({ cropId: ids.crop })
      )
    ).rejects.toMatchObject({
      reason: "inactive"
    });
  });
});
