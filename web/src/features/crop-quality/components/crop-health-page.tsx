import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";
import { ReviewedVisual } from "@/components/media/reviewed-visual";
import { CROP_HEALTH_COPY } from "@/content/vi/crop-health";
import { VISUAL_CATALOG_BY_AREA } from "@/lib/visual-catalog";

import type { CropHealthEnvelope } from "../analytics-evidence-contract";
import {
  cropHealthHref,
  type CropHealthRouteState
} from "../crop-health-route-state";
import { EvidenceContractBanner } from "./evidence-contract-banner";
import { EvidenceSignalList } from "./evidence-signal-list";
import styles from "./crop-quality.module.css";

const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

export function CropHealthPage({
  correlationId,
  envelope,
  state
}: Readonly<{
  correlationId: string;
  envelope: CropHealthEnvelope;
  state: CropHealthRouteState;
}>) {
  const { payload } = envelope;
  const visual = VISUAL_CATALOG_BY_AREA.get("crop-health");
  if (!visual?.evidenceBoundary) {
    throw new Error("Crop Health demo evidence boundary is unavailable");
  }
  return (
    <div className={styles.page} data-testid="crop-health-page">
      <header className={styles.pageHeading}>
        <div>
          <p className="eyebrow">{CROP_HEALTH_COPY.eyebrow}</p>
          <h1>{CROP_HEALTH_COPY.title}</h1>
          <p>{CROP_HEALTH_COPY.introduction}</p>
        </div>
        <div className={styles.trustNote}>
          <strong>Snapshot mô tả</strong>
          <span>{CROP_HEALTH_COPY.methodNote}</span>
        </div>
      </header>

      <EvidenceContractBanner
        assessmentMethod={payload.assessmentMethod}
        asOf={envelope.lineage.asOf}
        correlationId={correlationId}
        dataStatus={envelope.freshness.dataStatus}
        generatedAt={envelope.lineage.generatedAt}
        retryHref={cropHealthHref(state)}
        runId={envelope.lineage.runId}
        severity={payload.severity}
      />

      <section className={styles.heroGrid}>
        <div className={styles.summaryPanel}>
          <div className={styles.sectionHeading}>
            <div><p className="eyebrow">Tổng hợp theo contract</p><h2>Phạm vi cây trồng</h2></div>
            <span>{payload.page.total} khu vực</span>
          </div>
          <dl className={styles.metricGrid}>
            <div><dt>Khu vực được theo dõi</dt><dd>{number.format(payload.summary.monitoredFields)}</dd></div>
            <div><dt>Bản ghi 7 ngày</dt><dd>{number.format(payload.summary.readings7d)}</dd></div>
            <div><dt>Khu vực mức cao</dt><dd>{number.format(payload.summary.highRiskFields)}</dd></div>
            <div><dt>Khu vực cần xem xét</dt><dd>{number.format(payload.summary.watchFields)}</dd></div>
            <div><dt>Thiết bị quá tuổi dữ liệu</dt><dd>{number.format(payload.summary.offlineSensors)}</dd></div>
            <div><dt>Ghi nhận sinh vật hại 90 ngày</dt><dd>{number.format(payload.summary.pestCases90d)}</dd></div>
          </dl>
        </div>
        <figure className={styles.demoVisual}>
          <ReviewedVisual
            alt={visual.alt}
            filename={visual.filename}
            height={visual.height}
            width={visual.width}
          />
          <figcaption>
            <strong>{visual.title}</strong>
            <span>{visual.description}</span>
            <em data-testid="crop-demo-warning">{CROP_HEALTH_COPY.demoWarning}</em>
          </figcaption>
        </figure>
      </section>

      <section className={styles.panel}>
        <div className={styles.sectionHeading}>
          <div><p className="eyebrow">Bằng chứng mô tả</p><h2>Tín hiệu từ snapshot</h2></div>
          <span>{payload.evidenceSignals.length} tín hiệu</span>
        </div>
        <EvidenceSignalList signals={payload.evidenceSignals} />
      </section>

      <CropHealthFilter state={state} />
      <CropFieldTable envelope={envelope} state={state} />
      <CropAlertTable alerts={payload.alerts} />
    </div>
  );
}

function CropHealthFilter({ state }: Readonly<{ state: CropHealthRouteState }>) {
  return (
    <form action="/crop-health" className={styles.filterBar} method="get">
      <label>
        Mã nông trại
        <input
          defaultValue={state.farmCode ?? ""}
          maxLength={64}
          name="farm"
          pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,63}"
          placeholder="FARM-001"
        />
      </label>
      <label>
        Số dòng
        <select defaultValue={state.limit} name="limit">
          <option value="25">25</option>
          <option value="50">50</option>
          <option value="100">100</option>
        </select>
      </label>
      <button type="submit">Áp dụng phạm vi</button>
      <Link href="/crop-health" prefetch={false}>Xóa bộ lọc</Link>
    </form>
  );
}

function CropFieldTable({
  envelope,
  state
}: Readonly<{
  envelope: CropHealthEnvelope;
  state: CropHealthRouteState;
}>) {
  const { fields, page } = envelope.payload;
  if (fields.length === 0) {
    return (
      <StatePanel
        actionHref="/crop-health"
        label="Không có khu vực"
        message={CROP_HEALTH_COPY.empty}
        state="empty"
      />
    );
  }
  return (
    <section className={styles.panel}>
      <div className={styles.sectionHeading}>
        <div><p className="eyebrow">Khu vực và cây trồng</p><h2>Bảng bằng chứng</h2></div>
        <span>{page.offset + 1}–{page.offset + fields.length} / {page.total}</span>
      </div>
      <div aria-label="Bảng sức khỏe cây trồng có thể cuộn" className={styles.tableScroll} role="region" tabIndex={0}>
        <table>
          <caption className="sr-only">Bằng chứng cây trồng theo khu vực</caption>
          <thead><tr><th>Khu vực</th><th>Nông trại</th><th>Cây trồng</th><th>Ẩm đất</th><th>pH</th><th>Tuổi dữ liệu</th><th>Trạng thái quy tắc</th><th>Hành động</th></tr></thead>
          <tbody>
            {fields.map((field) => (
              <tr key={field.fieldCode}>
                <td><Link href={`/crop-health/${encodeURIComponent(field.fieldCode)}`} prefetch={false}>{field.fieldName}<small translate="no">{field.fieldCode}</small></Link></td>
                <td>{field.farmName}<small translate="no">{field.farmCode}</small></td>
                <td>{field.cropName}</td>
                <td>{number.format(field.soilMoisturePct)}%</td>
                <td>{number.format(field.soilPh)}</td>
                <td>{number.format(field.sensorAgeDays)} ngày</td>
                <td><code>{field.riskStatus}</code></td>
                <td>{field.recommendedAction}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav aria-label="Phân trang sức khỏe cây trồng" className={styles.pagination}>
        {page.offset > 0 ? <Link href={cropHealthHref(state, Math.max(0, page.offset - page.limit))} prefetch={false}>Trang trước</Link> : <span />}
        {page.hasMore ? <Link href={cropHealthHref(state, page.offset + page.limit)} prefetch={false}>Trang sau</Link> : null}
      </nav>
    </section>
  );
}

function CropAlertTable({
  alerts
}: Readonly<{ alerts: CropHealthEnvelope["payload"]["alerts"] }>) {
  if (alerts.length === 0) return null;
  return (
    <section className={styles.panel}>
      <div className={styles.sectionHeading}><div><p className="eyebrow">Ngoại lệ quy tắc</p><h2>Hành động được đề xuất</h2></div><span>{alerts.length} dòng</span></div>
      <div className={styles.tableScroll} role="region" aria-label="Bảng ngoại lệ cây trồng có thể cuộn" tabIndex={0}>
        <table><thead><tr><th>Khu vực</th><th>Trạng thái</th><th>Diện tích ảnh hưởng tối đa</th><th>Ghi nhận 90 ngày</th><th>Hành động</th></tr></thead><tbody>
          {alerts.map((alert) => <tr key={`${alert.fieldCode}-${alert.riskStatus}`}><td>{alert.fieldName}</td><td><code>{alert.riskStatus}</code></td><td>{number.format(alert.maxAffectedAreaPct)}%</td><td>{alert.pestCases90d}</td><td>{alert.recommendedAction}</td></tr>)}
        </tbody></table>
      </div>
    </section>
  );
}
