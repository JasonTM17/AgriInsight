import type { AdminMutationFeedback } from "../use-idempotent-admin-mutation";
import styles from "./admin-mutations.module.css";

export function AdminMutationFeedbackView({
  feedback
}: Readonly<{ feedback: AdminMutationFeedback | null }>) {
  if (!feedback) return null;
  return (
    <p
      aria-live="polite"
      className={feedback.kind === "success" ? styles.success : styles.error}
      role={feedback.kind === "error" ? "alert" : "status"}
    >
      {feedback.message}
      {feedback.correlationId ? (
        <small>Mã tương quan: <span translate="no">{feedback.correlationId}</span></small>
      ) : null}
    </p>
  );
}
