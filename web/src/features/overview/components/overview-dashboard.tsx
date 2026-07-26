import Link from "next/link";

import { ReviewedVisual } from "@/components/media/reviewed-visual";
import { StatePanel } from "@/components/app-shell/state-panels";
import type { OverviewViewModel } from "@/features/overview/load-overview-view-model";
import {
  toFilterQuery,
  type OverviewFilters
} from "@/features/overview/overview-filter-schema";
import { VISUAL_CATALOG_BY_AREA } from "@/lib/visual-catalog";

import { AnalyticsContextLine, LineageBanner } from "./lineage-banner";
import { MonthlyFinancialTrend } from "./monthly-financial-trend";
import styles from "./overview-farms.module.css";

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});
const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

export function OverviewDashboard({ viewModel }: { viewModel: OverviewViewModel }) {
  const visual = VISUAL_CATALOG_BY_AREA.get("overview");
  return (
    <div className={styles.stack}>
      <header className={styles.pageIntro}>
        <div>
          <p className="eyebrow">Tổng quan doanh nghiệp</p>
          <h2>Điểm cần xem xét</h2>
          <p>Theo dõi hiệu quả, xu hướng và ngoại lệ trong phạm vi dữ liệu đã xác minh.</p>
        </div>
        <Link className={styles.primaryLink} href={currentFarmsHref(viewModel)}>Xem hiệu quả nông trại</Link>
      </header>
      <OverviewPeriodFilter filters={viewModel.filters} />

      {viewModel.analytics.status === "failed" ? (
        <StatePanel
          actionHref={currentOverviewHref(viewModel)}
          correlationId={viewModel.analytics.correlationId}
          label="Phân tích tạm gián đoạn"
          message="Danh mục nông trại vẫn khả dụng, nhưng KPI và xu hướng chưa thể tải ở lần thử này."
          state="partial"
        />
      ) : (
        <>
          <LineageBanner envelope={viewModel.analytics.data} />
          <section aria-label="Chỉ số điều hành" className={styles.summaryBand}>
            <div className={styles.summaryLead}>
              <span>Doanh thu</span>
              <strong>{currency.format(viewModel.analytics.data.payload.summary.totalRevenueVnd)}</strong>
              <AnalyticsContextLine envelope={viewModel.analytics.data} period="Tổng hợp kỳ hiện hành" />
            </div>
            <dl className={styles.summaryMetrics}>
              <div><dt>Lợi nhuận</dt><dd>{currency.format(viewModel.analytics.data.payload.summary.profitVnd)}</dd></div>
              <div><dt>Biên lợi nhuận</dt><dd>{number.format(viewModel.analytics.data.payload.summary.profitMarginPct)}%</dd></div>
              <div><dt>Diện tích canh tác</dt><dd>{number.format(viewModel.analytics.data.payload.summary.cultivatedAreaHa)} ha</dd></div>
              <div><dt>Sản lượng thu hoạch</dt><dd>{number.format(viewModel.analytics.data.payload.summary.harvestQuantityKg)} kg</dd></div>
            </dl>
          </section>
          <MonthlyFinancialTrend
            envelope={viewModel.analytics.data}
            rows={viewModel.analytics.data.payload.monthlyTrend}
          />
          <div className={styles.evidenceGrid}>
            <section className={styles.riskPanel}>
              <div className={styles.sectionHeading}>
                <div>
                  <p className="eyebrow">Ngoại lệ ưu tiên</p>
                  <h3>Rủi ro cần theo dõi</h3>
                  <AnalyticsContextLine envelope={viewModel.analytics.data} period="Tại thời điểm chốt dữ liệu" />
                </div>
                <span>{viewModel.analytics.data.payload.topRisks.length} bản ghi</span>
              </div>
              {viewModel.analytics.data.payload.topRisks.length === 0 ? (
                <StatePanel
                  actionHref={null}
                  label="Không có cảnh báo"
                  message="Không có cảnh báo trong phạm vi và thời điểm chốt dữ liệu này."
                  state="empty"
                />
              ) : (
                <div className={styles.tableScroll} role="region" aria-label="Bảng rủi ro có thể cuộn" tabIndex={0}>
                  <table>
                    <caption className="sr-only">Rủi ro theo nông trại, khu vực và mùa vụ</caption>
                    <thead><tr><th>Nông trại</th><th>Khu vực</th><th>Loại rủi ro</th><th>Trạng thái</th></tr></thead>
                    <tbody>
                      {viewModel.analytics.data.payload.topRisks.slice(0, 8).map((risk, index) => (
                        <tr key={`${risk.seasonCode}-${risk.fieldName}-${index}`}>
                          <td>{risk.farmName}</td>
                          <td>{risk.fieldName}</td>
                          <td>{translateRiskValue(risk.riskType)}</td>
                          <td>{translateRiskValue(risk.status)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
            {visual ? (
              <figure className={styles.contextVisual}>
                <ReviewedVisual
                  alt={visual.alt}
                  filename={visual.filename}
                  height={visual.height}
                  width={visual.width}
                />
                <figcaption><strong>{visual.title}</strong><span>{visual.description}</span><small>Ảnh minh họa bối cảnh — không phải bằng chứng số liệu.</small></figcaption>
              </figure>
            ) : null}
          </div>
        </>
      )}
      {viewModel.farms.status === "failed" ? (
        <StatePanel
          actionHref={currentOverviewHref(viewModel)}
          correlationId={viewModel.farms.correlationId}
          label="Danh mục nông trại tạm gián đoạn"
          message="KPI toàn doanh nghiệp vẫn hiển thị, nhưng số nông trại trong phạm vi chưa thể xác minh."
          state="partial"
        />
      ) : (
        <p className={styles.sourceNote}>{viewModel.farms.data.length} nông trại nghiệp vụ trong phạm vi lọc hiện hành.</p>
      )}
    </div>
  );
}

function OverviewPeriodFilter({ filters }: { filters: OverviewFilters }) {
  return (
    <form action="/overview" className={styles.periodFilter} method="get">
      {filters.farmId ? <input name="farmId" type="hidden" value={filters.farmId} /> : null}
      {filters.fieldId ? <input name="fieldId" type="hidden" value={filters.fieldId} /> : null}
      {filters.cropId ? <input name="cropId" type="hidden" value={filters.cropId} /> : null}
      {filters.seasonId ? <input name="seasonId" type="hidden" value={filters.seasonId} /> : null}
      <label>
        Kỳ dữ liệu
        <select defaultValue={filters.datePreset} name="datePreset">
          <option value="all">Toàn bộ dữ liệu</option>
          <option value="last-30-days">30 ngày gần nhất</option>
          <option disabled={!filters.seasonId} value="season-to-date">Từ đầu mùa vụ</option>
        </select>
      </label>
      <button type="submit">Áp dụng kỳ</button>
      <Link className={styles.resetLink} href="/overview">Xóa bộ lọc</Link>
    </form>
  );
}

function currentOverviewHref(viewModel: OverviewViewModel): string {
  const query = toFilterQuery(viewModel.filters);
  return `/overview${query.size > 0 ? `?${query}` : ""}`;
}

function currentFarmsHref(viewModel: OverviewViewModel): string {
  const query = toFilterQuery(viewModel.filters);
  return `/farms${query.size > 0 ? `?${query}` : ""}`;
}

const RISK_LABELS: Readonly<Record<string, string>> = Object.freeze({
  active: "Đang theo dõi",
  crop_health: "Sức khỏe cây trồng",
  high: "Cao",
  inventory: "Tồn kho",
  medium: "Trung bình",
  open: "Chưa xử lý",
  resolved: "Đã xử lý",
  season: "Mùa vụ"
});

function translateRiskValue(value: string): string {
  const normalized = value.trim().toLowerCase();
  return RISK_LABELS[normalized]
    ?? normalized
      .split(/[_-]+/)
      .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
      .join(" ");
}
