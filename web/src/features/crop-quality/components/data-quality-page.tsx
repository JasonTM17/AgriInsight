import { StatePanel } from "@/components/app-shell/state-panels";
import { ReviewedVisual } from "@/components/media/reviewed-visual";
import { DATA_QUALITY_COPY } from "@/content/vi/data-quality";
import { VISUAL_CATALOG_BY_AREA } from "@/lib/visual-catalog";

import type { DataQualityEnvelope } from "../analytics-evidence-contract";
import { EvidenceContractBanner } from "./evidence-contract-banner";
import { EvidenceSignalList } from "./evidence-signal-list";
import styles from "./crop-quality.module.css";

const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

export function DataQualityPage({
  correlationId,
  envelope
}: Readonly<{
  correlationId: string;
  envelope: DataQualityEnvelope;
}>) {
  const visual = VISUAL_CATALOG_BY_AREA.get("data-quality");
  if (!visual) throw new Error("Data Quality reviewed visual is unavailable");
  const { payload } = envelope;
  const issueRows = payload.checks.after.reduce(
    (total, check) => total + check.failedRows,
    0
  );
  return (
    <div className={styles.page} data-testid="data-quality-page">
      <header className={styles.pageHeading}>
        <div><p className="eyebrow">{DATA_QUALITY_COPY.eyebrow}</p><h1>{DATA_QUALITY_COPY.title}</h1><p>{DATA_QUALITY_COPY.introduction}</p></div>
        <div className={styles.trustNote}><strong>Không đổi taxonomy</strong><span>{DATA_QUALITY_COPY.methodNote}</span></div>
      </header>
      <EvidenceContractBanner
        assessmentMethod={payload.assessmentMethod}
        asOf={envelope.lineage.asOf}
        correlationId={correlationId}
        dataStatus={envelope.freshness.dataStatus}
        generatedAt={envelope.lineage.generatedAt}
        retryHref="/data-quality"
        runId={envelope.lineage.runId}
        severity={payload.severity}
      />
      <section className={styles.heroGrid}>
        <div className={styles.summaryPanel}>
          <div className={styles.sectionHeading}><div><p className="eyebrow">Kết quả kiểm tra</p><h2>Trạng thái sau làm sạch</h2></div><code>{payload.status}</code></div>
          <dl className={styles.metricGrid}>
            <div><dt>Kiểm tra sau làm sạch</dt><dd>{payload.checks.after.length}</dd></div>
            <div><dt>Dòng chưa đạt</dt><dd>{number.format(issueRows)}</dd></div>
            <div><dt>Dòng cách ly</dt><dd>{number.format(payload.remediationActions.rowsQuarantined)}</dd></div>
            <div><dt>Dòng trùng đã bỏ</dt><dd>{number.format(payload.remediationActions.duplicatesRemoved)}</dd></div>
          </dl>
        </div>
        <figure className={styles.contextVisual}>
          <ReviewedVisual alt={visual.alt} filename={visual.filename} height={visual.height} width={visual.width} />
          <figcaption><strong>{visual.title}</strong><span>{visual.description}</span><small>Ảnh minh họa bối cảnh thu thập — không phải bằng chứng kiểm tra.</small></figcaption>
        </figure>
      </section>
      {payload.checks.after.length === 0 ? (
        <StatePanel actionHref="/data-quality" message={DATA_QUALITY_COPY.empty} state="empty" />
      ) : (
        <QualityCheckTable checks={payload.checks.after} />
      )}
      <QualityScoreTable scores={payload.scores} />
      <section className={styles.detailGrid}>
        <div className={styles.panel}>
          <p className="eyebrow">Remediation từ pipeline</p><h2>Hành động đã ghi nhận</h2>
          <dl className={styles.detailList}>
            <div><dt>Mã đã chuẩn hóa</dt><dd>{payload.remediationActions.codesCanonicalized}</dd></div>
            <div><dt>Dòng trùng đã bỏ</dt><dd>{payload.remediationActions.duplicatesRemoved}</dd></div>
            <div><dt>Dòng cách ly</dt><dd>{payload.remediationActions.rowsQuarantined}</dd></div>
            <div><dt>Đơn vị về base</dt><dd>{payload.remediationActions.unitsConvertedToBase}</dd></div>
            <div><dt>Đơn vị về kg</dt><dd>{payload.remediationActions.unitsConvertedToKg}</dd></div>
          </dl>
        </div>
        <div className={styles.panel}><p className="eyebrow">Bằng chứng mô tả</p><h2>Tín hiệu contract</h2><EvidenceSignalList signals={payload.evidenceSignals} /></div>
      </section>
    </div>
  );
}

function QualityCheckTable({
  checks
}: Readonly<{ checks: DataQualityEnvelope["payload"]["checks"]["after"] }>) {
  return (
    <section className={styles.panel}>
      <div className={styles.sectionHeading}><div><p className="eyebrow">Kiểm tra sau làm sạch</p><h2>Dòng cần đối chiếu</h2></div><span>{checks.length} kiểm tra</span></div>
      <div aria-label="Bảng kiểm tra chất lượng có thể cuộn" className={styles.tableScroll} role="region" tabIndex={0}>
        <table><thead><tr><th>Bảng</th><th>Kiểm tra</th><th>Mức dòng</th><th>Dòng chưa đạt</th><th>Tổng dòng</th></tr></thead><tbody>
          {checks.map((check) => <tr key={`${check.table}-${check.check}`}><td><code>{check.table}</code></td><td><code>{check.check}</code></td><td><code>{check.severity}</code></td><td>{number.format(check.failedRows)}</td><td>{number.format(check.totalRows)}</td></tr>)}
        </tbody></table>
      </div>
    </section>
  );
}

function QualityScoreTable({
  scores
}: Readonly<{ scores: DataQualityEnvelope["payload"]["scores"] }>) {
  const rows = [
    ["Đầy đủ", scores.before.completenessPct, scores.after.completenessPct],
    ["Hợp lệ", scores.before.validityPct, scores.after.validityPct],
    ["Duy nhất", scores.before.uniquenessPct, scores.after.uniquenessPct],
    ["Freshness", scores.before.freshnessPct, scores.after.freshnessPct]
  ] as const;
  return (
    <section className={styles.panel}>
      <div className={styles.sectionHeading}><div><p className="eyebrow">Đối chiếu trước / sau</p><h2>Điểm chất lượng</h2></div></div>
      <div className={styles.tableScroll} role="region" aria-label="Bảng điểm chất lượng có thể cuộn" tabIndex={0}>
        <table><thead><tr><th>Chiều đo</th><th>Trước làm sạch</th><th>Sau làm sạch</th></tr></thead><tbody>{rows.map(([label, before, after]) => <tr key={label}><td>{label}</td><td>{number.format(before)}%</td><td>{number.format(after)}%</td></tr>)}</tbody></table>
      </div>
    </section>
  );
}
