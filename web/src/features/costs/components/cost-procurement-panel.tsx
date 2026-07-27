import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";

import type { CostSourceResult } from "../load-cost-view-model";
import type { ProcurementCostsEnvelope } from "../cost-generated-contract-schemas";
import styles from "./cost-analysis.module.css";

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});
const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

export function CostProcurementPanel({
  source,
  dateRange,
  farmCode
}: Readonly<{
  source: CostSourceResult<ProcurementCostsEnvelope>;
  dateRange: Readonly<{ from: string; to: string }>;
  farmCode?: string;
}>) {
  if (source.status === "failed") {
    return (
      <StatePanel
        actionHref={`/costs?lens=procurement&from=${dateRange.from}&to=${dateRange.to}`}
        correlationId={source.correlationId}
        label="Snapshot mua hàng tạm gián đoạn"
        message={source.message}
        state="partial"
      />
    );
  }
  const { payload, freshness, lineage, scope } = source.data;
  const maxMonth = Math.max(
    1,
    ...payload.monthly.map((row) => row.procurementSpendVnd)
  );
  const exportBase = new URLSearchParams({
    format: "csv",
    month_from: dateRange.from.slice(0, 7),
    month_to: dateRange.to.slice(0, 7),
    scope: "procurement"
  });
  if (farmCode) exportBase.set("farm", farmCode);
  const exportHref = `/api/costs/export?${exportBase.toString()}`;
  return (
    <div className={styles.panelStack}>
      <section aria-label="KPI chi phí mua hàng" className={styles.kpiGrid}>
        <Kpi label="Tổng chi mua hàng" value={currency.format(payload.summary.procurementSpendVnd)} />
        <Kpi label="Số giao dịch" value={number.format(payload.summary.transactionCount)} />
        <Kpi label="Số lượng cơ sở" value={number.format(payload.summary.procurementQuantityBaseUnit)} />
        <Kpi label="Nhà cung cấp" value={number.format(payload.suppliers.length)} />
      </section>
      <div className={styles.panelToolbar}>
        <div>
          <p className="eyebrow">EVIDENCE EXPORT</p>
          <strong>Tải đúng snapshot đã xác minh</strong>
          <span>CSV nhẹ, phục vụ đối soát và chia sẻ.</span>
        </div>
        <div className={styles.exportActions}>
          <Link className={styles.primaryButton} href={exportHref} prefetch={false}>Tải CSV</Link>
          <Link className={styles.secondaryButton} href={exportHref.replace("format=csv", "format=pdf")} prefetch={false}>Tải PDF</Link>
        </div>
      </div>
      <div className={styles.twoColumn}>
        <section className={styles.panel} aria-labelledby="procurement-trend-title">
          <PanelHeading id="procurement-trend-title" title="Chi mua theo tháng" meta={`${payload.monthly.length} tháng`} />
          {payload.monthly.length === 0 ? (
            <p className={styles.emptyState}>Chưa có giao dịch mua hàng trong kỳ.</p>
          ) : (
            <div className={styles.barList}>
              {payload.monthly.map((row) => (
                <div key={row.month}>
                  <div className={styles.barLabel}>
                    <span>{row.month}</span>
                    <strong>{currency.format(row.procurementSpendVnd)}</strong>
                  </div>
                  <progress
                    aria-label={`Chi mua tháng ${row.month}`}
                    className={styles.barTrack}
                    max={maxMonth}
                    value={row.procurementSpendVnd}
                  />
                  <small>{number.format(row.transactionCount)} giao dịch · {number.format(row.procurementQuantityBaseUnit)} cơ sở</small>
                </div>
              ))}
            </div>
          )}
        </section>
        <section className={styles.panel} aria-labelledby="procurement-lineage-title">
          <PanelHeading id="procurement-lineage-title" title="Freshness & lineage" meta={freshness.dataStatus} />
          <dl className={styles.lineageList}>
            <div><dt>Chốt dữ liệu</dt><dd>{lineage.asOf}</dd></div>
            <div><dt>Run ID</dt><dd>{lineage.runId}</dd></div>
            <div><dt>Fingerprint</dt><dd>{lineage.manifestFingerprint.slice(0, 16)}…</dd></div>
            <div><dt>Tuổi snapshot</dt><dd>{number.format(freshness.artifactAgeHours)} giờ / {freshness.maxAgeHours} giờ</dd></div>
            <div><dt>Tenant-wide</dt><dd>{scope.tenantWide ? "Có" : "Không"}</dd></div>
          </dl>
          <p className={styles.sourceNote}>
            Scope backend: {scope.farmCodes.length} farm code, {scope.warehouseCodes.length} warehouse code. Procurement không được nhập vào operating ledger.
          </p>
        </section>
      </div>
      <section className={styles.panel} aria-labelledby="supplier-drivers-title">
        <PanelHeading id="supplier-drivers-title" title="Nhà cung cấp dẫn dắt chi phí" meta={`${payload.suppliers.length} nhà cung cấp`} />
        <div className={styles.tableScroll} role="region" aria-label="Bảng nhà cung cấp mua hàng có thể cuộn" tabIndex={0}>
          <table className={styles.dataTable}>
            <caption className="sr-only">Nhà cung cấp và giao dịch mua hàng</caption>
            <thead><tr><th>Nhà cung cấp</th><th>Mã</th><th>Chi tiêu</th><th>Giao dịch</th></tr></thead>
            <tbody>
              {payload.suppliers.map((supplier) => (
                <tr key={supplier.supplierCode}>
                  <th scope="row">{supplier.supplierName}</th>
                  <td>{supplier.supplierCode}</td>
                  <td className={styles.numeric}>{currency.format(supplier.procurementSpendVnd)}</td>
                  <td>{number.format(supplier.transactionCount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className={styles.panel} aria-labelledby="procurement-detail-title">
        <PanelHeading id="procurement-detail-title" title="Chi tiết mua hàng" meta={`${payload.items.length} dòng`} />
        {payload.items.length === 0 ? (
          <p className={styles.emptyState}>Chưa có dòng detail trong snapshot.</p>
        ) : (
          <div className={styles.tableScroll} role="region" aria-label="Chi tiết mua hàng có thể cuộn" tabIndex={0}>
            <table className={styles.dataTable}>
              <caption className="sr-only">Chi tiết giao dịch mua hàng</caption>
              <thead><tr><th>Ngày</th><th>Nhà cung cấp</th><th>Vật tư</th><th>Nông trại</th><th>Kho</th><th>Chi tiêu</th><th>Đơn giá</th></tr></thead>
              <tbody>
                {payload.items.map((item) => (
                  <tr key={item.transactionId}>
                    <td>{item.transactionDate}</td>
                    <td>{item.supplierName}<small>{item.supplierCode}</small></td>
                    <td>{item.materialName}<small>{item.materialCode}</small></td>
                    <td>{item.farmName}<small>{item.farmCode}</small></td>
                    <td>{item.warehouseName}<small>{item.warehouseCode}</small></td>
                    <td className={styles.numeric}>{currency.format(item.procurementSpendVnd)}</td>
                    <td className={styles.numeric}>{currency.format(item.procurementUnitCostVnd)}</td>
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
  id,
  title,
  meta
}: Readonly<{ id: string; title: string; meta: string }>) {
  return <div className={styles.panelHeading}><div><p className="eyebrow">PROCUREMENT GOLD</p><h2 id={id}>{title}</h2></div><span>{meta}</span></div>;
}
