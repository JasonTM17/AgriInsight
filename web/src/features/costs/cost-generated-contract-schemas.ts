import { z } from "zod";

import type { components as BackendComponents } from "@/server/generated/backend/schema";

type GeneratedOperatingCost = BackendComponents["schemas"]["OperatingCostResponse"];
type GeneratedOperatingCostPage =
  BackendComponents["schemas"]["OperatingCostPageResponse"];
type GeneratedCostSummary = BackendComponents["schemas"]["CostSummaryResponse"];
type GeneratedCostCorrection =
  BackendComponents["schemas"]["CostCorrectionResponse"];

const uuidSchema = z.string().regex(
  /^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/
);
const instantSchema = z.iso.datetime({ offset: true });
const categorySchema = z.enum([
  "LABOR",
  "MATERIAL",
  "MACHINERY",
  "TRANSPORT",
  "UTILITY",
  "OTHER"
]);
const targetTypeSchema = z.enum([
  "TENANT",
  "FARM",
  "FIELD",
  "SEASON",
  "ACTIVITY"
]);

const optionalString = z.string().nullish().transform((value) => value ?? undefined);
const optionalUuid = uuidSchema.nullish().transform((value) => value ?? undefined);

const operatingCostShape = {
  amountVnd: z.number().finite().nonnegative(),
  category: categorySchema,
  description: optionalString,
  entryKind: z.enum(["POSTING", "REVERSAL"]),
  id: uuidSchema,
  occurredAt: instantSchema,
  reversalOf: optionalUuid,
  signedAmountVnd: z.number().finite(),
  sourceReference: optionalString,
  targetId: optionalUuid,
  targetType: targetTypeSchema,
  version: z.number().int().nonnegative()
} satisfies Record<keyof Required<GeneratedOperatingCost>, z.ZodType>;
export const operatingCostEntrySchema =
  z.object(operatingCostShape).strict().readonly();
const costCorrectionShape = {
  replacement: operatingCostEntrySchema,
  reversal: operatingCostEntrySchema
} satisfies Record<keyof Required<GeneratedCostCorrection>, z.ZodType>;
export const costCorrectionResponseSchema =
  z.object(costCorrectionShape).strict().readonly();

const operatingSummaryItemSchema = z.object({
  budgetVarianceVnd: z.number().finite().nullish(),
  groupId: optionalUuid,
  groupKey: optionalString,
  netOperatingCostVnd: z.number().finite(),
  postingAmountVnd: z.number().finite().nonnegative(),
  reversalAmountVnd: z.number().finite().nonnegative(),
  seasonBudgetVnd: z.number().finite().nullish()
}).strict();

const operatingCostPageShape = {
  hasMore: z.boolean().default(false),
  items: z.array(operatingCostEntrySchema).max(100).default([]),
  limit: z.number().int().min(1).max(100).default(50),
  offset: z.number().int().min(0).max(10_000).default(0)
} satisfies Record<keyof GeneratedOperatingCostPage, z.ZodType>;

const operatingSummaryShape = {
  groupBy: z.enum(["MONTH", "FARM", "SEASON", "CATEGORY"]),
  hasMore: z.boolean().default(false),
  items: z.array(operatingSummaryItemSchema).max(10_000).default([]),
  lens: z.literal("OPERATING_COST"),
  limit: z.number().int().positive().default(10_000),
  occurredFrom: instantSchema,
  occurredTo: instantSchema,
  source: z.literal("POSTGRES_OPERATING_COST_LEDGER"),
  tenantId: uuidSchema
} satisfies Record<keyof Required<GeneratedCostSummary>, z.ZodType>;

const freshnessSchema = z.object({
  artifactAgeHours: z.number().finite().nonnegative(),
  dataStatus: z.enum(["current", "stale", "partial", "missing"]),
  maxAgeHours: z.number().int().positive()
}).strict();
const lineageSchema = z.object({
  asOf: z.string().min(1),
  contractVersion: z.literal("1.0.0"),
  generatedAt: z.string().min(1),
  manifestFingerprint: z.string().length(64),
  runId: z.string().min(1)
}).strict();
const procurementItemSchema = z.object({
  farmCode: z.string().min(1),
  farmName: z.string().min(1),
  materialCode: z.string().min(1),
  materialName: z.string().min(1),
  procurementQuantityBaseUnit: z.number().finite().nonnegative(),
  procurementSpendVnd: z.number().finite().nonnegative(),
  procurementUnitCostVnd: z.number().finite().nonnegative(),
  supplierCode: z.string().min(1),
  supplierName: z.string().min(1),
  transactionDate: z.string().min(1),
  transactionId: z.string().min(1),
  warehouseCode: z.string().min(1),
  warehouseName: z.string().min(1)
}).strict();
const procurementMonthlySchema = z.object({
  month: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
  procurementQuantityBaseUnit: z.number().finite().nonnegative(),
  procurementSpendVnd: z.number().finite().nonnegative(),
  transactionCount: z.number().int().nonnegative()
}).strict();
const procurementSupplierSchema = z.object({
  procurementSpendVnd: z.number().finite().nonnegative(),
  supplierCode: z.string().min(1),
  supplierName: z.string().min(1),
  transactionCount: z.number().int().nonnegative()
}).strict();
const procurementPayloadSchema = z.object({
  capabilities: z.object({
    detailPageAvailable: z.literal(true),
    fileExportAvailable: z.literal(true),
    readOnly: z.literal(true)
  }).strict(),
  items: z.array(procurementItemSchema).max(100).readonly(),
  monthly: z.array(procurementMonthlySchema).max(1200).readonly(),
  page: z.object({
    hasMore: z.boolean(),
    limit: z.number().int().min(1).max(100),
    offset: z.number().int().min(0).max(10_000),
    total: z.number().int().min(0).max(100_000)
  }).strict(),
  suppliers: z.array(procurementSupplierSchema).max(20).readonly(),
  summary: z.object({
    procurementQuantityBaseUnit: z.number().finite().nonnegative(),
    procurementSpendVnd: z.number().finite().nonnegative(),
    transactionCount: z.number().int().nonnegative()
  }).strict()
}).strict();

export const operatingCostPageSchema =
  z.object(operatingCostPageShape).strict().readonly();
export const operatingCostSummarySchema =
  z.object(operatingSummaryShape).strict().readonly();
export const procurementCostsEnvelopeSchema = z.object({
  freshness: freshnessSchema,
  lineage: lineageSchema,
  payload: procurementPayloadSchema,
  scope: z.object({
    appliedFilter: z.unknown().optional(),
    farmCodes: z.array(z.string()).max(100_000),
    tenantId: uuidSchema,
    tenantWide: z.boolean(),
    warehouseCodes: z.array(z.string()).max(100_000)
  }).strict()
}).strict().readonly();

export type OperatingCostEntry = z.output<
  typeof operatingCostPageSchema
>["items"][number];
export type OperatingCostPage = z.output<typeof operatingCostPageSchema>;
export type OperatingCostSummary = z.output<typeof operatingCostSummarySchema>;
export type ProcurementCostsEnvelope = z.output<
  typeof procurementCostsEnvelopeSchema
>;
