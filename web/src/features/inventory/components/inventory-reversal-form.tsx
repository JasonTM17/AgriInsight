"use client";

import { useRef, type FormEvent } from "react";

import type {
  InventoryTransaction,
  Material
} from "../inventory-generated-client-adapter";
import { getInventoryTransactionEtag } from "../inventory-api-client";
import { useIdempotentInventoryMutation } from "../use-idempotent-inventory-mutation";
import { InventoryMutationFeedbackPanel } from "./inventory-mutation-feedback";
import styles from "./inventory-control.module.css";

type PreparedReversal = Readonly<{ etag: string; fingerprint: string }>;

export function InventoryReversalForm({
  materials,
  transactions
}: Readonly<{
  materials: readonly Material[];
  transactions: readonly InventoryTransaction[];
}>) {
  const prepared = useRef<PreparedReversal | null>(null);
  const mutation = useIdempotentInventoryMutation(
    "Bút toán đảo đã được liên kết vào sổ bất biến."
  );
  const reversible = transactions.filter(
    (transaction) => transaction.kind !== "REVERSAL"
  );
  const materialNames = new Map(
    materials.map((material) => [
      material.id,
      `${material.code} · ${material.displayName}`
    ])
  );

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const transactionId = text(form.get("transactionId"));
    const quantityBase = Number(text(form.get("quantityBase")));
    const reason = text(form.get("reason"));
    if (!transactionId || !Number.isFinite(quantityBase) || quantityBase <= 0 || !reason) {
      mutation.setLocalError("Chọn giao dịch, số lượng và nêu rõ lý do đảo.");
      return;
    }
    const path = `/api/inventory/transactions/${transactionId}/reversals`;
    const payload = { quantityBase, reason };
    const fingerprint = JSON.stringify([path, payload]);
    try {
      if (!prepared.current || prepared.current.fingerprint !== fingerprint) {
        prepared.current = {
          etag: await getInventoryTransactionEtag(transactionId),
          fingerprint
        };
      }
      const outcome = await mutation.submit(
        path,
        payload,
        prepared.current.etag
      );
      if (outcome !== "ambiguous") prepared.current = null;
    } catch (error) {
      prepared.current = null;
      mutation.setLocalError(
        error instanceof Error
          ? error.message
          : "Không thể xác minh phiên bản giao dịch nguồn."
      );
    }
  }

  return (
    <form
      className={styles.commandForm}
      data-testid="inventory-reversal-form"
      onSubmit={onSubmit}
    >
      <p className={styles.formHint}>
        Hệ thống lấy lại ETag gốc trước khi đảo; lịch sử cũ không bị sửa.
      </p>
      <div className={styles.formGrid}>
        <label className={styles.formSpan}>
          Giao dịch nguồn
          <select name="transactionId" required>
            {reversible.map((transaction) => (
              <option key={transaction.id} value={transaction.id}>
                {transaction.kind} · {materialNames.get(transaction.materialId) ?? transaction.materialId} · {transaction.quantityBase} {transaction.unit}
              </option>
            ))}
          </select>
        </label>
        <label>
          Số lượng cần đảo
          <input min="0.0001" name="quantityBase" required step="0.0001" type="number" />
        </label>
        <label>
          Lý do hiệu chỉnh
          <input maxLength={500} name="reason" required />
        </label>
      </div>
      <InventoryMutationFeedbackPanel feedback={mutation.feedback} />
      <button
        className={styles.secondaryButton}
        data-testid="inventory-reversal-submit"
        disabled={mutation.pending || reversible.length === 0}
        type="submit"
      >
        {mutation.pending ? "Đang khóa phiên bản…" : "Tạo bút toán đảo"}
      </button>
    </form>
  );
}

function text(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}
