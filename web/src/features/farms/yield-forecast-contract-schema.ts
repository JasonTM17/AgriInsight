import "server-only";

import { z } from "zod";

import type { AnalyticsResponse } from "@/server/clients/analytics";

type GeneratedYieldForecastEnvelope = AnalyticsResponse<"analyticsYieldForecast">;

const finite = z.number().finite();
const nonnegativeFinite = finite.nonnegative();
const nonnegativeInteger = nonnegativeFinite.int();
const nullableNonnegativeFinite = nonnegativeFinite.nullable();
const nullableDateTime = z.iso.datetime({ local: true, offset: true }).nullable();

const forecastHealthSchema = z.object({
  ready: nonnegativeInteger,
  insufficientHistory: nonnegativeInteger,
  total: nonnegativeInteger
}).strict().superRefine((health, context) => {
  if (health.ready + health.insufficientHistory !== health.total) {
    context.addIssue({
      code: "custom",
      message: "Yield forecast health counters must equal total."
    });
  }
}).readonly();

const yieldForecastItemSchema = z.object({
  asOfDate: z.iso.date(),
  farmCode: z.string().min(1).max(64),
  fieldCode: z.string().min(1).max(64),
  seasonCode: z.string().min(1).max(64),
  cropCode: z.string().min(1).max(64),
  modelVersion: z.string().min(1).max(64),
  forecastStatus: z.enum(["ready", "insufficientHistory"]),
  forecastOriginDate: z.iso.date(),
  expectedHarvestDate: z.iso.date(),
  seasonAreaHa: finite.positive(),
  targetYieldKg: nullableNonnegativeFinite,
  historyStartAt: nullableDateTime,
  historyEndAt: nullableDateTime,
  historySeasons: nonnegativeInteger,
  backtestOrigins: nonnegativeInteger,
  backtestSeasons: nonnegativeInteger,
  forecastYieldKgPerHa: nullableNonnegativeFinite,
  observedMinYieldKgPerHa: nullableNonnegativeFinite,
  observedMaxYieldKgPerHa: nullableNonnegativeFinite,
  forecastQuantityKg: nullableNonnegativeFinite,
  observedMinQuantityKg: nullableNonnegativeFinite,
  observedMaxQuantityKg: nullableNonnegativeFinite,
  backtestMaeKgPerHa: nullableNonnegativeFinite,
  backtestWapePct: nullableNonnegativeFinite
}).strict().superRefine((item, context) => {
  const evidence = [
    item.forecastYieldKgPerHa,
    item.observedMinYieldKgPerHa,
    item.observedMaxYieldKgPerHa,
    item.forecastQuantityKg,
    item.observedMinQuantityKg,
    item.observedMaxQuantityKg,
    item.backtestMaeKgPerHa,
    item.backtestWapePct
  ];
  if (item.forecastStatus === "ready") {
    if (evidence.some((value) => value === null)) {
      context.addIssue({
        code: "custom",
        message: "Ready yield forecast evidence must be present."
      });
    }
    if (item.historySeasons < 5 || item.backtestOrigins < 2) {
      context.addIssue({
        code: "custom",
        message: "Ready yield forecasts require the minimum history and backtest evidence."
      });
    }
  } else if (evidence.some((value) => value !== null)) {
    context.addIssue({
      code: "custom",
      message: "Insufficient-history yield forecast evidence must be null."
    });
  }
  if (
    item.observedMinYieldKgPerHa !== null &&
    item.observedMaxYieldKgPerHa !== null &&
    item.observedMinYieldKgPerHa > item.observedMaxYieldKgPerHa
  ) {
    context.addIssue({
      code: "custom",
      message: "Observed yield span must be ordered."
    });
  }
  if (
    item.observedMinQuantityKg !== null &&
    item.observedMaxQuantityKg !== null &&
    item.observedMinQuantityKg > item.observedMaxQuantityKg
  ) {
    context.addIssue({
      code: "custom",
      message: "Observed quantity span must be ordered."
    });
  }
  if (item.forecastOriginDate > item.asOfDate) {
    context.addIssue({
      code: "custom",
      message: "Forecast origin must not be after the snapshot as-of date."
    });
  }
  if (item.expectedHarvestDate <= item.asOfDate) {
    context.addIssue({
      code: "custom",
      message: "Active-season expected harvest must be after the snapshot as-of date."
    });
  }
  if (item.historyStartAt === null !== (item.historyEndAt === null)) {
    context.addIssue({
      code: "custom",
      message: "History bounds must be present together."
    });
  }
  if (
    item.historyStartAt !== null &&
    item.historyEndAt !== null &&
    item.historyStartAt > item.historyEndAt
  ) {
    context.addIssue({
      code: "custom",
      message: "History bounds must be ordered."
    });
  }
}).readonly();

const appliedFilterSchema = z.object({
  cropCode: z.string().nullable().optional(),
  dateFrom: z.string().nullable().optional(),
  datePreset: z.enum(["all", "last-30-days", "season-to-date"]),
  dateTo: z.string(),
  farmCode: z.string().nullable().optional(),
  fieldCode: z.string().nullable().optional(),
  seasonCode: z.string().nullable().optional()
}).strict().readonly();

export const yieldForecastEnvelopeSchema: z.ZodType<GeneratedYieldForecastEnvelope> = z.object({
  freshness: z.object({
    artifactAgeHours: nonnegativeFinite,
    dataStatus: z.enum(["current", "stale", "partial", "missing"]),
    maxAgeHours: nonnegativeFinite
  }).strict().readonly(),
  lineage: z.object({
    asOf: z.iso.date(),
    contractVersion: z.literal("1.0.0"),
    generatedAt: z.iso.datetime({ offset: true }),
    manifestFingerprint: z.string().min(1),
    runId: z.string().min(1)
  }).strict().readonly(),
  payload: z.object({
    forecastHealth: forecastHealthSchema,
    items: z.array(yieldForecastItemSchema).max(100).readonly(),
    page: z.object({
      hasMore: z.boolean(),
      limit: z.number().int().min(1).max(100),
      offset: z.number().int().min(0).max(10_000),
      total: z.number().int().min(0).max(10_000)
    }).strict().readonly()
  }).strict().superRefine((payload, context) => {
    if (payload.items.length > payload.page.limit) {
      context.addIssue({
        code: "custom",
        message: "Yield forecast page exceeds its declared limit."
      });
    }
    if (payload.page.total < payload.page.offset + payload.items.length) {
      context.addIssue({
        code: "custom",
        message: "Yield forecast page exceeds its declared total."
      });
    }
    if (
      payload.page.hasMore !==
      (payload.page.offset + payload.items.length < payload.page.total)
    ) {
      context.addIssue({
        code: "custom",
        message: "Yield forecast hasMore must match the declared page total."
      });
    }
  }).readonly(),
  scope: z.object({
    appliedFilter: appliedFilterSchema.nullish(),
    farmCodes: z.array(z.string()).readonly().optional(),
    tenantId: z.uuid(),
    tenantWide: z.boolean(),
    warehouseCodes: z.array(z.string()).readonly().optional()
  }).strict().readonly()
}).strict().readonly();

export type YieldForecastEnvelope = GeneratedYieldForecastEnvelope;
export type YieldForecastItem = YieldForecastEnvelope["payload"]["items"][number];
export type YieldForecastHealth = YieldForecastEnvelope["payload"]["forecastHealth"];

export function parseScopedYieldForecast(
  value: unknown,
  farmCode: string
): YieldForecastEnvelope {
  const envelope = yieldForecastEnvelopeSchema.parse(value);
  const seasonCodes = new Set<string>();
  for (const item of envelope.payload.items) {
    if (item.farmCode !== farmCode) {
      throw new Error("Yield forecast farm scope mismatch");
    }
    if (seasonCodes.has(item.seasonCode)) {
      throw new Error("Yield forecast season grain is duplicated");
    }
    seasonCodes.add(item.seasonCode);
  }
  return envelope;
}
