"use client";

import { useRef, useState } from "react";

import { postWorkMutation } from "./work-api-client";

export type WorkMutationFeedback = Readonly<{
  correlationId?: string;
  kind: "error" | "success";
  message: string;
}>;

export function useIdempotentWorkMutation(successMessage: string) {
  const [feedback, setFeedback] = useState<WorkMutationFeedback | null>(null);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const fingerprint = useRef<string | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  async function submit(path: string, payload: unknown): Promise<boolean> {
    if (inFlight.current) return false;
    const nextFingerprint = JSON.stringify([path, payload]);
    if (fingerprint.current !== nextFingerprint || !idempotencyKey.current) {
      fingerprint.current = nextFingerprint;
      idempotencyKey.current = crypto.randomUUID();
    }
    inFlight.current = true;
    setPending(true);
    setFeedback(null);
    try {
      const result = await postWorkMutation(
        path,
        payload,
        idempotencyKey.current
      );
      if (!result.ok) {
        setFeedback({
          correlationId: result.problem?.correlationId,
          kind: "error",
          message: result.problem?.title ?? "Máy chủ chưa chấp nhận bản ghi."
        });
        return false;
      }
      fingerprint.current = null;
      idempotencyKey.current = null;
      setFeedback({ kind: "success", message: successMessage });
      // Re-read append-only history from the authoritative server. A soft
      // refresh can be coalesced when the previous transition was cancelled.
      window.location.reload();
      return true;
    } catch {
      setFeedback({
        kind: "error",
        message:
          "Chưa nhận được xác nhận từ máy chủ. Thử lại sẽ dùng cùng khóa chống trùng."
      });
      return false;
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  return {
    clearFeedback: () => setFeedback(null),
    feedback,
    pending,
    setLocalError: (message: string) =>
      setFeedback({ kind: "error", message }),
    submit
  } as const;
}
