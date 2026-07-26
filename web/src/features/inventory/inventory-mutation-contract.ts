import { z } from "zod";

const uuidSchema = z.uuid();

const quantityBaseSchema = z.number()
  .finite()
  .positive()
  .max(99_999_999_999_999.9999)
  .multipleOf(0.0001);

const unitCostVndSchema = z.number()
  .finite()
  .nonnegative()
  .max(9_999_999_999_999_999.99)
  .multipleOf(0.01);

const optionalTrimmedText = (maximum: number) =>
  z.preprocess(
    (value) =>
      typeof value === "string" && value.trim() === "" ? undefined : value,
    z.string().trim().min(1).max(maximum).optional()
  );

export const postInventoryTransactionSchema = z.object({
  kind: z.enum(["RECEIPT", "ISSUE"]),
  warehouseId: uuidSchema,
  materialId: uuidSchema,
  supplierId: uuidSchema.optional(),
  quantityBase: quantityBaseSchema,
  unitCostVnd: unitCostVndSchema.optional(),
  batchCode: optionalTrimmedText(64),
  expiryDate: z.iso.date().optional(),
  stockLotId: uuidSchema.optional(),
  occurredAt: z.iso.datetime({ offset: true }),
  reason: optionalTrimmedText(500),
  referenceCode: optionalTrimmedText(128)
}).strict().superRefine((value, context) => {
  if (value.kind === "RECEIPT") {
    validateReceiptShape(value, context);
    if (
      value.expiryDate
      && value.occurredAt
      && value.expiryDate < value.occurredAt.slice(0, 10)
    ) {
      issue(
        context,
        "expiryDate",
        "Ngày hết hạn không được trước ngày nhập kho."
      );
    }
    return;
  }
  validateIssueShape(value, context);
});

export const reversalInventoryTransactionSchema = z.object({
  transactionId: uuidSchema,
  quantityBase: quantityBaseSchema,
  reason: z.string().trim().min(1).max(500)
}).strict();

export type PostInventoryTransactionInput = z.infer<
  typeof postInventoryTransactionSchema
>;
export type ReversalInventoryTransactionInput = z.infer<
  typeof reversalInventoryTransactionSchema
>;

type PostShape = Readonly<{
  supplierId?: string;
  unitCostVnd?: number;
  batchCode?: string;
  expiryDate?: string;
  stockLotId?: string;
  reason?: string;
}>;

function validateReceiptShape(
  value: PostShape,
  context: z.RefinementCtx
): void {
  if (!value.supplierId) {
    issue(context, "supplierId", "Phiếu nhập kho yêu cầu nhà cung cấp.");
  }
  if (value.unitCostVnd === undefined) {
    issue(context, "unitCostVnd", "Phiếu nhập kho yêu cầu đơn giá VND.");
  }
  if (!value.batchCode) {
    issue(context, "batchCode", "Phiếu nhập kho yêu cầu mã lô hàng.");
  }
  if (!value.expiryDate) {
    issue(context, "expiryDate", "Phiếu nhập kho yêu cầu ngày hết hạn.");
  }
  if (value.stockLotId !== undefined) {
    issue(context, "stockLotId", "Phiếu nhập kho không được chứa lô xuất kho.");
  }
  if (value.reason !== undefined) {
    issue(context, "reason", "Phiếu nhập kho không được chứa lý do xuất kho.");
  }
}

function validateIssueShape(
  value: PostShape,
  context: z.RefinementCtx
): void {
  if (!value.reason) {
    issue(context, "reason", "Phiếu xuất kho yêu cầu lý do xuất kho.");
  }
  if (value.supplierId !== undefined) {
    issue(context, "supplierId", "Phiếu xuất kho không được chứa nhà cung cấp.");
  }
  if (value.unitCostVnd !== undefined) {
    issue(context, "unitCostVnd", "Phiếu xuất kho không được chứa đơn giá VND.");
  }
  if (value.batchCode !== undefined) {
    issue(context, "batchCode", "Phiếu xuất kho không được chứa mã lô hàng.");
  }
  if (value.expiryDate !== undefined) {
    issue(context, "expiryDate", "Phiếu xuất kho không được chứa ngày hết hạn.");
  }
}

function issue(
  context: z.RefinementCtx,
  path: string,
  message: string
): void {
  context.addIssue({ code: "custom", message, path: [path] });
}
