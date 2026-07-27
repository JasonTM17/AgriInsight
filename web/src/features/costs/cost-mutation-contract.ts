import { z } from "zod";

import { OPERATING_COST_CATEGORIES } from "./cost-filter-schema";

const uuidSchema = z.string().regex(
  /^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/
);
const amountSchema = z.number()
  .finite()
  .int()
  .positive()
  .max(9_999_999_999_999_999);
const optionalText = (max: number) =>
  z.preprocess(
    (value) => typeof value === "string" && value.trim() === "" ? undefined : value,
    z.string().trim().min(1).max(max).optional()
  );
const targetTypeSchema = z.enum([
  "TENANT",
  "FARM",
  "FIELD",
  "SEASON",
  "ACTIVITY"
]);
const categorySchema = z.enum(OPERATING_COST_CATEGORIES);

const operatingCostFields = {
  amountVnd: amountSchema,
  category: categorySchema,
  description: optionalText(500),
  occurredAt: z.iso.datetime({ offset: true }),
  reasonCode: optionalText(128),
  sourceReference: optionalText(128),
  targetId: uuidSchema.optional(),
  targetType: targetTypeSchema
};

export const postCostEntrySchema = z.object(operatingCostFields).strict()
  .superRefine(validateTarget);

export const correctCostEntrySchema = z.object({
  entryId: uuidSchema,
  ...operatingCostFields,
  correctionReason: z.string().trim().min(1).max(500)
}).strict().superRefine(validateTarget);

export type PostCostEntryInput = z.infer<typeof postCostEntrySchema>;
export type CorrectCostEntryInput = z.infer<typeof correctCostEntrySchema>;

function validateTarget(
  value: Readonly<{ targetId?: string; targetType: string }>,
  context: z.RefinementCtx
): void {
  if (value.targetType === "TENANT" && value.targetId) {
    context.addIssue({
      code: "custom",
      message: "Phân bổ TENANT không được chứa targetId.",
      path: ["targetId"]
    });
  }
  if (value.targetType !== "TENANT" && !value.targetId) {
    context.addIssue({
      code: "custom",
      message: "Phân bổ cấp nông trại trở xuống yêu cầu targetId.",
      path: ["targetId"]
    });
  }
}
