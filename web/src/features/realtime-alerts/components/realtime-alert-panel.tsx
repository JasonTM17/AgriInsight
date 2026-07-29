"use client";

import { useEffect, useRef, useState } from "react";

import { Icon } from "@/components/ui/icon";

import {
  beginRealtimeAlertFeedLoad,
  createRealtimeAlertPanelState,
  getRealtimeAlertAcknowledgement,
  isRealtimeAlertFeedStale,
  isRealtimeAlertPanelTerminalStatus,
  REALTIME_ALERT_STALE_AFTER_MS,
  type RealtimeAlertPanelStatus
} from "../realtime-alert-panel-state";

import styles from "./realtime-alert-panel.module.css";
import { RealtimeAlertRow } from "./realtime-alert-row";
import { useRealtimeAlertAcknowledgement } from "./use-realtime-alert-acknowledgement";
import { useRealtimeAlertFeed } from "./use-realtime-alert-feed";
import { useRealtimeAlertFreshnessClock } from "./use-realtime-alert-freshness-clock";

const displayDateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh"
});

export function RealtimeAlertPanel({
  canAcknowledge,
  id,
  onClose
}: Readonly<{
  canAcknowledge: boolean;
  id: string;
  onClose: () => void;
}>) {
  const [state, setState] = useState(() =>
    beginRealtimeAlertFeedLoad(createRealtimeAlertPanelState())
  );
  const [liveMessage, setLiveMessage] = useState(
    "Đang tải cảnh báo vận hành realtime."
  );
  const headingRef = useRef<HTMLHeadingElement>(null);
  const nowMs = useRealtimeAlertFreshnessClock();
  const panelTerminal = isRealtimeAlertPanelTerminalStatus(state.status);
  const loadFeed = useRealtimeAlertFeed({
    enabled: !panelTerminal,
    setLiveMessage,
    setState
  });
  const acknowledge = useRealtimeAlertAcknowledgement({
    setLiveMessage,
    setState,
    state
  });

  useEffect(() => {
    const focusFrame = window.requestAnimationFrame(() =>
      headingRef.current?.focus()
    );
    return () => window.cancelAnimationFrame(focusFrame);
  }, []);

  const feed = state.feed;
  const feedIsStale = feed
    ? isRealtimeAlertFeedStale(
        feed.data,
        nowMs,
        REALTIME_ALERT_STALE_AFTER_MS
      )
    : false;
  const statusClassName = [
    styles.statusSummary,
    state.status === "failed" ? styles.statusSummaryError : "",
    state.status === "partial" || state.status === "stale" || feedIsStale
      ? styles.statusSummaryWarning
      : ""
  ].filter(Boolean).join(" ");
  const processingFreshness = feed
    ? `${feedIsStale ? "Dữ liệu có thể đã cũ" : "Cửa sổ máy chủ"} · ${
        formatDateTime(feed.data.generatedAt)
      }`
    : undefined;

  return (
    <section
      aria-describedby={`${id}-status`}
      aria-labelledby={`${id}-title`}
      className={`${styles.panel} ${styles.panelPosition}`}
      id={id}
      role="dialog"
    >
      <header className={styles.panelHeader}>
        <div className={styles.panelHeading}>
          <p className={styles.panelEyebrow}>Field Ledger · Live Operations</p>
          <h2 id={`${id}-title`} ref={headingRef} tabIndex={-1}>
            Cảnh báo vận hành realtime
          </h2>
        </div>
        <div className={styles.panelActions}>
          <button
            aria-label="Làm mới cảnh báo vận hành"
            className={styles.refreshButton}
            disabled={panelTerminal || state.status === "loading"}
            onClick={() => void loadFeed()}
            type="button"
          >
            <Icon name="refresh" size={18} />
          </button>
          <button
            aria-label="Đóng cảnh báo vận hành"
            className={styles.closeButton}
            onClick={onClose}
            type="button"
          >
            <Icon name="x" size={18} />
          </button>
        </div>
      </header>

      <p
        aria-atomic="true"
        aria-live="polite"
        className={statusClassName}
        id={`${id}-status`}
        role="status"
      >
        {liveMessage}
      </p>

      {feed?.data.items.length ? (
        <ul className={styles.alertList}>
          {feed.data.items.map((alert) => {
            const acknowledgement = getRealtimeAlertAcknowledgement(
              state,
              alert.id
            );
            const acknowledged =
              alert.acknowledged
              || acknowledgement.status === "acknowledged";
            const acknowledgementBlocked = acknowledged
              ? null
              : acknowledgement.status === "acknowledgement-denied"
                ? "denied"
                : acknowledgement.status === "alert-unavailable"
                  ? "unavailable"
                  : null;
            return (
              <li key={alert.id}>
                <RealtimeAlertRow
                  acknowledgementBlocked={acknowledgementBlocked}
                  acknowledgementUnknown={
                    !acknowledged
                    && acknowledgement.status === "acknowledgement-unknown"
                  }
                  acknowledged={acknowledged}
                  acknowledging={acknowledgement.status === "acknowledging"}
                  alert={alert}
                  canAcknowledge={canAcknowledge}
                  onAcknowledge={() => void acknowledge(alert.id)}
                  processingFreshness={processingFreshness}
                />
              </li>
            );
          })}
        </ul>
      ) : (
        <p className={styles.emptyState}>
          {getEmptyStateMessage(state.status)}
        </p>
      )}

      {feed?.data.hasMore ? (
        <p className={styles.boundaryNote}>
          Đang hiển thị giới hạn 50 cảnh báo mở mới nhất. Làm mới để đối chiếu
          cửa sổ hiện hành.
        </p>
      ) : null}
    </section>
  );
}

function formatDateTime(value: string): string {
  return displayDateTime.format(new Date(value));
}

function getEmptyStateMessage(status: RealtimeAlertPanelStatus): string {
  if (status === "loading") return "Đang đối chiếu sổ vận hành…";
  if (status === "denied") {
    return "Phiên hiện tại không còn quyền xem cảnh báo vận hành.";
  }
  if (status === "session-expired") {
    return "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.";
  }
  if (status === "failed") {
    return "Dữ liệu chưa khả dụng. Dùng nút làm mới để thử lại.";
  }
  return "Không có cảnh báo mở trong cửa sổ hiện hành.";
}
