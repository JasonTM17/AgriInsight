"use client";

import { useState, type FormEvent } from "react";

import type {
  Material,
  StockLot,
  Supplier,
  Warehouse
} from "../inventory-generated-client-adapter";
import { useIdempotentInventoryMutation } from "../use-idempotent-inventory-mutation";
import { InventoryMutationFeedbackPanel } from "./inventory-mutation-feedback";
import styles from "./inventory-control.module.css";

export function InventoryTransactionForm({
  lots,
  materials,
  suppliers,
  warehouse
}: Readonly<{
  lots: readonly StockLot[];
  materials: readonly Material[];
  suppliers: readonly Supplier[];
  warehouse: Warehouse;
}>) {
  const [kind, setKind] = useState<"ISSUE" | "RECEIPT">("RECEIPT");
  const mutation = useIdempotentInventoryMutation(
    kind === "RECEIPT"
      ? "Phiếu nhập đã được ghi vào sổ kho."
      : "Phiếu xuất đã được ghi vào sổ kho."
  );

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const occurredAt = toInstant(form.get("occurredAt"));
    const quantityBase = toNumber(form.get("quantityBase"));
    const materialId = form.get("materialId");
    if (!occurredAt || !quantityBase || typeof materialId !== "string") {
      mutation.setLocalError("Kiểm tra vật tư, số lượng và thời điểm giao dịch.");
      return;
    }
    const payload = kind === "RECEIPT"
      ? receiptPayload(form, warehouse.id, materialId, quantityBase, occurredAt)
      : issuePayload(form, warehouse.id, materialId, quantityBase, occurredAt);
    await mutation.submit("/api/inventory/transactions", payload);
  }

  return (
    <form
      className={styles.commandForm}
      data-testid="inventory-transaction-form"
      onSubmit={onSubmit}
    >
      <div className={styles.segmentedControl}>
        <button
          aria-pressed={kind === "RECEIPT"}
          onClick={() => setKind("RECEIPT")}
          type="button"
        >
          Nhập kho
        </button>
        <button
          aria-pressed={kind === "ISSUE"}
          onClick={() => setKind("ISSUE")}
          type="button"
        >
          Xuất kho
        </button>
      </div>
      <div className={styles.formGrid}>
        <label>
          Vật tư
          <select name="materialId" required>
            {materials.map((material) => (
              <option key={material.id} value={material.id}>
                {material.code} · {material.displayName}
              </option>
            ))}
          </select>
        </label>
        <label>
          Số lượng theo đơn vị gốc
          <input min="0.0001" name="quantityBase" required step="0.0001" type="number" />
        </label>
        <label>
          Thời điểm nghiệp vụ
          <input name="occurredAt" required type="datetime-local" />
        </label>
        <label>
          Tham chiếu chứng từ
          <input maxLength={128} name="referenceCode" placeholder="PO / phiếu cấp phát" />
        </label>
        {kind === "RECEIPT" ? (
          <ReceiptFields suppliers={suppliers} />
        ) : (
          <IssueFields lots={lots} />
        )}
      </div>
      <InventoryMutationFeedbackPanel feedback={mutation.feedback} />
      <button
        className={styles.primaryButton}
        data-testid="inventory-transaction-submit"
        disabled={mutation.pending || materials.length === 0}
        type="submit"
      >
        {mutation.pending ? "Đang đối soát…" : `Ghi phiếu ${kind === "RECEIPT" ? "nhập" : "xuất"}`}
      </button>
    </form>
  );
}

function ReceiptFields({ suppliers }: Readonly<{ suppliers: readonly Supplier[] }>) {
  return (
    <>
      <label>
        Nhà cung cấp
        <select name="supplierId" required>
          {suppliers.map((supplier) => (
            <option key={supplier.id} value={supplier.id}>
              {supplier.code} · {supplier.displayName}
            </option>
          ))}
        </select>
      </label>
      <label>
        Đơn giá VND
        <input min="0" name="unitCostVnd" required step="0.01" type="number" />
      </label>
      <label>
        Mã lô
        <input maxLength={64} name="batchCode" required />
      </label>
      <label>
        Ngày hết hạn
        <input name="expiryDate" required type="date" />
      </label>
    </>
  );
}

function IssueFields({ lots }: Readonly<{ lots: readonly StockLot[] }>) {
  return (
    <>
      <label className={styles.formSpan}>
        Lô nguồn (không bắt buộc)
        <select name="stockLotId">
          <option value="">Để backend phân bổ FEFO</option>
          {lots.map((lot) => (
            <option key={lot.id} value={lot.id}>
              {lot.batchCode} · hạn {lot.expiryDate} · còn {lot.availableQuantity} {lot.unit}
            </option>
          ))}
        </select>
      </label>
      <label className={styles.formSpan}>
        Lý do xuất
        <textarea maxLength={500} name="reason" required rows={3} />
      </label>
    </>
  );
}

function receiptPayload(
  form: FormData,
  warehouseId: string,
  materialId: string,
  quantityBase: number,
  occurredAt: string
) {
  return {
    kind: "RECEIPT",
    warehouseId,
    materialId,
    supplierId: text(form.get("supplierId")),
    quantityBase,
    unitCostVnd: toNumber(form.get("unitCostVnd")),
    batchCode: text(form.get("batchCode")),
    expiryDate: text(form.get("expiryDate")),
    occurredAt,
    referenceCode: optionalText(form.get("referenceCode"))
  };
}

function issuePayload(
  form: FormData,
  warehouseId: string,
  materialId: string,
  quantityBase: number,
  occurredAt: string
) {
  return {
    kind: "ISSUE",
    warehouseId,
    materialId,
    quantityBase,
    stockLotId: optionalText(form.get("stockLotId")),
    occurredAt,
    reason: text(form.get("reason")),
    referenceCode: optionalText(form.get("referenceCode"))
  };
}

function text(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

function optionalText(value: FormDataEntryValue | null): string | undefined {
  return text(value) || undefined;
}

function toNumber(value: FormDataEntryValue | null): number | undefined {
  const number = typeof value === "string" ? Number(value) : Number.NaN;
  return Number.isFinite(number) && number >= 0 ? number : undefined;
}

function toInstant(value: FormDataEntryValue | null): string | undefined {
  if (typeof value !== "string" || !value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? undefined : date.toISOString();
}
