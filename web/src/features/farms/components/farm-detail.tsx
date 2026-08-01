import Link from "next/link";

import { ReviewedVisual } from "@/components/media/reviewed-visual";
import { StatePanel } from "@/components/app-shell/state-panels";
import { YieldForecastPanel } from "@/features/farms/components/yield-forecast-panel";
import type { FarmDetailViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import {
  AnalyticsContextLine,
  LineageBanner
} from "@/features/overview/components/lineage-banner";
import { VISUAL_CATALOG_BY_AREA } from "@/lib/visual-catalog";
import styles from "@/features/overview/components/overview-farms.module.css";

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});
const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

export function FarmDetail({
  viewModel,
  backHref,
  forecastPageHref
}: {
  viewModel: FarmDetailViewModel;
  backHref: string;
  forecastPageHref: (offset: number) => string;
}) {
  const analytic = viewModel.analytics.status === "ready"
    ? viewModel.analytics.data.payload.items.find(
        (item) => item.farmCode === viewModel.farm.code
      )
    : null;
  const visual = VISUAL_CATALOG_BY_AREA.get("farms");
  return (
    <div className={styles.stack}>
      <header className={styles.pageIntro}>
        <div>
          <p className="eyebrow">Nông trại · <span translate="no">{viewModel.farm.code}</span></p>
          <h2>{viewModel.farm.displayName}</h2>
          <p>Hiệu quả và trạng thái trong phạm vi dữ liệu đã xác minh.</p>
        </div>
        <Link className={styles.primaryLink} href={backHref}>Quay lại danh sách</Link>
      </header>
      {viewModel.analytics.status === "failed" ? (
        <StatePanel
          actionHref={backHref}
          actionLabel="Quay lại danh sách"
          correlationId={viewModel.analytics.correlationId}
          label="Chỉ số phân tích tạm gián đoạn"
          message="Thông tin nông trại vẫn khả dụng, nhưng chỉ số hiệu quả chưa thể tải ở lần thử này."
          state="partial"
        />
      ) : (
        <LineageBanner envelope={viewModel.analytics.data} />
      )}
      <div className={styles.farmDetailGrid}>
        <section className={styles.farmDetail}>
          <div className={styles.sectionHeading}><div><p className="eyebrow">Thông tin nông trại</p><h3>Trạng thái hiện hành</h3></div></div>
          <dl>
            <div><dt>Mã nông trại</dt><dd translate="no">{viewModel.farm.code}</dd></div>
            <div><dt>Trạng thái</dt><dd>{viewModel.farm.active ? "Đang hoạt động" : "Ngừng hoạt động"}</dd></div>
          </dl>
          <details className={styles.technicalDetails}>
            <summary>Thông tin kỹ thuật</summary>
            <dl>
              <div><dt>UUID</dt><dd translate="no">{viewModel.farm.id}</dd></div>
              <div><dt>Phiên bản bản ghi</dt><dd>v{viewModel.farm.version}</dd></div>
            </dl>
          </details>
        </section>
        <section className={styles.farmDetail}>
          <div className={styles.sectionHeading}>
            <div>
              <p className="eyebrow">Hiệu quả nông trại</p>
              <h3>Chỉ số phân tích</h3>
              {viewModel.analytics.status === "ready" ? (
                <AnalyticsContextLine envelope={viewModel.analytics.data} period="Kỳ hiệu quả hiện hành" />
              ) : null}
            </div>
          </div>
          {analytic ? (
            <dl>
              <div><dt>Doanh thu</dt><dd>{currency.format(analytic.totalRevenueVnd)}</dd></div>
              <div><dt>Lợi nhuận</dt><dd>{currency.format(analytic.profitVnd)}</dd></div>
              <div><dt>Biên lợi nhuận</dt><dd>{number.format(analytic.profitMarginPct)}%</dd></div>
              <div><dt>Diện tích canh tác</dt><dd>{number.format(analytic.cultivatedAreaHa)} ha</dd></div>
              <div><dt>Sản lượng/ha</dt><dd>{number.format(analytic.yieldKgPerHa)} kg/ha</dd></div>
            </dl>
          ) : (
            <StatePanel
              actionHref={null}
              label="Chưa có chỉ số"
              message="Thông tin nông trại đã sẵn sàng, nhưng chưa có bản ghi phân tích cho kỳ hiện hành."
              state="empty"
            />
          )}
        </section>
      </div>
      {viewModel.forecast.status === "ready" ? (
        <YieldForecastPanel
          forecast={viewModel.forecast.data}
          forecastPageHref={forecastPageHref}
        />
      ) : (
        <StatePanel
          actionHref={backHref}
          actionLabel="Quay lại danh sách"
          correlationId={viewModel.forecast.correlationId}
          label="Bằng chứng dự báo tạm gián đoạn"
          message="Thông tin nông trại và chỉ số hiệu quả vẫn có thể dùng, nhưng dự báo năng suất chưa tải được ở lần này."
          state="partial"
        />
      )}
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
  );
}
