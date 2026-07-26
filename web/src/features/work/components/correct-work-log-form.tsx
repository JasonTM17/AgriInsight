"use client";

import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";

import type { WorkActivityLog } from "../work-generated-client-adapter";
import { useIdempotentWorkMutation } from "../use-idempotent-work-mutation";
import {
  toLocalDateTimeInput,
  useLocalWorkTime
} from "../use-local-work-time";
import {
  commonWorkLogPayload,
  requiredText,
  WorkLogFields
} from "./work-log-fields";
import { WorkMutationFeedbackView } from "./work-mutation-feedback";
import styles from "./work-operations.module.css";

export function CorrectWorkLogForm({
  activityId,
  log
}: Readonly<{ activityId: string; log: WorkActivityLog }>) {
  const [correctionKind, setCorrectionKind] =
    useState<"REPLACE" | "VOID">("REPLACE");
  const [occurredAt, setOccurredAt] = useLocalWorkTime();
  const mutation = useIdempotentWorkMutation(
    "Máy chủ đã nối bản hiệu chỉnh vào lịch sử."
  );

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const common = commonWorkLogPayload(form);
    if ((common.quantity === undefined) !== (common.unit === undefined)) {
      mutation.setLocalError("Số lượng và đơn vị phải được nhập cùng nhau.");
      return;
    }
    if (
      correctionKind === "REPLACE"
      && !common.notes
      && common.quantity === undefined
      && !common.evidenceUri
    ) {
      mutation.setLocalError(
        "Bản thay thế phải có ghi chú, số lượng hoặc URI bằng chứng."
      );
      return;
    }
    const saved = await mutation.submit(
      `/api/work/activities/${encodeURIComponent(activityId)}/logs/`
        + `${encodeURIComponent(log.id)}/corrections`,
      {
        ...common,
        correctionKind,
        correctionReason: requiredText(form, "correctionReason")
      }
    );
    if (saved) {
      formElement.reset();
      setCorrectionKind("REPLACE");
      setOccurredAt(toLocalDateTimeInput(new Date()));
    }
  }

  return (
    <form className={styles.formCard} onSubmit={onSubmit}>
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Append-only</p>
          <h3>Hiệu chỉnh bản ghi</h3>
        </div>
        <span className={styles.lineageBadge}>
          Gốc {log.id.slice(0, 8).toUpperCase()}
        </span>
      </div>
      <label className={styles.field}>
        <span>Cách hiệu chỉnh</span>
        <select
          name="correctionKind"
          onChange={(event) => {
            setCorrectionKind(event.currentTarget.value as "REPLACE" | "VOID");
            mutation.clearFeedback();
          }}
          value={correctionKind}
        >
          <option value="REPLACE">Thêm bản thay thế</option>
          <option value="VOID">Hủy hiệu lực bản gốc</option>
        </select>
      </label>
      <label className={styles.field}>
        <span>Lý do hiệu chỉnh</span>
        <textarea
          maxLength={500}
          name="correctionReason"
          placeholder="Nêu lý do có thể kiểm toán"
          required
          rows={2}
        />
      </label>
      <WorkLogFields
        occurredAt={occurredAt}
        onOccurredAtChange={(event) => {
          setOccurredAt(event.currentTarget.value);
          mutation.clearFeedback();
        }}
        voidMode={correctionKind === "VOID"}
      />
      <WorkMutationFeedbackView feedback={mutation.feedback} />
      <div className={styles.formActions}>
        <Button
          data-testid="submit-work-correction"
          disabled={mutation.pending || !occurredAt}
          type="submit"
        >
          {mutation.pending ? "Đang gửi…" : "Gửi bản hiệu chỉnh"}
        </Button>
      </div>
    </form>
  );
}
