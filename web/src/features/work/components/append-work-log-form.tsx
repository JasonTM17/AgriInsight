"use client";

import type { FormEvent } from "react";

import { Button } from "@/components/ui/button";

import type { WorkActivityAssignment } from "../work-generated-client-adapter";
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

export function AppendWorkLogForm({
  activityId,
  assignments
}: Readonly<{
  activityId: string;
  assignments: readonly WorkActivityAssignment[];
}>) {
  const [occurredAt, setOccurredAt] = useLocalWorkTime();
  const mutation = useIdempotentWorkMutation(
    "Máy chủ đã xác nhận bản ghi mới."
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
    if (!common.notes && common.quantity === undefined && !common.evidenceUri) {
      mutation.setLocalError(
        "Nhập ghi chú, số lượng hoặc URI bằng chứng trước khi gửi."
      );
      return;
    }
    const saved = await mutation.submit(
      `/api/work/activities/${encodeURIComponent(activityId)}/logs`,
      {
        ...common,
        employeeId: requiredText(form, "employeeId")
      }
    );
    if (saved) {
      formElement.reset();
      setOccurredAt(toLocalDateTimeInput(new Date()));
    }
  }

  return (
    <form className={styles.formCard} onSubmit={onSubmit}>
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Bản ghi mới</p>
          <h3>Ghi nhận công việc</h3>
        </div>
        <span className={styles.serverOnlyBadge}>Ghi trực tiếp máy chủ</span>
      </div>
      <label className={styles.field}>
        <span>Nhân sự được giao</span>
        <select defaultValue={assignments[0]?.employeeId} name="employeeId" required>
          {assignments.map((assignment) => (
            <option key={assignment.id} value={assignment.employeeId}>
              Mã nhân sự {assignment.employeeId.slice(0, 8).toUpperCase()}
            </option>
          ))}
        </select>
      </label>
      <WorkLogFields
        occurredAt={occurredAt}
        onOccurredAtChange={(event) => {
          setOccurredAt(event.currentTarget.value);
          mutation.clearFeedback();
        }}
      />
      <WorkMutationFeedbackView feedback={mutation.feedback} />
      <div className={styles.formActions}>
        <Button
          data-testid="submit-work-log"
          disabled={mutation.pending || !occurredAt}
          type="submit"
        >
          {mutation.pending ? "Đang gửi…" : "Gửi bản ghi"}
        </Button>
      </div>
    </form>
  );
}
