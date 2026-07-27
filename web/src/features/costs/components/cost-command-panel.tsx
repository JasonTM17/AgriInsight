"use client";

import type { FormEvent } from "react";
import { useState } from "react";

import type { OperatingCostEntry } from "../cost-generated-contract-schemas";
import { useIdempotentWorkMutation } from "@/features/work/use-idempotent-work-mutation";
import styles from "./cost-analysis.module.css";

export function CostCommandPanel({
  entries
}: Readonly<{ entries: readonly OperatingCostEntry[] }>) {
  const [targetType, setTargetType] = useState("TENANT");
  const append = useIdempotentWorkMutation("Máy chủ đã ghi nhận chi phí vận hành.");
  const correction = useIdempotentWorkMutation("Máy chủ đã ghi nhận correction bất biến.");

  async function submitAppend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const target = readTarget(form, targetType, append.setLocalError);
    if (!target) return;
    await append.submit("/api/costs/entries", {
      ...target,
      amountVnd: Number(form.get("amountVnd")),
      category: String(form.get("category")),
      description: optional(form.get("description")),
      occurredAt: toUtcInstant(String(form.get("occurredAt"))),
      sourceReference: optional(form.get("sourceReference"))
    });
  }

  async function submitCorrection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const entryId = String(form.get("entryId") ?? "");
    if (!entryId) {
      correction.setLocalError("Chọn một posting để correction.");
      return;
    }
    await correction.submit(`/api/costs/entries/${encodeURIComponent(entryId)}/corrections`, {
      amountVnd: Number(form.get("correctionAmountVnd")),
      category: String(form.get("correctionCategory")),
      correctionReason: String(form.get("correctionReason") ?? ""),
      description: optional(form.get("correctionDescription")),
      occurredAt: toUtcInstant(String(form.get("correctionOccurredAt"))),
      sourceReference: optional(form.get("correctionSourceReference")),
      targetId: optional(form.get("correctionTargetId")),
      targetType: String(form.get("correctionTargetType"))
    });
  }

  return (
    <section className={styles.commandPanel} aria-labelledby="cost-command-title">
      <div className={styles.panelHeading}>
        <div><p className="eyebrow">COST_MANAGER COMMANDS</p><h2 id="cost-command-title">Ghi nhận & correction</h2></div>
        <span>Append-only · idempotent</span>
      </div>
      <div className={styles.commandGrid}>
        <form className={styles.commandForm} onSubmit={submitAppend}>
          <h3>Posting mới</h3>
          <label>Số tiền VND<input min="1" name="amountVnd" required type="number" /></label>
          <label>Nhóm<select defaultValue="MATERIAL" name="category"><option value="LABOR">Nhân công</option><option value="MATERIAL">Vật tư</option><option value="MACHINERY">Máy móc</option><option value="TRANSPORT">Vận chuyển</option><option value="UTILITY">Tiện ích</option><option value="OTHER">Khác</option></select></label>
          <label>Phân bổ<select name="targetType" onChange={(event) => setTargetType(event.currentTarget.value)} value={targetType}><option value="TENANT">Tenant</option><option value="FARM">Farm</option><option value="FIELD">Field</option><option value="SEASON">Season</option><option value="ACTIVITY">Activity</option></select></label>
          {targetType === "TENANT" ? null : <label>Target ID<input name="targetId" pattern="[0-9a-fA-F-]{36}" required /></label>}
          <label>Thời điểm UTC<input defaultValue={localDateTimeValue()} name="occurredAt" required type="datetime-local" /></label>
          <label>Mô tả<input name="description" /></label>
          <label>Tham chiếu<input name="sourceReference" /></label>
          <Feedback feedback={append.feedback} />
          <button className={styles.primaryButton} disabled={append.pending} type="submit">{append.pending ? "Đang ghi…" : "Ghi posting"}</button>
        </form>
        <form className={styles.commandForm} onSubmit={submitCorrection}>
          <h3>Correction bất biến</h3>
          <label>Posting cần sửa<select defaultValue="" name="entryId" required><option disabled value="">Chọn posting</option>{entries.filter((entry) => entry.entryKind === "POSTING").map((entry) => <option key={entry.id} value={entry.id}>{entry.category} · {entry.id.slice(0, 8)} · {entry.signedAmountVnd.toLocaleString("vi-VN")} VND</option>)}</select></label>
          <label>Số tiền thay thế<input min="1" name="correctionAmountVnd" required type="number" /></label>
          <label>Nhóm thay thế<select defaultValue="MATERIAL" name="correctionCategory"><option value="LABOR">Nhân công</option><option value="MATERIAL">Vật tư</option><option value="MACHINERY">Máy móc</option><option value="TRANSPORT">Vận chuyển</option><option value="UTILITY">Tiện ích</option><option value="OTHER">Khác</option></select></label>
          <label>Phân bổ thay thế<select defaultValue="TENANT" name="correctionTargetType"><option value="TENANT">Tenant</option><option value="FARM">Farm</option><option value="FIELD">Field</option><option value="SEASON">Season</option><option value="ACTIVITY">Activity</option></select></label>
          <label>Target ID thay thế<input name="correctionTargetId" pattern="[0-9a-fA-F-]{36}" /></label>
          <label>Lý do correction<textarea maxLength={500} name="correctionReason" required /></label>
          <label>Thời điểm UTC<input defaultValue={localDateTimeValue()} name="correctionOccurredAt" required type="datetime-local" /></label>
          <Feedback feedback={correction.feedback} />
          <button className={styles.secondaryButton} disabled={correction.pending} type="submit">{correction.pending ? "Đang gửi…" : "Gửi correction"}</button>
        </form>
      </div>
    </section>
  );
}

function Feedback({
  feedback
}: Readonly<{ feedback: { kind: "error" | "success"; message: string; correlationId?: string } | null }>) {
  if (!feedback) return null;
  return <p className={feedback.kind === "error" ? styles.commandError : styles.commandSuccess} role="status">{feedback.message}{feedback.correlationId ? ` · ${feedback.correlationId}` : ""}</p>;
}

function readTarget(
  form: FormData,
  targetType: string,
  setError: (message: string) => void
): Readonly<{ targetId?: string; targetType: string }> | null {
  const targetId = optional(form.get("targetId"));
  if (targetType !== "TENANT" && !targetId) {
    setError("Phân bổ cấp farm trở xuống yêu cầu target ID.");
    return null;
  }
  return { targetId, targetType };
}

function optional(value: FormDataEntryValue | null): string | undefined {
  if (typeof value !== "string") return undefined;
  const normalized = value.trim();
  return normalized === "" ? undefined : normalized;
}

function toUtcInstant(value: string): string {
  return value ? `${value}:00Z` : new Date().toISOString();
}

function localDateTimeValue(): string {
  return new Date().toISOString().slice(0, 16);
}
