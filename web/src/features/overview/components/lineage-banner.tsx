import type { AnalyticsOverviewEnvelope } from "@/features/overview/load-overview-view-model";

import styles from "./lineage-banner.module.css";

export type AnalyticsMetadata = Pick<
  AnalyticsOverviewEnvelope,
  "freshness" | "lineage" | "scope"
>;

export function LineageBanner({ envelope }: { envelope: AnalyticsMetadata }) {
  const freshnessLabel = formatFreshnessStatus(envelope);
  return (
    <section aria-label="Nguồn gốc và độ mới dữ liệu" className={styles.banner}>
      <div>
        <span>Phạm vi</span>
        <strong>{formatScope(envelope.scope)}</strong>
      </div>
      <div>
        <span>Độ mới</span>
        <strong>{freshnessLabel} · cập nhật {formatAge(envelope.freshness.artifactAgeHours)} trước</strong>
      </div>
      <div>
        <span>Dữ liệu đến</span>
        <strong>{formatDate(envelope.lineage.asOf)}</strong>
      </div>
      <div>
        <span>Phiên dữ liệu</span>
        <strong className={styles.technical} translate="no">{envelope.lineage.runId}</strong>
      </div>
      <div>
        <span>Phiên bản hợp đồng</span>
        <strong translate="no">{envelope.lineage.contractVersion}</strong>
      </div>
      <div>
        <span>Dấu vân tay dữ liệu</span>
        <strong className={styles.technical} translate="no">{envelope.lineage.manifestFingerprint}</strong>
      </div>
    </section>
  );
}

export function AnalyticsContextLine({
  envelope,
  period
}: {
  envelope: AnalyticsMetadata;
  period: string;
}) {
  return (
    <p className={styles.contextLine}>
      {formatScope(envelope.scope)} · {period} · {formatFreshnessStatus(envelope)} · dữ liệu đến {formatDate(envelope.lineage.asOf)}
    </p>
  );
}

function formatFreshnessStatus(envelope: AnalyticsMetadata): string {
  return envelope.freshness.dataStatus === "current"
    ? "Hiện hành"
    : envelope.freshness.dataStatus === "partial"
      ? "Một phần"
      : envelope.freshness.dataStatus === "stale"
        ? "Đã cũ"
        : "Thiếu dữ liệu";
}

function formatScope(scope: AnalyticsMetadata["scope"]): string {
  if (scope.tenantWide) return "Toàn doanh nghiệp";
  const farmCount = scope.farmCodes?.length ?? 0;
  if (farmCount > 0) return `${farmCount} nông trại được cấp quyền`;
  return "Phạm vi được cấp quyền";
}

function formatAge(hours: number): string {
  if (hours < 1) return "dưới 1 giờ";
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(hours)} giờ`;
}

function formatDate(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.valueOf())) return value;
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeZone: "UTC"
  }).format(parsed);
}
