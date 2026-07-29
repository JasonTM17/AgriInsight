"use client";

import {
  useCallback,
  useEffect,
  useRef,
  type Dispatch,
  type SetStateAction
} from "react";

import { acknowledgeRealtimeOperationalAlert } from "../realtime-alert-client";
import {
  beginRealtimeAlertAcknowledgement,
  completeRealtimeAlertAcknowledgement,
  getRealtimeAlertAcknowledgement,
  getRealtimeAlertAcknowledgementKey,
  handleRealtimeAlertAcknowledgementFailure,
  markRealtimeAlertAcknowledgementUnknown,
  type RealtimeAlertPanelState
} from "../realtime-alert-panel-state";

export function useRealtimeAlertAcknowledgement({
  setLiveMessage,
  setState,
  state
}: Readonly<{
  setLiveMessage: Dispatch<SetStateAction<string>>;
  setState: Dispatch<SetStateAction<RealtimeAlertPanelState>>;
  state: RealtimeAlertPanelState;
}>) {
  const requestsRef = useRef(new Map<string, AbortController>());

  useEffect(() => {
    const requests = requestsRef.current;
    return () => {
      for (const controller of requests.values()) controller.abort();
      requests.clear();
    };
  }, []);

  return useCallback(async (alertId: string) => {
    if (requestsRef.current.has(alertId)) return;

    const acknowledgement = getRealtimeAlertAcknowledgement(state, alertId);
    if (
      acknowledgement.status === "acknowledging"
      || acknowledgement.status === "acknowledged"
      || acknowledgement.status === "acknowledgement-denied"
      || acknowledgement.status === "alert-unavailable"
    ) {
      return;
    }

    const idempotencyKey =
      getRealtimeAlertAcknowledgementKey(state, alertId)
      ?? window.crypto.randomUUID();
    const controller = new AbortController();
    requestsRef.current.set(alertId, controller);
    setState((current) =>
      beginRealtimeAlertAcknowledgement(current, alertId, idempotencyKey)
    );
    setLiveMessage("Đang gửi xác nhận cảnh báo đến máy chủ.");

    try {
      const result = await acknowledgeRealtimeOperationalAlert(
        alertId,
        idempotencyKey,
        controller.signal
      );
      if (controller.signal.aborted) return;

      if (result.ok) {
        setState((current) =>
          completeRealtimeAlertAcknowledgement(current, alertId, result.data)
        );
        setLiveMessage("Máy chủ đã xác nhận cảnh báo vận hành.");
      } else if (result.ambiguous) {
        setState((current) =>
          markRealtimeAlertAcknowledgementUnknown(current, alertId)
        );
        setLiveMessage("Chưa rõ kết quả xác nhận. Có thể thử lại an toàn.");
      } else {
        setState((current) =>
          handleRealtimeAlertAcknowledgementFailure(
            current,
            alertId,
            result.problem.status
          )
        );
        setLiveMessage(result.problem.title);
      }
    } catch (cause) {
      if (isAbortError(cause)) return;
      setState((current) =>
        markRealtimeAlertAcknowledgementUnknown(current, alertId)
      );
      setLiveMessage("Chưa rõ kết quả xác nhận. Có thể thử lại an toàn.");
    } finally {
      requestsRef.current.delete(alertId);
    }
  }, [setLiveMessage, setState, state]);
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === "AbortError";
}
