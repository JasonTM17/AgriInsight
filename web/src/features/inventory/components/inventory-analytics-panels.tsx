import type {
  InventoryAnalyticsEnvelope
} from "../inventory-analytics-contract-schema";
import type { SourceResult } from "../load-inventory-view-model";
import {
  formatCurrency,
  formatDate,
  formatQuantity
} from "../inventory-format";
import styles from "./inventory-control.module.css";

export function InventoryAnalyticsPanels({
  analytics,
  hasOperationalFilters,
  selectedWarehouseCode
}: Readonly<{
  analytics: SourceResult<InventoryAnalyticsEnvelope>;
  hasOperationalFilters: boolean;
  selectedWarehouseCode: string;
}>) {
  if (analytics.status === "failed") {
    return (
      <section className={styles.degradedPanel} aria-labelledby="analytics-title">
        <p className="eyebrow">Gold intelligence</p>
        <h2 id="analytics-title">Phân tích tạm thời gián đoạn</h2>
        <p>{analytics.message} Dữ liệu vận hành Spring vẫn hiển thị độc lập.</p>
      </section>
    );
  }
  const payload = analytics.data.payload;
  const freshness = analytics.data.freshness;
  return (
    <section className={styles.analyticsStack} aria-labelledby="analytics-title">
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Gold intelligence · {selectedWarehouseCode}</p>
          <h2 id="analytics-title">Hàng đợi quyết định tồn kho</h2>
          <p>
            {hasOperationalFilters
              ? "Tổng hợp Gold vẫn là toàn kho; bộ lọc vận hành chỉ áp dụng cho các bảng Spring."
              : "ABC, cảnh báo và ngày cung ứng là kết quả đã kiểm chứng từ pipeline analytics."}
          </p>
        </div>
        <span className={styles.snapshotBadge}>
          {freshness.dataStatus} · {freshness.artifactAgeHours} giờ tuổi
        </span>
      </div>
      <div className={styles.kpiGrid}>
        <Kpi label="Giá trị tồn kho" value={formatCurrency(payload.summary.totalInventoryValueVnd)} />
        <Kpi label="SKU thiếu hàng" value={String(payload.summary.lowStockSkus)} />
        <Kpi label="SKU hết hàng" value={String(payload.summary.stockoutSkus)} />
        <Kpi label="Cảnh báo nghiêm trọng" value={String(payload.summary.criticalAlerts)} />
      </div>
      <div className={styles.analyticsGrid}>
        <AlertQueue alerts={payload.alerts} />
        <AbcPanel abc={payload.abc} />
      </div>
      <StatusTable items={payload.items} page={payload.page} />
      <p className={styles.lineageNote}>
        Snapshot sinh lúc {formatDate(analytics.data.lineage.generatedAt)} ·
        lineage {analytics.data.lineage.runId}. Không có trend time-series trong contract Phase 7.
      </p>
    </section>
  );
}

function Kpi({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <article className={styles.kpiCard}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function AlertQueue({
  alerts
}: Readonly<{
  alerts: InventoryAnalyticsEnvelope["payload"]["alerts"];
}>) {
  return (
    <section className={styles.panel} aria-labelledby="alert-title">
      <div className={styles.panelHeading}>
        <div>
          <p className="eyebrow">Action queue</p>
          <h3 id="alert-title">Cảnh báo cần xử lý</h3>
        </div>
        <span>{alerts.length} cảnh báo</span>
      </div>
      {alerts.length === 0 ? (
        <p className={styles.emptyState}>Không có cảnh báo trong snapshot này.</p>
      ) : (
        <ul className={styles.alertList}>
          {alerts.map((alert) => (
            <li key={`${alert.materialCode}-${alert.alertType}`}>
              <span className={`${styles.severity} ${styles[severityClass(alert.severity)]}`}>
                {alert.severity}
              </span>
              <div>
                <strong>{alert.materialCode} · {alert.materialName}</strong>
                <span>{alert.message}</span>
                <small>{alert.recommendedAction}</small>
              </div>
              <b>{formatQuantity(alert.stockQuantity, alert.baseUnit)}</b>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AbcPanel({
  abc
}: Readonly<{ abc: InventoryAnalyticsEnvelope["payload"]["abc"] }>) {
  return (
    <section className={styles.panel} aria-labelledby="abc-title">
      <div className={styles.panelHeading}>
        <div>
          <p className="eyebrow">Value allocation</p>
          <h3 id="abc-title">Phân bổ ABC</h3>
        </div>
        <span>
          {abc.length > 8
            ? `8/${abc.length} nhóm giá trị nhất`
            : `${abc.length} nhóm vật tư`}
        </span>
      </div>
      {abc.length === 0 ? (
        <p className={styles.emptyState}>Chưa có dữ liệu ABC.</p>
      ) : (
        <div className={styles.barList}>
          {abc.slice(0, 8).map((item) => (
            <div key={item.materialCode}>
              <div className={styles.barLabel}>
                <strong>{item.materialCode}</strong>
                <span>{item.abcClass} · {item.valueSharePct.toFixed(1)}%</span>
              </div>
              <div className={styles.barTrack}>
                <i style={{ width: `${Math.min(100, Math.max(0, item.valueSharePct))}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function StatusTable({
  items,
  page
}: Readonly<{
  items: InventoryAnalyticsEnvelope["payload"]["items"];
  page: InventoryAnalyticsEnvelope["payload"]["page"];
}>) {
  return (
    <section className={styles.panel} aria-labelledby="status-title">
      <div className={styles.panelHeading}>
        <div>
          <p className="eyebrow">Stock status</p>
          <h3 id="status-title">Tình trạng theo SKU-location</h3>
        </div>
        <span>
          {page.hasMore
            ? `${items.length} dòng đầu của trang Gold — snapshot còn thêm dữ liệu`
            : `${items.length} dòng trong trang Gold`}
        </span>
      </div>
      <div className={styles.tableScroll}>
        <table className={styles.dataTable}>
          <thead>
            <tr>
              <th>Vật tư</th>
              <th>Trạng thái</th>
              <th>Số dư</th>
              <th>Ngày cung ứng</th>
              <th>Đề xuất nhập</th>
              <th>Hạn gần nhất</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={`${item.warehouseCode}-${item.materialCode}`}>
                <th>{item.materialCode}<small>{item.materialName}</small></th>
                <td><span className={styles.statusPill}>{item.stockStatus}</span></td>
                <td>{formatQuantity(item.stockQuantity, item.baseUnit)}</td>
                <td>{item.daysOfSupply === null ? "—" : `${item.daysOfSupply.toFixed(1)} ngày`}</td>
                <td>{formatQuantity(item.recommendedOrderQuantity, item.baseUnit)}</td>
                <td>{formatDate(item.nearestExpiryDate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function severityClass(value: string): "severityCritical" | "severityWarning" | "severityWatch" {
  const normalized = value.toLowerCase();
  if (normalized.includes("critical") || normalized.includes("nghiêm")) return "severityCritical";
  if (normalized.includes("warning") || normalized.includes("cảnh")) return "severityWarning";
  return "severityWatch";
}
