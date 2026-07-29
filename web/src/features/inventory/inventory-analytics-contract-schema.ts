import "server-only";

import { z } from "zod";

import type { components } from "@/server/generated/analytics/schema";

type InventoryEnvelope =
  components["schemas"]["AnalyticsEnvelope_InventoryPayload_"];
type InventoryAbc = components["schemas"]["InventoryAbcModel"];
type InventoryAlert = components["schemas"]["InventoryAlertModel"];
type InventoryStatus = components["schemas"]["InventoryItemModel"];
type InventorySummary = components["schemas"]["InventorySummaryModel"];
type AppliedFilter = components["schemas"]["AppliedFilterModel"];

const finite = z.number().finite();
const nullableFinite = finite.nullable();
const nonnegativeInteger = finite.int().nonnegative();
const nullableNonnegativeFinite = finite.nonnegative().nullable();
const nullableIsoDate = z.iso.date().nullable();
const nullableModelVersion = z.string().max(64).nullable();
const nullableHistoryDays = finite.int().min(1).max(180).nullable();
const nullableNonzeroDemandDays = nonnegativeInteger.max(180).nullable();
const nullableHorizonDays = z.literal(30).nullable();
const nullableBacktestWindows = nonnegativeInteger.max(9).nullable();

const forecastShape = {
  asOfDate: nullableIsoDate,
  modelVersion: nullableModelVersion,
  coverageStatus: z.enum([
    "ready",
    "noDemand",
    "insufficientHistory",
    "unavailable"
  ]),
  historyStartDate: nullableIsoDate,
  historyEndDate: nullableIsoDate,
  historyDays: nullableHistoryDays,
  nonzeroDemandDays: nullableNonzeroDemandDays,
  horizonDays: nullableHorizonDays,
  forecastQuantity: nullableNonnegativeFinite,
  lowerQuantity: nullableNonnegativeFinite,
  upperQuantity: nullableNonnegativeFinite,
  backtestWindows: nullableBacktestWindows,
  backtestMae: nullableNonnegativeFinite,
  backtestWapePct: nullableNonnegativeFinite,
  forecastDaysOfSupply: nullableNonnegativeFinite,
  forecastSuggestedOrderQuantity: nullableNonnegativeFinite
};

const forecastHealthShape = {
  ready: nonnegativeInteger,
  noDemand: nonnegativeInteger,
  insufficientHistory: nonnegativeInteger,
  unavailable: nonnegativeInteger,
  total: nonnegativeInteger
};

const forecastSchema = z.object(forecastShape).strict().superRefine(
  (forecast, context) => {
    if (forecast.coverageStatus !== "unavailable") return;
    if (Object.entries(forecast).some(([key, value]) => key !== "coverageStatus" && value !== null)) {
      context.addIssue({
        code: "custom",
        message: "Unavailable forecast evidence must be null."
      });
    }
  }
).readonly();
const forecastHealthSchema = z.object(forecastHealthShape).strict().superRefine(
  (health, context) => {
    const covered =
      health.ready +
      health.noDemand +
      health.insufficientHistory +
      health.unavailable;
    if (covered !== health.total) {
      context.addIssue({
        code: "custom",
        message: "Forecast health counters must equal total."
      });
    }
  }
).readonly();

const abcShape = {
  abcClass: z.enum(["A", "B", "C"]),
  category: z.string(),
  cumulativeValueSharePct: finite,
  inventoryValueVnd: finite,
  materialCode: z.string(),
  materialName: z.string(),
  stockLocations: z.number().int().nonnegative(),
  valueSharePct: finite
} satisfies Record<keyof InventoryAbc, z.ZodType>;

const alertShape = {
  abcClass: z.enum(["A", "B", "C"]),
  alertType: z.string(),
  baseUnit: z.string(),
  category: z.string(),
  farmName: z.string(),
  materialCode: z.string(),
  materialName: z.string(),
  message: z.string(),
  recommendedAction: z.string(),
  severity: z.string(),
  stockQuantity: finite,
  warehouseCode: z.string(),
  warehouseName: z.string()
} satisfies Record<keyof InventoryAlert, z.ZodType>;

const statusShape = {
  abcClass: z.enum(["A", "B", "C"]),
  averageDailyUsage: finite,
  averageUnitCostVnd: finite,
  baseUnit: z.string(),
  category: z.string(),
  daysOfSupply: nullableFinite,
  daysToExpiry: nullableFinite,
  farmCode: z.string(),
  farmName: z.string(),
  forecast: forecastSchema,
  inventoryValueVnd: finite,
  materialCode: z.string(),
  materialName: z.string(),
  nearestExpiryDate: z.string().nullable(),
  predicted30dNeed: finite,
  recommendedOrderQuantity: finite,
  reorderPoint: finite,
  stockQuantity: finite,
  stockStatus: z.string(),
  targetStockLevel: finite,
  warehouseCode: z.string(),
  warehouseName: z.string()
} satisfies Record<keyof InventoryStatus, z.ZodType>;

const summaryShape = {
  averageDaysOfSupply: nullableFinite,
  criticalAlerts: z.number().int().nonnegative(),
  expiring30dSkus: z.number().int().nonnegative(),
  lowStockSkus: z.number().int().nonnegative(),
  materialSkus: z.number().int().nonnegative(),
  overstockSkus: z.number().int().nonnegative(),
  skuLocations: z.number().int().nonnegative(),
  stockoutSkus: z.number().int().nonnegative(),
  totalInventoryValueVnd: finite
} satisfies Record<keyof InventorySummary, z.ZodType>;

const abcSchema = z.object(abcShape).strict().readonly();
const alertSchema = z.object(alertShape).strict().readonly();
const statusSchema = z.object(statusShape).strict().readonly();
const summarySchema = z.object(summaryShape).strict().readonly();
const optionalNullableText = z.string().nullable().optional();
const appliedFilterShape = {
  cropCode: optionalNullableText,
  dateFrom: optionalNullableText,
  datePreset: z.enum(["all", "last-30-days", "season-to-date"]),
  dateTo: z.string(),
  farmCode: optionalNullableText,
  fieldCode: optionalNullableText,
  seasonCode: optionalNullableText
} satisfies Record<keyof AppliedFilter, z.ZodType>;
const appliedFilterSchema =
  z.object(appliedFilterShape).strict().readonly();

export const inventoryAnalyticsEnvelopeSchema = z.object({
  freshness: z.object({
    artifactAgeHours: finite,
    dataStatus: z.enum(["current", "stale", "partial", "missing"]),
    maxAgeHours: finite
  }).strict().readonly(),
  lineage: z.object({
    asOf: z.string(),
    contractVersion: z.literal("1.0.0"),
    generatedAt: z.iso.datetime({ offset: true }),
    manifestFingerprint: z.string().min(1),
    runId: z.string().min(1)
  }).strict().readonly(),
  payload: z.object({
    abc: z.array(abcSchema).max(100).readonly(),
    alerts: z.array(alertSchema).max(100).readonly(),
    forecastHealth: forecastHealthSchema,
    items: z.array(statusSchema).max(100).readonly(),
    page: z.object({
      hasMore: z.boolean(),
      limit: z.number().int().min(1).max(100),
      offset: z.number().int().min(0).max(10_000),
      total: z.number().int().nonnegative()
    }).strict().readonly(),
    summary: summarySchema
  }).strict().readonly(),
  scope: z.object({
    appliedFilter: appliedFilterSchema.nullish(),
    farmCodes: z.array(z.string()).readonly().optional(),
    tenantId: z.uuid(),
    tenantWide: z.boolean(),
    warehouseCodes: z.array(z.string()).readonly().optional()
  }).strict().readonly()
}).strict().readonly() satisfies z.ZodType<InventoryEnvelope>;

export type InventoryAnalyticsEnvelope = z.output<
  typeof inventoryAnalyticsEnvelopeSchema
>;
export type InventoryForecastEvidence = z.output<typeof forecastSchema>;
export type InventoryForecastHealth = z.output<typeof forecastHealthSchema>;
export type InventoryForecastCoverageStatus =
  InventoryForecastEvidence["coverageStatus"];

export function parseScopedInventoryAnalytics(
  value: unknown,
  warehouseCode: string
): InventoryAnalyticsEnvelope {
  const envelope = inventoryAnalyticsEnvelopeSchema.parse(value);
  const hasForeignRows = [
    ...envelope.payload.items,
    ...envelope.payload.alerts
  ].some((item) => item.warehouseCode !== warehouseCode);
  if (hasForeignRows) {
    throw new Error("Inventory analytics scope mismatch");
  }
  return envelope;
}
