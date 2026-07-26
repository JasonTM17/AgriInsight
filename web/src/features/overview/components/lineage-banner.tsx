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
        <strong>
          {freshnessLabel} · cập nhật {formatAge(envelope.freshness.artifactAgeHours)} trước
        </strong>
      </div>
      <div>
        <span>Kỳ phân tích</span>
        <strong>{formatAppliedPeriod(envelope, "Toàn bộ dữ liệu đã xác minh")}</strong>
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
        <strong className={styles.technical} translate="no">
          {envelope.lineage.manifestFingerprint}
        </strong>
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
      {formatScope(envelope.scope)} · {formatAppliedPeriod(envelope, period)} ·{" "}
      {formatFreshnessStatus(envelope)} · dữ liệu đến {formatDate(envelope.lineage.asOf)}
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
  const applied = scope.appliedFilter;
  if (applied) {
    const dimensions = [
      applied.farmCode ? `Nông trại ${applied.farmCode}` : null,
      applied.fieldCode ? `Khu vực ${applied.fieldCode}` : null,
      applied.cropCode ? `Cây trồng ${applied.cropCode}` : null,
      applied.seasonCode ? `Mùa vụ ${applied.seasonCode}` : null
    ].filter((value): value is string => value !== null);
    if (dimensions.length > 0) return dimensions.join(" · ");
  }
  if (scope.tenantWide) return "Toàn doanh nghiệp";
  const farmCount = scope.farmCodes?.length ?? 0;
  if (farmCount > 0) return `${farmCount} nông trại được cấp quyền`;
  return "Phạm vi được cấp quyền";
}

function formatAppliedPeriod(
  envelope: AnalyticsMetadata,
  fallback: string
): string {
  const applied = envelope.scope.appliedFilter;
  if (!applied || applied.datePreset === "all") return fallback;
  const range = applied.dateFrom && applied.dateTo
    ? `${formatDate(applied.dateFrom)} – ${formatDate(applied.dateTo)}`
    : fallback;
  return applied.datePreset === "last-30-days"
    ? `30 ngày gần nhất (${range})`
    : `Từ đầu mùa vụ (${range})`;
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
