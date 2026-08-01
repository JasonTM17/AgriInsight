import Link from "next/link";

import { Icon } from "@/components/ui/icon";
import type {
  YieldForecastEnvelope,
  YieldForecastHealth,
  YieldForecastItem
} from "@/features/farms/yield-forecast-contract-schema";
import {
  formatBacktest,
  formatDataStatus,
  formatDate,
  formatForecastPoint,
  formatHistory,
  formatObservedSpan,
  formatOptionalQuantity,
  formatQuantity,
  number
} from "@/features/farms/yield-forecast-formatters";
import styles from "@/features/overview/components/overview-farms.module.css";

export function YieldForecastPanel({
  forecast,
  forecastPageHref
}: Readonly<{
  forecast: YieldForecastEnvelope;
  forecastPageHref: (offset: number) => string;
}>) {
  const displayedItems = forecast.payload.items.slice(0, 50);
  return (
    <section aria-labelledby="yield-forecast-title" className={styles.farmTable}>
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Bằng chứng dự báo</p>
          <h3 id="yield-forecast-title">Dự báo sản lượng theo mùa vụ</h3>
          <p className={styles.sourceNote}>
            Giá trị chỉ được định dạng từ snapshot máy chủ; khoảng quan sát không phải khoảng tin cậy hay cam kết sản lượng.
          </p>
        </div>
      </div>
      <Freshness freshness={forecast.freshness} />
      <ForecastHealth health={forecast.payload.forecastHealth} />
      {displayedItems.length === 0 ? (
        <p className={styles.sourceNote} role="status">
          Snapshot không có mùa vụ đang hoạt động trong phạm vi nông trại này.
        </p>
      ) : (
        <div
          aria-label="Bảng bằng chứng dự báo sản lượng có thể cuộn"
          className={styles.tableScroll}
          role="region"
          tabIndex={0}
        >
          <table>
            <caption className="sr-only">
              Bằng chứng dự báo sản lượng theo mùa vụ của nông trại
            </caption>
            <thead>
              <tr>
                <th scope="col">Mùa vụ</th>
                <th scope="col">Khu vực</th>
                <th scope="col">Cây trồng</th>
                <th scope="col">Trạng thái dự báo</th>
                <th scope="col">Ước tính máy chủ</th>
                <th scope="col">Khoảng quan sát lịch sử</th>
                <th scope="col">Ngữ cảnh mục tiêu</th>
                <th scope="col">Bằng chứng</th>
              </tr>
            </thead>
            <tbody>
              {displayedItems.map((item) => (
                <tr key={item.seasonCode}>
                  <td data-label="Mùa vụ" translate="no">{item.seasonCode}</td>
                  <td data-label="Khu vực" translate="no">{item.fieldCode}</td>
                  <td data-label="Cây trồng" translate="no">{item.cropCode}</td>
                  <td data-label="Trạng thái dự báo"><ForecastStatus item={item} /></td>
                  <td data-label="Ước tính máy chủ">{formatForecastPoint(item)}</td>
                  <td data-label="Khoảng quan sát lịch sử">{formatObservedSpan(item)}</td>
                  <td data-label="Ngữ cảnh mục tiêu">
                    {formatOptionalQuantity(item.targetYieldKg, "kg")}
                  </td>
                  <td data-label="Bằng chứng"><ForecastDisclosure item={item} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {forecast.payload.page.hasMore || forecast.payload.items.length > displayedItems.length ? (
        <p className={styles.sourceNote} role="status">
          Trang này hiển thị tối đa 50 mùa vụ; dùng điều hướng bên dưới để xem các trang còn lại.
        </p>
      ) : null}
      <ForecastPagination forecast={forecast} forecastPageHref={forecastPageHref} />
    </section>
  );
}

function ForecastPagination({
  forecast,
  forecastPageHref
}: Readonly<{
  forecast: YieldForecastEnvelope;
  forecastPageHref: (offset: number) => string;
}>) {
  const { hasMore, limit, offset, total } = forecast.payload.page;
  if (offset === 0 && !hasMore) return null;
  const pageStep = Math.min(limit, 50);
  const first = total === 0 ? 0 : Math.min(offset, total - 1) + 1;
  const last = Math.min(offset + forecast.payload.items.length, total);
  return (
    <nav aria-label="Phân trang dự báo năng suất" className={styles.pagination}>
      {offset > 0 ? (
        <Link href={forecastPageHref(Math.max(0, offset - pageStep))}>Trang trước</Link>
      ) : <span aria-hidden="true" className={styles.paginationSpacer} />}
      <span className={styles.paginationCurrent}>Mùa vụ {first}–{last} trên {total}</span>
      {hasMore ? (
        <Link href={forecastPageHref(offset + pageStep)}>Trang sau</Link>
      ) : <span aria-hidden="true" className={styles.paginationSpacer} />}
    </nav>
  );
}

function Freshness({
  freshness
}: Readonly<{ freshness: YieldForecastEnvelope["freshness"] }>) {
  const stale = freshness.dataStatus !== "current";
  return (
    <p className={styles.sourceNote} role="status">
      <Icon name={stale ? "alert-triangle" : "shield-check"} size={16} />{" "}
      Trạng thái snapshot: {formatDataStatus(freshness.dataStatus)}. Độ mới: {number.format(freshness.artifactAgeHours)} giờ, giới hạn {number.format(freshness.maxAgeHours)} giờ.
    </p>
  );
}

function ForecastHealth({ health }: Readonly<{ health: YieldForecastHealth }>) {
  return (
    <dl aria-label="Sức khỏe bằng chứng dự báo sản lượng">
      <div>
        <dt>Sẵn sàng</dt>
        <dd>{number.format(health.ready)}</dd>
      </div>
      <div>
        <dt>Thiếu lịch sử</dt>
        <dd>{number.format(health.insufficientHistory)}</dd>
      </div>
      <div>
        <dt>Tổng phạm vi</dt>
        <dd>{number.format(health.total)}</dd>
      </div>
    </dl>
  );
}

function ForecastStatus({ item }: Readonly<{ item: YieldForecastItem }>) {
  const ready = item.forecastStatus === "ready";
  return (
    <span>
      <Icon name={ready ? "shield-check" : "alert-triangle"} size={16} />{" "}
      {ready ? "Sẵn sàng" : "Thiếu lịch sử"}
      <small className={styles.sourceNote}>
        {ready
          ? "Máy chủ đã cung cấp điểm ước tính và bằng chứng đánh giá ngược."
          : "Máy chủ chưa có đủ lịch sử; giao diện không tự nội suy."}
      </small>
    </span>
  );
}

function ForecastDisclosure({ item }: Readonly<{ item: YieldForecastItem }>) {
  return (
    <details className={styles.technicalDetails}>
      <summary>Xem bằng chứng</summary>
      <dl>
        <div>
          <dt>Chốt dữ liệu</dt>
          <dd>{formatDate(item.asOfDate)}</dd>
        </div>
        <div>
          <dt>Ngày gốc dự báo</dt>
          <dd>{formatDate(item.forecastOriginDate)}</dd>
        </div>
        <div>
          <dt>Thu hoạch dự kiến</dt>
          <dd>{formatDate(item.expectedHarvestDate)}</dd>
        </div>
        <div>
          <dt>Diện tích mùa vụ</dt>
          <dd>{formatQuantity(item.seasonAreaHa, "ha")}</dd>
        </div>
        <div>
          <dt>Phiên bản mô hình</dt>
          <dd translate="no">{item.modelVersion}</dd>
        </div>
        <div>
          <dt>Lịch sử</dt>
          <dd>{formatHistory(item)}</dd>
        </div>
        <div>
          <dt>Mùa lịch sử</dt>
          <dd>{number.format(item.historySeasons)}</dd>
        </div>
        <div>
          <dt>Đánh giá ngược</dt>
          <dd>{formatBacktest(item)}</dd>
        </div>
      </dl>
    </details>
  );
}
