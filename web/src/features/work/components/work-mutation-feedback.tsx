import type { WorkMutationFeedback } from "../use-idempotent-work-mutation";

import styles from "./work-operations.module.css";

export function WorkMutationFeedbackView({
  feedback
}: Readonly<{ feedback: WorkMutationFeedback | null }>) {
  if (!feedback) return <div aria-live="polite" />;
  return (
    <div
      className={
        feedback.kind === "success"
          ? styles.feedbackSuccess
          : styles.feedbackError
      }
      data-testid="work-mutation-feedback"
      role={feedback.kind === "error" ? "alert" : "status"}
    >
      <strong>{feedback.message}</strong>
      {feedback.correlationId ? (
        <small>
          Mã tương quan:{" "}
          <span translate="no">{feedback.correlationId}</span>
        </small>
      ) : null}
    </div>
  );
}
