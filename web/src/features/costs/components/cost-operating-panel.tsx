import { StatePanel } from "@/components/app-shell/state-panels";

import type {
  OperatingCostBundle
} from "../load-cost-view-model";
import type { CostSourceResult } from "../load-cost-view-model";
import styles from "./cost-analysis.module.css";

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});
const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeZone: "UTC"
});

export function CostOperatingPanel({
  source,
  dateRange
}: Readonly<{
  source: CostSourceResult<OperatingCostBundle>;
  dateRange: Readonly<{ from: string; to: string }>;
}>) {
  if (source.status === "failed") {
    return (
      <StatePanel
        actionHref={`/costs?lens=operating&from=${dateRange.from}&to=${dateRange.to}`}
        correlationId={source.correlationId}
        label="Sổ vận hành tạm gián đoạn"
        message={source.message}
        state="partial"
      />
    );
  }
  const { page, summary } = source.data;
  const total = summary.items.reduce(
    (value, item) => value + item.netOperatingCostVnd,
    0
  );
  const postings = summary.items.reduce(
    (value, item) => value + item.postingAmountVnd,
    0
  );
  const reversals = summary.items.reduce(
    (value, item) => value + item.reversalAmountVnd,
    0
  );
  const variance = summary.items.reduce(
    (value, item) => value + (item.budgetVarianceVnd ?? 0),
    0
  );
  const maxMonthly = Math.max(
    1,
    ...summary.items.map((item) => Math.abs(item.netOperatingCostVnd))
  );
  return (
    <div className={styles.panelStack}>
      <section aria-label="KPI chi phí vận hành" className={styles.kpiGrid}>
        <Kpi label="Chi phí ròng" value={currency.format(total)} />
        <Kpi label="Posting" value={currency.format(postings)} />
        <Kpi label="Reversal" value={currency.format(reversals)} />
        <Kpi label="Sai lệch ngân sách" value={currency.format(variance)} />
      </section>
      <div className={styles.twoColumn}>
        <section className={styles.panel} aria-labelledby="operating-trend-title">
          <PanelHeading
            eyebrow="MONTHLY LEDGER"
            id="operating-trend-title"
            title="Nhịp chi phí theo tháng"
            meta={`${summary.items.length} nhóm`}
          />
          {summary.items.length === 0 ? (
            <p className={styles.emptyState}>Chưa có dòng tổng hợp trong cửa sổ này.</p>
          ) : (
            <div className={styles.barList}>
              {summary.items.map((item) => (
                <div key={`${item.groupKey ?? "month"}-${item.groupId ?? "none"}`}>
                  <div className={styles.barLabel}>
                    <span>{item.groupKey ?? "Không gắn nhóm"}</span>
                    <strong>{currency.format(item.netOperatingCostVnd)}</strong>
                  </div>
                  <progress
                    aria-label={`Chi phí ${item.groupKey ?? "không gắn nhóm"}`}
                    className={styles.barTrack}
                    max={maxMonthly}
                    value={Math.abs(item.netOperatingCostVnd)}
                  />
                </div>
              ))}
            </div>
          )}
        </section>
        <section className={styles.panel} aria-labelledby="operating-source-title">
          <PanelHeading
            eyebrow="LINEAGE"
            id="operating-source-title"
            title="Nguồn và phạm vi"
            meta={summary.source}
          />
          <dl className={styles.lineageList}>
            <div><dt>Khoảng UTC</dt><dd>{dateRange.from} → {dateRange.to}</dd></div>
            <div><dt>Nhóm tổng hợp</dt><dd>{summary.groupBy}</dd></div>
            <div><dt>Trang hiện tại</dt><dd>{page.items.length} / tối đa {page.limit}</dd></div>
            <div><dt>Offset</dt><dd>{page.offset}{page.hasMore ? " · còn trang" : " · hết trang"}</dd></div>
          </dl>
          <p className={styles.sourceNote}>
            Backend trả về entryKind, signedAmountVnd và version; giao diện không tự gộp procurement hoặc inventory.
          </p>
        </section>
      </div>
      <section className={styles.panel} aria-labelledby="operating-entries-title">
        <PanelHeading
          eyebrow="OPERATIONAL EVIDENCE"
          id="operating-entries-title"
          title="Dòng sổ chi tiết"
          meta={`${page.items.length} bản ghi`}
        />
        {page.items.length === 0 ? (
          <p className={styles.emptyState}>Chưa có bản ghi chi phí vận hành.</p>
        ) : (
          <div className={styles.tableScroll} role="region" aria-label="Sổ chi phí vận hành có thể cuộn" tabIndex={0}>
            <table className={styles.dataTable}>
              <caption className="sr-only">Chi tiết các bút toán chi phí vận hành</caption>
              <thead>
                <tr><th>Thời điểm UTC</th><th>Nhóm</th><th>Phân bổ</th><th>Loại</th><th>Số tiền</th><th>Tham chiếu</th></tr>
              </thead>
              <tbody>
                {page.items.map((entry) => (
                  <tr key={entry.id}>
                    <td>{dateTime.format(new Date(entry.occurredAt))}</td>
                    <td>{entry.category}</td>
                    <td>{entry.targetType}{entry.targetId ? ` · ${entry.targetId.slice(0, 8)}` : ""}</td>
                    <td><span className={entry.entryKind === "REVERSAL" ? styles.warningPill : styles.statusPill}>{entry.entryKind}</span></td>
                    <td className={styles.numeric}>{currency.format(entry.signedAmountVnd)}</td>
                    <td>{entry.sourceReference ?? entry.description ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function Kpi({ label, value }: Readonly<{ label: string; value: string }>) {
  return <div className={styles.kpiCard}><span>{label}</span><strong>{value}</strong></div>;
}

function PanelHeading({
  eyebrow,
  id,
  title,
  meta
}: Readonly<{ eyebrow: string; id: string; title: string; meta: string }>) {
  return (
    <div className={styles.panelHeading}>
      <div><p className="eyebrow">{eyebrow}</p><h2 id={id}>{title}</h2></div>
      <span>{meta}</span>
    </div>
  );
}
