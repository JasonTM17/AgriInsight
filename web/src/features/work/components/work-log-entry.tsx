import type { WorkActivityLog } from "../work-generated-client-adapter";
import {
  formatWorkInstant,
  formatWorkQuantity,
  shortWorkId
} from "../work-format";

import styles from "./work-operations.module.css";

export function WorkLogEntry({
  log
}: Readonly<{ log: WorkActivityLog }>) {
  const quantity = formatWorkQuantity(log);
  return (
    <article className={styles.logEntry}>
      <header>
        <strong>
          {log.correctionKind
            ? log.correctionKind === "VOID"
              ? "Hủy hiệu lực"
              : "Bản thay thế"
            : "Bản ghi gốc"}
        </strong>
        <time dateTime={log.occurredAt}>
          {formatWorkInstant(log.occurredAt)}
        </time>
      </header>
      <p>{log.notes ?? "Không có ghi chú."}</p>
      {quantity ? <b>{quantity}</b> : null}
      {log.correctionReason ? (
        <small>Lý do: {log.correctionReason}</small>
      ) : null}
      <small translate="no">ID {shortWorkId(log.id)}</small>
    </article>
  );
}

