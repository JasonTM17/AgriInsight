import type { AnalyticsOverviewEnvelope } from "@/features/overview/load-overview-view-model";

import { AnalyticsContextLine } from "./lineage-banner";
import styles from "./overview-farms.module.css";

type MonthlyRow = AnalyticsOverviewEnvelope["payload"]["monthlyTrend"][number];

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});

export function MonthlyFinancialTrend({
  envelope,
  rows
}: {
  envelope: AnalyticsOverviewEnvelope;
  rows: readonly MonthlyRow[];
}) {
  const visibleRows = rows.slice(-12);
  const maxAbsoluteValue = Math.max(
    1,
    ...visibleRows.flatMap((row) => [
      Math.abs(row.revenueVnd),
      Math.abs(row.costVnd),
      Math.abs(row.profitVnd)
    ])
  );
  return (
    <section className={styles.trendPanel}>
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Xu hướng tài chính</p>
          <h3>Doanh thu, chi phí và lợi nhuận</h3>
          <AnalyticsContextLine envelope={envelope} period="Tối đa 12 tháng gần nhất" />
        </div>
        <span>Đơn vị: VND · đường giữa = 0</span>
      </div>
      {visibleRows.length === 0 ? (
        <p className={styles.mutedValue}>Chưa có chuỗi xu hướng trong phạm vi hiện hành.</p>
      ) : (
        <>
          <div className={styles.trendLegend} aria-label="Chú giải biểu đồ">
            <span><i className={styles.revenueKey} />Doanh thu</span>
            <span><i className={styles.costKey} />Chi phí</span>
            <span><i className={styles.profitKey} />Lợi nhuận</span>
            <small>Lợi nhuận âm nằm dưới đường 0; giá trị đầy đủ ở bảng.</small>
          </div>
          <div aria-hidden="true" className={styles.trendChart}>
            {visibleRows.map((row) => (
              <div className={styles.trendGroup} key={row.month}>
                <FinancialTrendBars
                  cost={row.costVnd}
                  maxAbsoluteValue={maxAbsoluteValue}
                  profit={row.profitVnd}
                  revenue={row.revenueVnd}
                />
                <small>{row.month}</small>
              </div>
            ))}
          </div>
          <div className={styles.tableScroll} role="region" aria-label="Bảng xu hướng tài chính có thể cuộn" tabIndex={0}>
            <table>
              <caption className="sr-only">Bảng tương đương của biểu đồ doanh thu, chi phí và lợi nhuận theo tháng</caption>
              <thead><tr><th>Tháng</th><th>Doanh thu</th><th>Chi phí</th><th>Lợi nhuận</th></tr></thead>
              <tbody>
                {visibleRows.map((row) => (
                  <tr key={row.month}>
                    <td>{row.month}</td>
                    <td>{currency.format(row.revenueVnd)}</td>
                    <td>{currency.format(row.costVnd)}</td>
                    <td>{currency.format(row.profitVnd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function FinancialTrendBars({
  cost,
  maxAbsoluteValue,
  profit,
  revenue
}: Readonly<{
  cost: number;
  maxAbsoluteValue: number;
  profit: number;
  revenue: number;
}>) {
  const bars = [
    {
      className: styles.revenueBar,
      metric: "revenue",
      value: revenue
    },
    {
      className: styles.costBar,
      metric: "cost",
      value: cost
    },
    {
      className: styles.profitBar,
      metric: "profit",
      value: profit
    }
  ] as const;
  return (
    <div
      aria-hidden="true"
      className={styles.trendBars}
    >
      <span className={styles.trendBaseline} />
      {bars.map((bar) => (
        <span className={styles.trendBarSlot} key={bar.metric}>
          <progress
            className={[
              styles.trendBar,
              bar.className,
              bar.value < 0 ? styles.negativeBar : styles.positiveBar
            ].join(" ")}
            data-trend-direction={bar.value < 0 ? "negative" : "positive"}
            data-trend-metric={bar.metric}
            max={maxAbsoluteValue}
            value={Math.abs(bar.value)}
          />
        </span>
      ))}
    </div>
  );
}
