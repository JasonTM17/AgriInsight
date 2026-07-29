"use client";

import {
  useCallback,
  useEffect,
  useRef,
  type Dispatch,
  type SetStateAction
} from "react";

import { getRealtimeOperationalAlerts } from "../realtime-alert-client";
import {
  beginRealtimeAlertFeedLoad,
  failRealtimeAlertFeedLoad,
  handleRealtimeAlertFeedFailure,
  isRealtimeAlertPollingEligible,
  REALTIME_ALERT_STALE_AFTER_MS,
  receiveRealtimeAlertFeed,
  type RealtimeAlertPanelState
} from "../realtime-alert-panel-state";

const POLL_INTERVAL_MS = 30_000;
export function useRealtimeAlertFeed({
  enabled,
  setLiveMessage,
  setState
}: Readonly<{
  enabled: boolean;
  setLiveMessage: Dispatch<SetStateAction<string>>;
  setState: Dispatch<SetStateAction<RealtimeAlertPanelState>>;
}>) {
  const requestRef = useRef<AbortController | null>(null);

  const loadFeed = useCallback(async () => {
    if (requestRef.current) return;

    const controller = new AbortController();
    requestRef.current = controller;
    setState((current) => beginRealtimeAlertFeedLoad(current));
    setLiveMessage("Đang làm mới cảnh báo vận hành realtime.");

    try {
      const result = await getRealtimeOperationalAlerts(controller.signal);
      if (controller.signal.aborted) return;

      if (result.ok) {
        setState((current) =>
          receiveRealtimeAlertFeed(
            current,
            result.data,
            Date.now(),
            REALTIME_ALERT_STALE_AFTER_MS
          )
        );
        setLiveMessage(
          result.data.items.length === 0
            ? "Không có cảnh báo vận hành realtime đang mở."
            : `Đã tải ${result.data.items.length} cảnh báo vận hành realtime.`
        );
        return;
      }

      setState((current) =>
        handleRealtimeAlertFeedFailure(
          current,
          result.problem,
          result.problem.status
        )
      );
      setLiveMessage(result.problem.title);
    } catch (cause) {
      if (isAbortError(cause)) return;
      setState((current) =>
        failRealtimeAlertFeedLoad(current, {
          code: "realtime_alert_unavailable",
          title: "Không thể tải cảnh báo vận hành lúc này."
        })
      );
      setLiveMessage("Không thể tải cảnh báo vận hành lúc này.");
    } finally {
      if (requestRef.current === controller) requestRef.current = null;
    }
  }, [setLiveMessage, setState]);

  useEffect(() => {
    if (!enabled) return;

    const initialLoadTimer = window.setTimeout(() => void loadFeed(), 0);
    const refreshWhenEligible = () => {
      if (
        isRealtimeAlertPollingEligible(
          true,
          document.visibilityState,
          requestRef.current !== null
        )
      ) {
        void loadFeed();
      }
    };
    const pollTimer = window.setInterval(
      refreshWhenEligible,
      POLL_INTERVAL_MS
    );

    document.addEventListener("visibilitychange", refreshWhenEligible);
    return () => {
      window.clearTimeout(initialLoadTimer);
      window.clearInterval(pollTimer);
      document.removeEventListener("visibilitychange", refreshWhenEligible);
      const controller = requestRef.current;
      requestRef.current = null;
      controller?.abort();
    };
  }, [enabled, loadFeed]);

  return loadFeed;
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === "AbortError";
}
