"use client";

import { Icon, type IconName } from "@/components/ui/icon";

import type { RealtimeOperationalAlert } from "../realtime-alert-contract";

import styles from "./realtime-alert-panel.module.css";

const displayDateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh"
});

const policyLabels: Readonly<
  Record<RealtimeOperationalAlert["policy"], string>
> = {
  OUTBOX_PUBLISH_BACKLOG: "Tồn đọng xuất bản",
  REALTIME_DELIVERY_LAG: "Chậm phân phối realtime",
  REALTIME_DLT_RECORD: "Bản ghi DLT realtime"
};

const severityDetails: Readonly<
  Record<
    RealtimeOperationalAlert["severity"],
    Readonly<{ icon: IconName; label: string }>
  >
> = {
  WARNING: { icon: "alert-triangle", label: "Cảnh báo" },
  CRITICAL: { icon: "alert-octagon", label: "Nghiêm trọng" }
};

export type RealtimeAlertRowProps = Readonly<{
  alert: RealtimeOperationalAlert;
  processingFreshness?: string;
  canAcknowledge: boolean;
  acknowledging: boolean;
  acknowledged: boolean;
  acknowledgementBlocked: "denied" | "unavailable" | null;
  acknowledgementUnknown: boolean;
  onAcknowledge: () => void;
}>;

export function RealtimeAlertRow({
  alert,
  processingFreshness,
  canAcknowledge,
  acknowledging,
  acknowledged,
  acknowledgementBlocked,
  acknowledgementUnknown,
  onAcknowledge
}: RealtimeAlertRowProps) {
  const severity = severityDetails[alert.severity];
  const isCritical = alert.severity === "CRITICAL";
  const freshnessLabel = processingFreshness?.trim();
  const acknowledgementDisabled =
    !canAcknowledge
    || acknowledging
    || acknowledged
    || acknowledgementBlocked !== null;
  const acknowledgementLabel = getAcknowledgementLabel({
    canAcknowledge,
    acknowledging,
    acknowledged,
    acknowledgementBlocked,
    acknowledgementUnknown
  });

  return (
    <article
      className={`${styles.alertRow} ${
        isCritical ? styles.alertRowCritical : styles.alertRowWarning
      }`}
    >
      <header className={styles.rowHeader}>
        <div className={styles.rowLabels}>
          <span className={styles.sourceLabel}>Vận hành realtime</span>
          <span
            className={`${styles.severity} ${
              isCritical ? styles.severityCritical : styles.severityWarning
            }`}
          >
            <Icon name={severity.icon} size={18} />
            <span>Mức độ: {severity.label}</span>
          </span>
        </div>
        <span className={styles.openState}>Trạng thái: OPEN</span>
      </header>

      <dl className={styles.alertFacts}>
        <div>
          <dt>Chính sách</dt>
          <dd>{policyLabels[alert.policy]}</dd>
        </div>
        <div>
          <dt>Quan sát cuối</dt>
          <dd>
            <time dateTime={alert.lastObservedAt}>
              {formatDateTime(alert.lastObservedAt)}
            </time>
          </dd>
        </div>
        <div>
          <dt>Độ mới xử lý</dt>
          <dd>
            {freshnessLabel || (
              <time dateTime={alert.lastEvaluatedAt}>
                Xử lý lúc {formatDateTime(alert.lastEvaluatedAt)}
              </time>
            )}
          </dd>
        </div>
      </dl>

      <div className={styles.evidence}>
        <span>Bằng chứng</span>
        <strong>
          {alert.evidence.type === "TENANT_BACKLOG"
            ? "Tồn đọng cấp doanh nghiệp"
            : "Sự kiện vận hành"}
        </strong>
        {alert.evidence.type === "OPERATIONAL_EVENT" && alert.evidence.id ? (
          <code className={styles.technical} translate="no">
            {alert.evidence.id}
          </code>
        ) : null}
      </div>

      <div className={styles.acknowledgement}>
        <div>
          <span className={styles.acknowledgementState}>
            {acknowledged
              ? "Đã xác nhận trên máy chủ"
              : acknowledgementBlocked === "denied"
                ? "Phiên không còn quyền xác nhận"
                : acknowledgementBlocked === "unavailable"
                  ? "Cảnh báo không còn khả dụng"
              : acknowledgementUnknown
                ? "Chưa rõ kết quả xác nhận"
                : "Chưa xác nhận"}
          </span>
          {acknowledgementUnknown ? (
            <span className={styles.acknowledgementMeta}>
              Có thể thử lại an toàn với cùng khóa chống trùng.
            </span>
          ) : null}
          {acknowledged && alert.acknowledgedAt ? (
            <time className={styles.acknowledgementMeta} dateTime={alert.acknowledgedAt}>
              Xác nhận lúc {formatDateTime(alert.acknowledgedAt)}
            </time>
          ) : null}
        </div>
        <button
          aria-label={acknowledgementLabel}
          className={styles.acknowledgeButton}
          disabled={acknowledgementDisabled}
          onClick={onAcknowledge}
          type="button"
        >
          {acknowledging
            ? "Đang xác nhận"
            : acknowledged
              ? "Đã xác nhận"
              : acknowledgementBlocked === "denied"
                ? "Không có quyền xác nhận"
                : acknowledgementBlocked === "unavailable"
                  ? "Không còn khả dụng"
              : acknowledgementUnknown
                ? "Thử xác nhận lại"
              : canAcknowledge
                ? "Xác nhận đã xem"
                : "Không thể xác nhận"}
        </button>
      </div>
    </article>
  );
}

function formatDateTime(value: string): string {
  return displayDateTime.format(new Date(value));
}

function getAcknowledgementLabel({
  canAcknowledge,
  acknowledging,
  acknowledged,
  acknowledgementBlocked,
  acknowledgementUnknown
}: Pick<
  RealtimeAlertRowProps,
  | "canAcknowledge"
  | "acknowledging"
  | "acknowledged"
  | "acknowledgementBlocked"
  | "acknowledgementUnknown"
>): string {
  if (acknowledging) return "Đang gửi xác nhận đến máy chủ";
  if (acknowledged) return "Cảnh báo đã được xác nhận trên máy chủ";
  if (!canAcknowledge) return "Không có quyền xác nhận cảnh báo vận hành này";
  if (acknowledgementBlocked === "denied") {
    return "Phiên không còn quyền xác nhận cảnh báo vận hành";
  }
  if (acknowledgementBlocked === "unavailable") {
    return "Cảnh báo vận hành không còn khả dụng";
  }
  if (acknowledgementUnknown) {
    return "Thử lại xác nhận cảnh báo vận hành với cùng khóa chống trùng";
  }
  return "Xác nhận đã xem cảnh báo vận hành này";
}
