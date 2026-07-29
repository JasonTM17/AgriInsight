import { z } from "zod";

import type { components } from "@/server/generated/backend/schema";

type GeneratedEvidence = components["schemas"]["Evidence"];
type GeneratedAlert = components["schemas"]["RealtimeOperationalAlertResponse"];
type GeneratedFeed =
  components["schemas"]["RealtimeOperationalAlertFeedResponse"];

export const REALTIME_ALERT_LIMIT = 50;
export const realtimeAlertAcknowledgementBodySchema =
  z.object({}).strict().readonly();
export const realtimeAlertParamsSchema =
  z.object({ alertId: z.uuid() }).strict().readonly();

const instantSchema = z.iso.datetime({ offset: true });
const evidenceShape = {
  id: z.uuid().nullable(),
  type: z.enum(["TENANT_BACKLOG", "OPERATIONAL_EVENT"])
} satisfies Record<keyof Required<GeneratedEvidence>, z.ZodType>;

const realtimeAlertEvidenceSchema =
  z.object(evidenceShape).strict().readonly()
    .refine(
      (evidence) =>
        evidence.type === "TENANT_BACKLOG"
          ? evidence.id === null
          : evidence.id !== null,
      "Alert evidence type and identifier do not match."
    );

const alertShape = {
  acknowledged: z.boolean(),
  acknowledgedAt: instantSchema.nullable(),
  ageSeconds: z.number().int().nonnegative(),
  evidence: realtimeAlertEvidenceSchema,
  id: z.uuid(),
  lastEvaluatedAt: instantSchema,
  lastObservedAt: instantSchema,
  openedAt: instantSchema,
  policy: z.enum([
    "OUTBOX_PUBLISH_BACKLOG",
    "REALTIME_DELIVERY_LAG",
    "REALTIME_DLT_RECORD"
  ]),
  severity: z.enum(["WARNING", "CRITICAL"]),
  source: z.literal("realtime_operational"),
  sourceOccurredAt: instantSchema,
  state: z.literal("OPEN")
} satisfies Record<keyof Required<GeneratedAlert>, z.ZodType>;

export const realtimeOperationalAlertSchema =
  z.object(alertShape).strict().readonly()
    .refine(
      (alert) => alert.acknowledged === (alert.acknowledgedAt !== null),
      "Alert acknowledgement state and time do not match."
    )
    .refine(
      (alert) =>
        alert.policy === "OUTBOX_PUBLISH_BACKLOG"
          ? alert.evidence.type === "TENANT_BACKLOG"
            && alert.evidence.id === null
          : alert.evidence.type === "OPERATIONAL_EVENT"
            && alert.evidence.id !== null,
      "Alert policy and evidence do not match."
    );

const feedShape = {
  generatedAt: instantSchema,
  hasMore: z.boolean(),
  items: z.array(realtimeOperationalAlertSchema)
    .max(REALTIME_ALERT_LIMIT)
    .readonly(),
  limit: z.literal(REALTIME_ALERT_LIMIT)
} satisfies Record<keyof Required<GeneratedFeed>, z.ZodType>;

export const realtimeOperationalAlertFeedSchema =
  z.object(feedShape).strict().readonly();

export type RealtimeOperationalAlert =
  z.output<typeof realtimeOperationalAlertSchema>;
export type RealtimeOperationalAlertFeed =
  z.output<typeof realtimeOperationalAlertFeedSchema>;
