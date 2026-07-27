import { StatePanel } from "@/components/app-shell/state-panels";

import type {
  DataStatus,
  EvidenceSeverity
} from "../analytics-evidence-contract";
import styles from "./crop-quality.module.css";

type EvidenceContractBannerProps = Readonly<{
  assessmentMethod: "rule-based-heuristic";
  asOf: string;
  correlationId: string;
  dataStatus: DataStatus;
  generatedAt: string;
  retryHref: string;
  runId: string;
  severity: EvidenceSeverity;
}>;

export function EvidenceContractBanner({
  assessmentMethod,
  asOf,
  correlationId,
  dataStatus,
  generatedAt,
  retryHref,
  runId,
  severity
}: EvidenceContractBannerProps) {
  return (
    <>
      <section aria-label="Contract bằng chứng" className={styles.contractBanner}>
        <div>
          <span>Trạng thái dữ liệu</span>
          <strong translate="no">dataStatus={dataStatus}</strong>
        </div>
        <div>
          <span>Phương pháp</span>
          <strong translate="no">assessmentMethod={assessmentMethod}</strong>
        </div>
        <div>
          <span>Mức độ</span>
          <strong translate="no">severity={severity}</strong>
        </div>
        <div>
          <span>Thời điểm chốt</span>
          <strong translate="no">asOf={asOf}</strong>
        </div>
      </section>
      <p className={styles.lineage}>
        <span translate="no">runId={runId}</span>
        <span translate="no">generatedAt={generatedAt}</span>
      </p>
      <EvidenceState
        correlationId={correlationId}
        dataStatus={dataStatus}
        retryHref={retryHref}
      />
    </>
  );
}

function EvidenceState({
  correlationId,
  dataStatus,
  retryHref
}: Readonly<{
  correlationId: string;
  dataStatus: DataStatus;
  retryHref: string;
}>) {
  if (dataStatus === "current") return null;
  if (dataStatus === "stale") {
    return (
      <StatePanel
        actionHref={retryHref}
        correlationId={correlationId}
        message="Snapshot đã quá tuổi freshness của contract; số liệu vẫn giữ nguyên thời điểm chốt."
        state="stale"
      />
    );
  }
  if (dataStatus === "partial") {
    return (
      <StatePanel
        actionHref={retryHref}
        correlationId={correlationId}
        message="Snapshot chỉ có một phần bằng chứng trong phạm vi này; giao diện không bù dữ liệu thiếu."
        state="partial"
      />
    );
  }
  return (
    <StatePanel
      actionHref={retryHref}
      correlationId={correlationId}
      message="Snapshot xác nhận không có bằng chứng trong phạm vi hiện hành."
      state="empty"
    />
  );
}
