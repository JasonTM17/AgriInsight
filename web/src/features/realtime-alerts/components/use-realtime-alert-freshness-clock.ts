"use client";

import { useEffect, useState } from "react";

const FRESHNESS_TICK_MS = 30_000;

export function useRealtimeAlertFreshnessClock(): number {
  const [nowMs, setNowMs] = useState(0);

  useEffect(() => {
    const updateNow = () => setNowMs(Date.now());
    const initialTimer = window.setTimeout(updateNow, 0);
    const interval = window.setInterval(updateNow, FRESHNESS_TICK_MS);

    return () => {
      window.clearTimeout(initialTimer);
      window.clearInterval(interval);
    };
  }, []);

  return nowMs;
}
