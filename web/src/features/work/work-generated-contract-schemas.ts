import "server-only";

import { z } from "zod";

import type { components } from "@/server/generated/backend/schema";

type GeneratedActivity = components["schemas"]["ActivityResponse"];
type GeneratedActivityAssignment =
  components["schemas"]["ActivityAssignmentResponse"];
type GeneratedActivityLog = components["schemas"]["ActivityLogResponse"];
type GeneratedActivityPage = components["schemas"]["ActivityPageResponse"];
type GeneratedAssignmentPage =
  components["schemas"]["ActivityAssignmentPageResponse"];
type GeneratedLogPage = components["schemas"]["ActivityLogPageResponse"];

export const WORK_ACTIVITY_STATUSES = [
  "PLANNED",
  "STARTED",
  "COMPLETED",
  "CANCELLED"
] as const;
export const WORK_MAX_OFFSET = 10_000;

const activityTypes = [
  "PLANTING",
  "IRRIGATION",
  "FERTILIZATION",
  "PEST_CONTROL",
  "WEEDING",
  "PEST_INSPECTION",
  "HARVEST",
  "TRANSPORT"
] as const;
const logUnits = [
  "KG",
  "TONNE",
  "LITRE",
  "HOUR",
  "HECTARE",
  "UNIT"
] as const;
const instantSchema = z.iso.datetime({ offset: true });
const uuidSchema = z.uuid();
const optionalRuntimeValue = <Schema extends z.ZodType>(schema: Schema) =>
  schema.nullish().transform((value) => value ?? undefined);

const activityShape = {
  activityType: z.enum(activityTypes),
  cancelledAt: optionalRuntimeValue(instantSchema),
  code: z.string().min(1),
  completedAt: optionalRuntimeValue(instantSchema),
  description: optionalRuntimeValue(z.string()),
  dueAt: instantSchema,
  farmId: uuidSchema,
  fieldId: uuidSchema,
  id: uuidSchema,
  plannedStartAt: instantSchema,
  seasonId: uuidSchema,
  startedAt: optionalRuntimeValue(instantSchema),
  status: z.enum(WORK_ACTIVITY_STATUSES),
  title: z.string().min(1),
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedActivity, z.ZodType>;

const assignmentShape = {
  active: z.boolean(),
  activityId: uuidSchema,
  employeeId: uuidSchema,
  id: uuidSchema,
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedActivityAssignment, z.ZodType>;

const logShape = {
  activityId: uuidSchema,
  authorProfileId: uuidSchema,
  correctionKind: optionalRuntimeValue(z.enum(["REPLACE", "VOID"])),
  correctionReason: optionalRuntimeValue(z.string()),
  correctsLogId: optionalRuntimeValue(uuidSchema),
  employeeId: uuidSchema,
  evidenceUri: optionalRuntimeValue(z.string().min(1)),
  id: uuidSchema,
  notes: optionalRuntimeValue(z.string()),
  occurredAt: instantSchema,
  quantity: optionalRuntimeValue(z.number().finite()),
  unit: optionalRuntimeValue(z.enum(logUnits)),
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedActivityLog, z.ZodType>;

export const activitySchema = z.object(activityShape).strict().readonly();
export const assignmentSchema = z.object(assignmentShape).strict().readonly();
export const logSchema = z.object(logShape).strict().readonly();

const activityPageShape = {
  hasMore: z.boolean(),
  items: z.array(activitySchema).max(25).readonly(),
  limit: z.literal(25),
  offset: z.literal(0)
} satisfies Record<keyof GeneratedActivityPage, z.ZodType>;

const assignmentPageShape = {
  hasMore: z.boolean(),
  items: z.array(assignmentSchema).max(50).readonly(),
  limit: z.literal(50),
  offset: z.literal(0)
} satisfies Record<keyof GeneratedAssignmentPage, z.ZodType>;

const logPageShape = {
  hasMore: z.boolean(),
  items: z.array(logSchema).max(50).readonly(),
  limit: z.literal(50),
  offset: z.number().int().min(0).max(WORK_MAX_OFFSET)
} satisfies Record<keyof GeneratedLogPage, z.ZodType>;

export const activityPageSchema =
  z.object(activityPageShape).strict().readonly();
export const assignmentPageSchema =
  z.object(assignmentPageShape).strict().readonly();
export const logPageSchema = z.object(logPageShape).strict().readonly();

export type WorkActivity = z.output<typeof activitySchema>;
export type WorkActivityAssignment = z.output<typeof assignmentSchema>;
export type WorkActivityLog = z.output<typeof logSchema>;
export type WorkActivityStatus = WorkActivity["status"];
