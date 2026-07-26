"use client";

import { useRef, useState } from "react";

import { postInventoryMutation } from "./inventory-api-client";

export type InventoryMutationFeedback = Readonly<{
  correlationId?: string;
  kind: "error" | "success";
  message: string;
}>;

export type InventorySubmitOutcome = "ambiguous" | "rejected" | "success";

export function useIdempotentInventoryMutation(successMessage: string) {
  const [feedback, setFeedback] =
    useState<InventoryMutationFeedback | null>(null);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const fingerprint = useRef<string | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  async function submit(
    path: string,
    payload: unknown,
    ifMatch?: string
  ): Promise<InventorySubmitOutcome> {
    if (inFlight.current) return "rejected";
    const nextFingerprint = JSON.stringify([path, payload, ifMatch]);
    if (fingerprint.current !== nextFingerprint || !idempotencyKey.current) {
      fingerprint.current = nextFingerprint;
      idempotencyKey.current = crypto.randomUUID();
    }
    inFlight.current = true;
    setPending(true);
    setFeedback(null);
    try {
      const result = await postInventoryMutation(
        path,
        payload,
        idempotencyKey.current,
        ifMatch
      );
      if (!result.ok) {
        setFeedback({
          correlationId: result.problem.correlationId,
          kind: "error",
          message: result.problem.title
        });
        return result.ambiguous ? "ambiguous" : "rejected";
      }
      fingerprint.current = null;
      idempotencyKey.current = null;
      setFeedback({ kind: "success", message: successMessage });
      window.location.reload();
      return "success";
    } catch {
      setFeedback({
        kind: "error",
        message:
          "Chưa nhận được xác nhận. Thử lại giữ nguyên khóa chống trùng."
      });
      return "ambiguous";
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
