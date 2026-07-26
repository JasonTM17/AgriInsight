import type { InventoryMutationFeedback } from "../use-idempotent-inventory-mutation";
import styles from "./inventory-control.module.css";

export function InventoryMutationFeedbackPanel({
  feedback
}: Readonly<{ feedback: InventoryMutationFeedback | null }>) {
  if (!feedback) return null;
  return (
    <div
      className={
        feedback.kind === "success"
          ? styles.mutationSuccess
          : styles.mutationError
      }
      data-testid="inventory-mutation-feedback"
      role={feedback.kind === "error" ? "alert" : "status"}
    >
      <strong>
        {feedback.kind === "success" ? "Đã ghi nhận" : "Chưa ghi nhận"}
      </strong>
      <span>{feedback.message}</span>
      {feedback.correlationId ? (
        <small>Mã đối soát: {feedback.correlationId}</small>
      ) : null}
    </div>
  );
}
