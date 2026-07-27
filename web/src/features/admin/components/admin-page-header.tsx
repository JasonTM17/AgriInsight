import Link from "next/link";

import { ADMINISTRATION_COPY } from "@/content/vi/administration";

import styles from "./tenant-administration.module.css";

export function AdminPageHeader({
  active
}: Readonly<{ active: "audit" | "directory" }>) {
  return (
    <>
      <header className={styles.pageHeading}>
        <div>
          <p className="eyebrow">Identity governance / tenant scope</p>
          <h1>{active === "audit"
            ? ADMINISTRATION_COPY.auditTitle
            : ADMINISTRATION_COPY.directoryTitle}</h1>
          <p>{active === "audit"
            ? ADMINISTRATION_COPY.auditDescription
            : ADMINISTRATION_COPY.directoryDescription}</p>
        </div>
        <div className={styles.trustNote}>
          <strong>Quản trị có kiểm chứng</strong>
          <span>{ADMINISTRATION_COPY.scopeNotice}</span>
        </div>
      </header>
      <nav aria-label="Khu vực quản trị" className={styles.tabs}>
        <Link aria-current={active === "directory" ? "page" : undefined} href="/admin">
          Người dùng & phân quyền
        </Link>
        <Link aria-current={active === "audit" ? "page" : undefined} href="/admin/audit">
          Nhật ký kiểm toán
        </Link>
      </nav>
    </>
  );
}
