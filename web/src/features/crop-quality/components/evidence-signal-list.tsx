import type { CropHealthEnvelope } from "../analytics-evidence-contract";
import styles from "./crop-quality.module.css";

type EvidenceSignal = CropHealthEnvelope["payload"]["evidenceSignals"][number];

export function EvidenceSignalList({
  signals
}: Readonly<{ signals: readonly EvidenceSignal[] }>) {
  if (signals.length === 0) {
    return <p className={styles.muted}>Contract không trả về tín hiệu mô tả.</p>;
  }
  return (
    <dl className={styles.signalList}>
      {signals.map((signal) => (
        <div key={signal.name}>
          <dt translate="no">{signal.name}</dt>
          <dd>
            {formatJsonValue(signal.value)}
            {signal.unit ? <small translate="no"> {signal.unit}</small> : null}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function formatJsonValue(value: unknown): string {
  if (typeof value === "string") return value;
  if (typeof value === "number") {
    return new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 })
      .format(value);
  }
  if (typeof value === "boolean") return value ? "true" : "false";
  if (value === null) return "null";
  return JSON.stringify(value);
}
