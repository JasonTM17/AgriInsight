import type { InventoryAnalyticsEnvelope } from "../inventory-analytics-contract-schema";
import {
  formatDataStatus,
  formatDate,
  formatHours,
  formatOptionalDays
} from "../inventory-format";
import styles from "./inventory-control.module.css";
import {
  type ForecastEvidence,
  forecastCoverageClass,
  forecastStatusContent,
  formatBacktest,
  formatForecastRange,
  formatHistory,
  formatOptionalCount,
  formatOptionalQuantity
} from "./inventory-forecast-evidence-format";

export function InventoryForecastEvidencePanel({
  freshness,
  health,
  items
}: Readonly<{
  freshness: InventoryAnalyticsEnvelope["freshness"];
  health: InventoryAnalyticsEnvelope["payload"]["forecastHealth"];
  items: InventoryAnalyticsEnvelope["payload"]["items"];
}>) {
  return (
    <section className={styles.panel} aria-labelledby="forecast-title">
      <div className={styles.panelHeading}>
        <div>
          <p className="eyebrow">Bằng chứng dự báo</p>
          <h3 id="forecast-title">Bằng chứng dự báo nhu cầu</h3>
        </div>
        <span className={styles.forecastFreshness}>
          Độ mới: {formatDataStatus(freshness.dataStatus)} ·{" "}
          {formatHours(freshness.artifactAgeHours)} tuổi
        </span>
      </div>
      <ForecastHealth health={health} />
      {items.length === 0 ? (
        <p className={styles.emptyState} role="status">
          Snapshot không có dòng trạng thái để hiển thị bằng chứng dự báo.
        </p>
      ) : (
        <div
          aria-label="Bảng bằng chứng dự báo có thể cuộn"
          className={styles.tableScroll}
          role="region"
          tabIndex={0}
        >
          <table className={`${styles.dataTable} ${styles.forecastTable}`}>
            <thead>
              <tr>
                <th scope="col">Vật tư</th>
                <th scope="col">Trạng thái dự báo</th>
                <th scope="col">Dự báo điểm</th>
                <th scope="col">Dải dự báo</th>
                <th scope="col">Ngày cung ứng theo dự báo</th>
                <th scope="col">Đề xuất theo dự báo</th>
                <th scope="col">Bằng chứng</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={`${item.warehouseCode}-${item.materialCode}`}>
                  <th scope="row">
                    {item.materialCode}
                    <small>{item.materialName}</small>
                  </th>
                  <td>
                    <ForecastStatus forecast={item.forecast} />
                  </td>
                  <td>
                    {formatOptionalQuantity(
                      item.forecast.forecastQuantity,
                      item.baseUnit
                    )}
                  </td>
                  <td>{formatForecastRange(item.forecast, item.baseUnit)}</td>
                  <td>
                    {formatOptionalDays(item.forecast.forecastDaysOfSupply)}
                  </td>
                  <td>
                    {formatOptionalQuantity(
                      item.forecast.forecastSuggestedOrderQuantity,
                      item.baseUnit
                    )}
                  </td>
                  <td>
                    <ForecastDisclosure forecast={item.forecast} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function ForecastHealth({
  health
}: Readonly<{
  health: InventoryAnalyticsEnvelope["payload"]["forecastHealth"];
}>) {
  return (
    <dl className={styles.forecastHealth} aria-label="Sức khỏe mô hình dự báo">
      <div>
        <dt>Sẵn sàng</dt>
        <dd>{health.ready}</dd>
      </div>
      <div>
        <dt>Không có nhu cầu</dt>
        <dd>{health.noDemand}</dd>
      </div>
      <div>
        <dt>Thiếu lịch sử</dt>
        <dd>{health.insufficientHistory}</dd>
      </div>
      <div>
        <dt>Không có dự báo</dt>
        <dd>{health.unavailable}</dd>
      </div>
      <div>
        <dt>Tổng phạm vi</dt>
        <dd>{health.total}</dd>
      </div>
    </dl>
  );
}

function ForecastStatus({ forecast }: Readonly<{ forecast: ForecastEvidence }>) {
  const content = forecastStatusContent(forecast.coverageStatus);
  return (
    <div className={styles.forecastStatus}>
      <span
        className={`${styles.statusPill} ${
          styles[forecastCoverageClass(forecast.coverageStatus)]
        }`}
      >
        {content.label}
      </span>
      <small>{content.description}</small>
    </div>
  );
}

function ForecastDisclosure({
  forecast
}: Readonly<{ forecast: ForecastEvidence }>) {
  return (
    <details className={styles.forecastDisclosure}>
      <summary>Bằng chứng</summary>
      <dl>
        <div>
          <dt>Chốt dữ liệu</dt>
          <dd>{formatDate(forecast.asOfDate)}</dd>
        </div>
        <div>
          <dt>Phiên bản mô hình</dt>
          <dd translate="no">{forecast.modelVersion ?? "Chưa có"}</dd>
        </div>
        <div>
          <dt>Lịch sử</dt>
          <dd>{formatHistory(forecast)}</dd>
        </div>
        <div>
          <dt>Ngày có nhu cầu</dt>
          <dd>{formatOptionalCount(forecast.nonzeroDemandDays, "ngày")}</dd>
        </div>
        <div>
          <dt>Kỳ dự báo</dt>
          <dd>{formatOptionalCount(forecast.horizonDays, "ngày")}</dd>
        </div>
        <div>
          <dt>Đánh giá ngược</dt>
          <dd>{formatBacktest(forecast)}</dd>
        </div>
      </dl>
    </details>
  );
}
