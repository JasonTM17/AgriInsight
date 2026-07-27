import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";
import { ADMIN_AUDIT_OUTCOME_LABELS } from "@/content/vi/administration";

import { ADMIN_PAGE_SIZE } from "../admin-contract-schemas";
import type { loadAdminAudit } from "../admin-read-model";
import styles from "./tenant-administration.module.css";

type Audit = Awaited<ReturnType<typeof loadAdminAudit>>;
type AuditFilters = Readonly<{
  action?: string;
  offset: number;
  outcome?: "CONFLICT" | "DENIED" | "SUCCEEDED";
  targetType?: string;
}>;

export function AdminAuditPage({
  audit,
  filters
}: Readonly<{ audit: Audit; filters: AuditFilters }>) {
  return (
    <div className={styles.page} data-testid="admin-audit-page">
      <AuditFilter filters={filters} />
      <section className={styles.panel}>
        <div className={styles.panelHeading}>
          <div><p className="eyebrow">Immutable event trail</p><h2>Sự kiện quản trị</h2></div>
          <span>Múi giờ hiển thị: UTC</span>
        </div>
        {audit.entries.length === 0 ? (
          <StatePanel actionHref="/admin/audit" actionLabel="Xóa bộ lọc" state="empty" />
        ) : (
          <div className={styles.tableScroll}>
            <table className={styles.dataTable}>
              <thead><tr>
                <th scope="col">Thời điểm</th><th scope="col">Hành động</th>
                <th scope="col">Chủ thể</th><th scope="col">Đích</th>
                <th scope="col">Kết quả</th><th scope="col">Theo dõi</th>
              </tr></thead>
              <tbody>{audit.entries.map((entry) => (
                <tr key={entry.eventKey}>
                  <td><time dateTime={entry.at}>{formatUtc(entry.at)}</time></td>
                  <th scope="row">{entry.actionLabel}</th>
                  <td>{entry.actorLabel}</td><td>{entry.targetLabel}</td>
                  <td>
                    <span className={entry.outcome === "SUCCEEDED" ? styles.activePill : styles.inactivePill}>
                      {ADMIN_AUDIT_OUTCOME_LABELS[entry.outcome]}
                    </span>
                    {entry.reasonLabel ? <small>{entry.reasonLabel}</small> : null}
                  </td>
                  <td><small translate="no">{entry.correlationId ?? "Không có mã"}</small></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
        <div className={styles.pagination}>
          <span>Vị trí {audit.offset + 1}–{audit.offset + audit.entries.length}</span>
          <div>
            {audit.offset > 0 ? <Link href={auditHref(filters, audit.offset - ADMIN_PAGE_SIZE)}>Trang trước</Link> : null}
            {audit.hasMore ? <Link href={auditHref(filters, audit.offset + ADMIN_PAGE_SIZE)}>Trang sau</Link> : null}
          </div>
        </div>
      </section>
    </div>
  );
}

function AuditFilter({ filters }: Readonly<{ filters: AuditFilters }>) {
  return (
    <form action="/admin/audit" className={styles.filterForm} method="get">
      <label>Hành động<input defaultValue={filters.action} maxLength={100} name="action" /></label>
      <label>Loại đích<input defaultValue={filters.targetType} maxLength={100} name="targetType" /></label>
      <label>Kết quả<select defaultValue={filters.outcome ?? ""} name="outcome">
        <option value="">Tất cả</option><option value="SUCCEEDED">Thành công</option>
        <option value="DENIED">Bị từ chối</option><option value="CONFLICT">Xung đột</option>
      </select></label>
      <button type="submit">Áp dụng</button><Link href="/admin/audit">Đặt lại</Link>
    </form>
  );
}

function auditHref(filters: AuditFilters, offset: number): string {
  const params = new URLSearchParams({ offset: String(Math.max(0, offset)) });
  if (filters.action) params.set("action", filters.action);
  if (filters.outcome) params.set("outcome", filters.outcome);
  if (filters.targetType) params.set("targetType", filters.targetType);
  return `/admin/audit?${params.toString()}`;
}

function formatUtc(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "medium",
    timeZone: "UTC"
  }).format(new Date(value));
}
