"use client";

import { useRef, useState } from "react";

import {
  adminMutationCommandSchema,
  type AdminMutationCommand
} from "./admin-mutation-contract";
import { postAdminMutation } from "./admin-api-client";

export type AdminMutationFeedback = Readonly<{
  correlationId?: string;
  kind: "error" | "success";
  message: string;
}>;

export function useIdempotentAdminMutation(successMessage: string) {
  const [feedback, setFeedback] = useState<AdminMutationFeedback | null>(null);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const fingerprint = useRef<string | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  async function submit(raw: unknown, ifMatch?: string): Promise<boolean> {
    if (inFlight.current) return false;
    const parsed = adminMutationCommandSchema.safeParse(raw);
    if (!parsed.success) {
      setFeedback({
        kind: "error",
        message: parsed.error.issues[0]?.message ?? "Dữ liệu chưa hợp lệ."
      });
      return false;
    }
    const nextFingerprint = await commandFingerprint(parsed.data, ifMatch);
    if (fingerprint.current !== nextFingerprint || !idempotencyKey.current) {
      fingerprint.current = nextFingerprint;
      idempotencyKey.current = crypto.randomUUID();
    }
    return send(parsed.data, ifMatch);
  }

  async function send(
    command: AdminMutationCommand,
    ifMatch?: string
  ): Promise<boolean> {
    inFlight.current = true;
    setPending(true);
    setFeedback(null);
    try {
      const result = await postAdminMutation(
        command,
        idempotencyKey.current!,
        ifMatch
      );
      if (!result.ok) {
        setFeedback({
          correlationId: result.problem.correlationId,
          kind: "error",
          message: result.problem.title
        });
        if (!result.ambiguous) clearRetryState();
        return false;
      }
      clearRetryState();
      setFeedback({ kind: "success", message: successMessage });
      window.location.reload();
      return true;
    } catch {
      setFeedback({
        kind: "error",
        message: "Chưa nhận được xác nhận. Gửi lại sẽ giữ khóa chống trùng."
      });
      return false;
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  function clearRetryState(): void {
    fingerprint.current = null;
    idempotencyKey.current = null;
  }

  return { feedback, pending, submit } as const;
}

async function commandFingerprint(
  command: AdminMutationCommand,
  ifMatch?: string
): Promise<string> {
  const bytes = new TextEncoder().encode(JSON.stringify([command, ifMatch]));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0")
  ).join("");
}
