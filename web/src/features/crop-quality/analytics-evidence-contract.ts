import { z } from "zod";

const DATA_STATUSES = ["current", "stale", "partial", "missing"] as const;
const SEVERITIES = ["none", "low", "medium", "high"] as const;
const codeSchema = z.string().regex(/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/);
const labelSchema = z.string().trim().min(1).max(500);
const nonnegativeNumber = z.number().finite().nonnegative();
const nonnegativeInteger = z.number().int().nonnegative();
const optionalCode = codeSchema.nullish();

const freshnessSchema = z.object({
  artifactAgeHours: nonnegativeNumber,
  dataStatus: z.enum(DATA_STATUSES),
  maxAgeHours: z.number().int().positive()
}).strict();

const lineageSchema = z.object({
  asOf: z.iso.date(),
  contractVersion: z.literal("1.0.0"),
  generatedAt: z.iso.datetime({ offset: true }),
  manifestFingerprint: z.string().regex(/^[0-9a-f]{64}$/),
  runId: z.string().trim().min(1).max(160)
}).strict();

const appliedFilterSchema = z.object({
  cropCode: optionalCode,
  dateFrom: z.iso.date().nullish(),
  datePreset: z.enum(["all", "last-30-days", "season-to-date"]),
  dateTo: z.iso.date(),
  farmCode: optionalCode,
  fieldCode: optionalCode,
  seasonCode: optionalCode
}).strict();

const scopeSchema = z.object({
  appliedFilter: appliedFilterSchema.nullish(),
  farmCodes: z.array(codeSchema).max(10_000),
  tenantId: z.uuid(),
  tenantWide: z.boolean(),
  warehouseCodes: z.array(codeSchema).max(10_000)
}).strict();

const pageSchema = z.object({
  hasMore: z.boolean(),
  limit: z.number().int().min(1).max(100),
  offset: z.number().int().min(0).max(10_000),
  total: z.number().int().min(0).max(10_000)
}).strict();

const evidenceSignalSchema = z.object({
  name: codeSchema,
  unit: z.string().trim().min(1).max(32).nullish(),
  value: z.json()
}).strict();

const fieldHealthSchema = z.object({
  airHumidityPct: nonnegativeNumber,
  areaHa: nonnegativeNumber,
  batteryPct: nonnegativeNumber,
  cropCode: codeSchema,
  cropName: labelSchema,
  farmCode: codeSchema,
  farmName: labelSchema,
  fieldCode: codeSchema,
  fieldName: labelSchema,
  lastReadingAt: z.iso.datetime({ offset: true }),
  latitude: z.number().finite().min(-90).max(90),
  longitude: z.number().finite().min(-180).max(180),
  maxAffectedAreaPct: nonnegativeNumber,
  maxMortalityPct: nonnegativeNumber,
  pestCases90d: nonnegativeInteger,
  rainfall7dMm: nonnegativeNumber,
  readingCount7d: nonnegativeInteger,
  recommendedAction: labelSchema,
  riskScore: nonnegativeNumber,
  riskStatus: codeSchema,
  sensorAgeDays: nonnegativeNumber,
  soilMoisturePct: nonnegativeNumber,
  soilPh: nonnegativeNumber,
  temperatureC: z.number().finite()
}).strict();

const cropAlertSchema = z.object({
  cropName: labelSchema,
  farmCode: codeSchema,
  farmName: labelSchema,
  fieldCode: codeSchema,
  fieldName: labelSchema,
  maxAffectedAreaPct: nonnegativeNumber,
  pestCases90d: nonnegativeInteger,
  rainfall7dMm: nonnegativeNumber,
  recommendedAction: labelSchema,
  riskScore: nonnegativeNumber,
  riskStatus: codeSchema,
  sensorAgeDays: nonnegativeNumber,
  soilMoisturePct: nonnegativeNumber,
  soilPh: nonnegativeNumber
}).strict();

const cropSummarySchema = z.object({
  averageSoilMoisturePct: nonnegativeNumber.nullish(),
  averageSoilPh: nonnegativeNumber.nullish(),
  averageTemperatureC: z.number().finite().nullish(),
  highRiskFields: nonnegativeInteger,
  monitoredFields: nonnegativeInteger,
  offlineSensors: nonnegativeInteger,
  pestCases90d: nonnegativeInteger,
  readings7d: nonnegativeInteger,
  watchFields: nonnegativeInteger
}).strict();

const pestIncidentSchema = z.object({
  averageAffectedAreaPct: nonnegativeNumber,
  caseCount: nonnegativeInteger,
  maxAffectedAreaPct: nonnegativeNumber,
  pestCode: codeSchema,
  pestName: labelSchema,
  week: z.iso.date()
}).strict();

const cropHealthPayloadSchema = z.object({
  alerts: z.array(cropAlertSchema).max(100),
  assessmentMethod: z.literal("rule-based-heuristic"),
  evidenceSignals: z.array(evidenceSignalSchema).max(100),
  fields: z.array(fieldHealthSchema).max(100),
  page: pageSchema,
  pestIncidentsWeekly: z.array(pestIncidentSchema).max(1_000),
  severity: z.enum(SEVERITIES),
  summary: cropSummarySchema
}).strict();

const qualityCheckSchema = z.object({
  check: codeSchema,
  failedRows: nonnegativeInteger,
  severity: codeSchema,
  table: codeSchema,
  totalRows: nonnegativeInteger
}).strict();

const qualityScoreSchema = z.object({
  completenessPct: z.number().finite().min(0).max(100),
  freshnessAgeDays: nonnegativeNumber,
  freshnessPct: z.number().finite().min(0).max(100),
  uniquenessPct: z.number().finite().min(0).max(100),
  validityPct: z.number().finite().min(0).max(100)
}).strict();

const dataQualityPayloadSchema = z.object({
  assessmentMethod: z.literal("rule-based-heuristic"),
  checks: z.object({
    after: z.array(qualityCheckSchema).max(1_000),
    before: z.array(qualityCheckSchema).max(1_000)
  }).strict(),
  evidenceSignals: z.array(evidenceSignalSchema).max(100),
  remediationActions: z.object({
    codesCanonicalized: nonnegativeInteger,
    duplicatesRemoved: nonnegativeInteger,
    rowsQuarantined: nonnegativeInteger,
    unitsConvertedToBase: nonnegativeInteger,
    unitsConvertedToKg: nonnegativeInteger
  }).strict(),
  scores: z.object({
    after: qualityScoreSchema,
    before: qualityScoreSchema
  }).strict(),
  severity: z.enum(SEVERITIES),
  status: z.enum(["passed", "failed"])
}).strict();

export const cropHealthEnvelopeSchema = z.object({
  freshness: freshnessSchema,
  lineage: lineageSchema,
  payload: cropHealthPayloadSchema,
  scope: scopeSchema
}).strict().readonly();

export const dataQualityEnvelopeSchema = z.object({
  freshness: freshnessSchema,
  lineage: lineageSchema,
  payload: dataQualityPayloadSchema,
  scope: scopeSchema
}).strict().readonly();

export type DataStatus = (typeof DATA_STATUSES)[number];
export type EvidenceSeverity = (typeof SEVERITIES)[number];
export type CropHealthEnvelope = z.output<typeof cropHealthEnvelopeSchema>;
export type DataQualityEnvelope = z.output<typeof dataQualityEnvelopeSchema>;
