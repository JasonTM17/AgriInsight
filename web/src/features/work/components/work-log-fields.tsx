"use client";

import type { ChangeEvent } from "react";

import styles from "./work-operations.module.css";

export function WorkLogFields({
  occurredAt,
  onOccurredAtChange,
  voidMode = false
}: Readonly<{
  occurredAt: string;
  onOccurredAtChange: (event: ChangeEvent<HTMLInputElement>) => void;
  voidMode?: boolean;
}>) {
  return (
    <>
      <label className={styles.field}>
        <span>Thời điểm ghi nhận</span>
        <input
          name="occurredAt"
          onChange={onOccurredAtChange}
          required
          type="datetime-local"
          value={occurredAt}
        />
      </label>
      <label className={styles.field}>
        <span>Ghi chú hiện trường</span>
        <textarea
          maxLength={2000}
          name="notes"
          placeholder={
            voidMode
              ? "Mô tả ngắn lý do nghiệp vụ nếu cần"
              : "Mô tả kết quả và thao tác đã thực hiện"
          }
          rows={3}
        />
        <small>Tối đa 2.000 ký tự; không ghi dữ liệu cá nhân không cần thiết.</small>
      </label>
      {!voidMode ? (
        <>
          <div className={styles.measureGrid}>
            <label className={styles.field}>
              <span>Số lượng (không bắt buộc)</span>
              <input
                inputMode="decimal"
                min="0.0001"
                name="quantity"
                step="0.0001"
                type="number"
              />
            </label>
            <label className={styles.field}>
              <span>Đơn vị</span>
              <select defaultValue="" name="unit">
                <option value="">Chọn khi có số lượng</option>
                <option value="KG">kg</option>
                <option value="TONNE">tấn</option>
                <option value="LITRE">lít</option>
                <option value="HOUR">giờ</option>
                <option value="HECTARE">ha</option>
                <option value="UNIT">đơn vị</option>
              </select>
            </label>
          </div>
          <label className={styles.field}>
            <span>URI bằng chứng (không bắt buộc)</span>
            <input
              autoCapitalize="none"
              maxLength={2048}
              name="evidenceUri"
              pattern="^(https|s3|gs|az)://\S+$"
              placeholder="https://, s3://, gs:// hoặc az://"
              type="text"
            />
            <small>Chỉ liên kết đã được hệ thống lưu trữ; không tải tệp từ thiết bị.</small>
          </label>
        </>
      ) : null}
    </>
  );
}

export function commonWorkLogPayload(form: FormData) {
  const quantityText = optionalText(form, "quantity");
  const unit = optionalText(form, "unit");
  return {
    evidenceUri: optionalText(form, "evidenceUri"),
    notes: optionalText(form, "notes"),
    occurredAt: new Date(requiredText(form, "occurredAt")).toISOString(),
    quantity: quantityText ? Number(quantityText) : undefined,
    unit: unit || undefined
  };
}

export function optionalText(form: FormData, name: string): string | undefined {
  const value = form.get(name);
  if (typeof value !== "string") return undefined;
  return value.trim() || undefined;
}

export function requiredText(form: FormData, name: string): string {
  return optionalText(form, name) ?? "";
}
