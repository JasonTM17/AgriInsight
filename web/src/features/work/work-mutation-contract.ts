import { z } from "zod";

const unitSchema = z.enum([
  "KG",
  "TONNE",
  "LITRE",
  "HOUR",
  "HECTARE",
  "UNIT"
]);

const optionalTrimmedText = (maximum: number) =>
  z.preprocess(
    (value) =>
      typeof value === "string" && value.trim() === "" ? undefined : value,
    z.string().trim().min(1).max(maximum).optional()
  );

const evidenceUriSchema = z.preprocess(
  (value) =>
    typeof value === "string" && value.trim() === "" ? undefined : value,
  z.string()
    .trim()
    .max(2048)
    .regex(/^(?:https|s3|gs|az):\/\/\S+$/)
    .optional()
);

const payloadFields = {
  evidenceUri: evidenceUriSchema,
  notes: optionalTrimmedText(2000),
  occurredAt: z.iso.datetime({ offset: true }),
  quantity: z.number()
    .finite()
    .positive()
    .max(99_999_999_999_999.9999)
    .multipleOf(0.0001)
    .optional(),
  unit: unitSchema.optional()
} as const;

export const appendWorkLogSchema = z.object({
  activityId: z.uuid(),
  employeeId: z.uuid(),
  ...payloadFields
}).strict().superRefine((value, context) => {
  validateQuantityAndUnit(value, context);
  if (!value.notes && value.quantity === undefined && !value.evidenceUri) {
    context.addIssue({
      code: "custom",
      message: "Nhập ghi chú, số lượng hoặc URI bằng chứng.",
      path: ["notes"]
    });
  }
});

export const correctWorkLogSchema = z.object({
  activityId: z.uuid(),
  correctionKind: z.enum(["REPLACE", "VOID"]),
  correctionReason: z.string().trim().min(1).max(500),
  logId: z.uuid(),
  ...payloadFields
}).strict().superRefine((value, context) => {
  validateQuantityAndUnit(value, context);
  if (value.correctionKind === "VOID") {
    if (
      value.quantity !== undefined
      || value.unit !== undefined
      || value.evidenceUri !== undefined
    ) {
      context.addIssue({
        code: "custom",
        message: "Bản hủy hiệu lực không được chứa số lượng hoặc bằng chứng.",
        path: ["correctionKind"]
      });
    }
    return;
  }
  if (!value.notes && value.quantity === undefined && !value.evidenceUri) {
    context.addIssue({
      code: "custom",
      message: "Bản thay thế phải có ghi chú, số lượng hoặc URI bằng chứng.",
      path: ["notes"]
    });
  }
});

type QuantityAndUnit = Readonly<{
  quantity?: number;
  unit?: z.infer<typeof unitSchema>;
}>;

function validateQuantityAndUnit(
  value: QuantityAndUnit,
  context: z.RefinementCtx
): void {
  if ((value.quantity === undefined) !== (value.unit === undefined)) {
    context.addIssue({
      code: "custom",
      message: "Số lượng và đơn vị phải được gửi cùng nhau.",
      path: value.quantity === undefined ? ["quantity"] : ["unit"]
    });
  }
}

export type AppendWorkLogInput = z.infer<typeof appendWorkLogSchema>;
export type CorrectWorkLogInput = z.infer<typeof correctWorkLogSchema>;
