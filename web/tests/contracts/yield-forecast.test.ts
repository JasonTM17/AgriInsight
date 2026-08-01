import { describe, expect, it } from "vitest";

import {
  parseScopedYieldForecast,
  yieldForecastEnvelopeSchema
} from "@/features/farms/yield-forecast-contract-schema";
import { formatDateTime } from "@/features/farms/yield-forecast-formatters";

const forecastItem = {
  asOfDate: "2026-07-30",
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
} as const;

const yieldForecastEnvelope = {
  freshness: {
    artifactAgeHours: 2,
    dataStatus: "current",
    maxAgeHours: 24
  },
  lineage: {
    asOf: "2026-07-30",
    contractVersion: "1.0.0",
    generatedAt: "2026-07-30T01:00:00Z",
    manifestFingerprint: "a".repeat(64),
    runId: "run-yield-forecast"
  },
  payload: {
    forecastHealth: {
      ready: 1,
      insufficientHistory: 0,
      total: 1
    },
    items: [forecastItem],
    page: {
      hasMore: false,
      limit: 100,
      offset: 0,
      total: 1
    }
  },
  scope: {
    appliedFilter: {
      cropCode: null,
      dateFrom: null,
      datePreset: "all",
      dateTo: "2026-07-30",
      farmCode: "FARM-001",
      fieldCode: null,
      seasonCode: null
    },
    farmCodes: ["FARM-001"],
    tenantId: "3eb92f10-60dd-45cb-9160-7c569c3258b4",
    tenantWide: false,
    warehouseCodes: []
  }
} as const;

describe("yield forecast analytics contract", () => {
  it("accepts the exact server evidence envelope", () => {
    const parsed = yieldForecastEnvelopeSchema.parse(yieldForecastEnvelope);

    expect(parsed.payload.items[0]).toMatchObject({
      farmCode: "FARM-001",
      forecastStatus: "ready",
      forecastQuantityKg: 38_750,
      observedMaxYieldKgPerHa: 3_400
    });
  });

  it("rejects a foreign farm row at the scoped runtime boundary", () => {
    expect(() => parseScopedYieldForecast({
      ...yieldForecastEnvelope,
      payload: {
        ...yieldForecastEnvelope.payload,
        items: [{ ...forecastItem, farmCode: "FARM-002" }]
      }
    }, "FARM-001")).toThrow("farm scope mismatch");
  });

  it("rejects a duplicate season grain at the scoped runtime boundary", () => {
    expect(() => parseScopedYieldForecast({
      ...yieldForecastEnvelope,
      payload: {
        ...yieldForecastEnvelope.payload,
        forecastHealth: { ready: 2, insufficientHistory: 0, total: 2 },
        items: [forecastItem, { ...forecastItem, fieldCode: "FIELD-002" }],
        page: { hasMore: false, limit: 100, offset: 0, total: 2 }
      }
    }, "FARM-001")).toThrow("season grain is duplicated");
  });

  it("rejects malformed, unknown, non-finite, and inconsistent evidence", () => {
    expect(() => yieldForecastEnvelopeSchema.parse({
      ...yieldForecastEnvelope,
      payload: {
        ...yieldForecastEnvelope.payload,
        forecastHealth: { ready: 0, insufficientHistory: 0, total: 1 },
        items: [{ ...forecastItem, historyStartAt: "not-a-timestamp" }],
        page: { hasMore: true, limit: 100, offset: 0, total: 1 }
      }
    })).toThrow();

    expect(() => yieldForecastEnvelopeSchema.parse({
      ...yieldForecastEnvelope,
      payload: {
        ...yieldForecastEnvelope.payload,
        items: [{ ...forecastItem, forecastQuantityKg: Number.NaN }]
      }
    })).toThrow();

    expect(() => yieldForecastEnvelopeSchema.parse({
      ...yieldForecastEnvelope,
      unexpected: true
    })).toThrow();
  });

  it("formats timestamp evidence in UTC regardless of the browser zone", () => {
    expect(formatDateTime("2026-07-30T01:00:00Z")).toContain("01:00");
    expect(formatDateTime("2026-07-30T01:00:00")).toBe("01:00 30/07/2026");
    expect(formatDateTime(null)).toBe("Chưa có");
  });
});
