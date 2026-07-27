import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";
import { ReviewedVisual } from "@/components/media/reviewed-visual";
import { CROP_HEALTH_COPY } from "@/content/vi/crop-health";
import { VISUAL_CATALOG_BY_AREA } from "@/lib/visual-catalog";

import type { CropHealthEnvelope } from "../analytics-evidence-contract";
import { EvidenceContractBanner } from "./evidence-contract-banner";
import { EvidenceSignalList } from "./evidence-signal-list";
import styles from "./crop-quality.module.css";

const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

export function CropHealthFieldDetail({
  correlationId,
  envelope,
  fieldCode
}: Readonly<{
  correlationId: string;
  envelope: CropHealthEnvelope;
  fieldCode: string;
}>) {
  const field = envelope.payload.fields[0];
  const visual = VISUAL_CATALOG_BY_AREA.get("crop-health");
  if (!visual?.evidenceBoundary) {
    throw new Error("Crop Health demo evidence boundary is unavailable");
  }
  if (!field || field.fieldCode !== fieldCode) {
    return (
      <StatePanel
        actionHref="/crop-health"
        correlationId={correlationId}
        label="Không có bằng chứng khu vực"
        message="Khu vực không thuộc phạm vi đã xác minh hoặc snapshot chưa có dữ liệu."
        state="denied"
      />
    );
  }
  return (
    <article className={styles.page} data-testid="crop-health-detail-page">
      <header className={styles.pageHeading}>
        <div><p className="eyebrow">Chi tiết khu vực / <span translate="no">{field.fieldCode}</span></p><h1>{field.fieldName}</h1><p>{field.farmName} · {field.cropName} · {number.format(field.areaHa)} ha</p></div>
        <Link className={styles.backLink} href="/crop-health" prefetch={false}>Về danh sách khu vực</Link>
      </header>
      <EvidenceContractBanner
        assessmentMethod={envelope.payload.assessmentMethod}
        asOf={envelope.lineage.asOf}
        correlationId={correlationId}
        dataStatus={envelope.freshness.dataStatus}
        generatedAt={envelope.lineage.generatedAt}
        retryHref={`/crop-health/${encodeURIComponent(field.fieldCode)}`}
        runId={envelope.lineage.runId}
        severity={envelope.payload.severity}
      />
      <section className={styles.detailGrid}>
        <div className={styles.panel}>
          <p className="eyebrow">Giá trị tại snapshot</p><h2>Bằng chứng khu vực</h2>
          <dl className={styles.detailList}>
            <div><dt>Nhiệt độ</dt><dd>{number.format(field.temperatureC)} °C</dd></div>
            <div><dt>Ẩm không khí</dt><dd>{number.format(field.airHumidityPct)}%</dd></div>
            <div><dt>Ẩm đất</dt><dd>{number.format(field.soilMoisturePct)}%</dd></div>
            <div><dt>pH đất</dt><dd>{number.format(field.soilPh)}</dd></div>
            <div><dt>Mưa 7 ngày</dt><dd>{number.format(field.rainfall7dMm)} mm</dd></div>
            <div><dt>Bản ghi 7 ngày</dt><dd>{field.readingCount7d}</dd></div>
            <div><dt>Lần ghi nhận cuối</dt><dd translate="no">{field.lastReadingAt}</dd></div>
            <div><dt>Tuổi dữ liệu</dt><dd>{number.format(field.sensorAgeDays)} ngày</dd></div>
            <div><dt>Trạng thái quy tắc</dt><dd><code>{field.riskStatus}</code></dd></div>
            <div><dt>Hành động</dt><dd>{field.recommendedAction}</dd></div>
          </dl>
        </div>
        <figure className={styles.demoVisual}>
          <ReviewedVisual alt={visual.alt} filename={visual.filename} height={visual.height} width={visual.width} />
          <figcaption><strong>{visual.title}</strong><span>{visual.description}</span><em data-testid="crop-demo-warning">{CROP_HEALTH_COPY.demoWarning}</em></figcaption>
        </figure>
      </section>
      <section className={styles.panel}><div className={styles.sectionHeading}><div><p className="eyebrow">Bằng chứng mô tả</p><h2>Tín hiệu contract</h2></div></div><EvidenceSignalList signals={envelope.payload.evidenceSignals} /></section>
    </article>
  );
}
