import { z } from "zod";

import type { components as AnalyticsComponents } from "@/server/generated/analytics/schema";

type GeneratedAnswer = AnalyticsComponents["schemas"]["AssistantAnswer"];
type GeneratedCitation = AnalyticsComponents["schemas"]["EvidenceCitation"];
type GeneratedQuery = AnalyticsComponents["schemas"]["AssistantQuery"];
type GeneratedTurn = AnalyticsComponents["schemas"]["ConversationTurn"];
type GeneratedUsage = AnalyticsComponents["schemas"]["AssistantUsage"];

const plainTextSchema = (maximum: number) =>
  z.string()
    .trim()
    .min(1)
    .max(maximum)
    .refine(
      (value) => !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value),
      "Nội dung chứa ký tự điều khiển không hợp lệ."
    )
    .refine(
      (value) => !/<\s*\/?\s*[A-Za-z][^>]*>/.test(value),
      "Nội dung phải là văn bản thuần."
    );

const turnShape = {
  content: plainTextSchema(2_000),
  role: z.enum(["user", "assistant"])
} satisfies Record<keyof GeneratedTurn, z.ZodType>;

export const conversationTurnSchema =
  z.object(turnShape).strict().readonly();

const queryShape = {
  history: z.array(conversationTurnSchema).max(6).optional(),
  question: plainTextSchema(1_200)
} satisfies Record<keyof Required<GeneratedQuery>, z.ZodType>;

export const assistantQuerySchema = z.object(queryShape).strict().readonly();

const citationShape = {
  asOf: z.iso.date(),
  evidenceId: z.string().regex(/^[a-z0-9][a-z0-9._:-]{0,127}$/),
  excerpt: plainTextSchema(1_200),
  sourceType: z.enum([
    "overview",
    "farm-performance",
    "inventory",
    "crop-health",
    "data-quality",
    "cost",
    "procurement"
  ]),
  title: plainTextSchema(200)
} satisfies Record<keyof GeneratedCitation, z.ZodType>;

export const assistantCitationSchema =
  z.object(citationShape).strict().readonly();

const usageShape = {
  completionTokens: z.number().int().nonnegative(),
  promptCacheHitTokens: z.number().int().nonnegative(),
  promptCacheMissTokens: z.number().int().nonnegative(),
  promptTokens: z.number().int().nonnegative(),
  totalTokens: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedUsage, z.ZodType>;

const assistantUsageSchema = z.object(usageShape).strict().readonly()
  .refine(
    (usage) => usage.promptCacheHitTokens + usage.promptCacheMissTokens
      === usage.promptTokens,
    "Số token cache không khớp."
  )
  .refine(
    (usage) => usage.promptTokens + usage.completionTokens
      === usage.totalTokens,
    "Tổng token không khớp."
  );

const answerShape = {
  answer: plainTextSchema(8_000),
  citations: z.array(assistantCitationSchema).max(20).optional(),
  status: z.enum(["answered", "insufficient_evidence"]),
  usage: assistantUsageSchema
} satisfies Record<keyof Required<GeneratedAnswer>, z.ZodType>;

export const assistantAnswerSchema = z.object(answerShape).strict().readonly()
  .refine(
    (answer) => answer.status !== "answered" || (answer.citations?.length ?? 0) > 0,
    "Câu trả lời phải có bằng chứng."
  );

export type AssistantAnswer = z.infer<typeof assistantAnswerSchema>;
export type AssistantQuery = z.infer<typeof assistantQuerySchema>;
export type ConversationTurn = z.infer<typeof conversationTurnSchema>;
