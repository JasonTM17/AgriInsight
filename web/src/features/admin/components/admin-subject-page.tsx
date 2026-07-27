import Link from "next/link";

import {
  ADMINISTRATION_COPY,
  ADMIN_STATUS_LABELS
} from "@/content/vi/administration";

import type { loadAdminSubject } from "../admin-read-model";
import styles from "./tenant-administration.module.css";

type Subject = Awaited<ReturnType<typeof loadAdminSubject>>;

export function AdminSubjectPage({
  canManageRoles,
  subject
}: Readonly<{ canManageRoles: boolean; subject: Subject }>) {
  return (
    <div className={styles.page} data-testid="admin-subject-page">
      <Link className={styles.backLink} href="/admin">← Danh sách người dùng</Link>
      <header className={styles.subjectHeading}>
        <div>
          <p className="eyebrow">User profile / tenant subject</p>
          <h1>{subject.displayName}</h1>
          <p>{subject.contactLabel}</p>
        </div>
        <dl className={styles.subjectMeta}>
          <div><dt>Trạng thái</dt><dd>{ADMIN_STATUS_LABELS[subject.status]}</dd></div>
          <div><dt>Phiên bản</dt><dd>{subject.version}</dd></div>
        </dl>
      </header>
      <section className={styles.notice}>
        <strong>Ranh giới dữ liệu định danh</strong>
        <span>{ADMINISTRATION_COPY.identityNotice}</span>
      </section>
      <div className={styles.twoColumn}>
        <AdminCollection
          empty="Chưa cấp vai trò."
          items={subject.roles.map((role) => ({
            detail: role.status === "active" ? "Đang hiệu lực" : "Đã thu hồi",
            key: role.code,
            label: role.label
          }))}
          title="Vai trò"
        />
        <AdminCollection
          empty="Chưa liên kết nhà cung cấp đăng nhập."
          items={subject.providerLinks.map((provider) => ({
            detail: provider.status === "active" ? "Đang liên kết" : "Đã ngắt liên kết",
            key: provider.identityKey,
            label: provider.providerLabel
          }))}
          title="Liên kết đăng nhập"
        />
      </div>
      <AdminCollection
        empty="Chưa có phạm vi nông trại hoặc kho."
        items={subject.assignments.map((assignment) => ({
          detail: `${assignment.scopeType === "farm" ? "Nông trại" : "Kho"} · ${
            assignment.status === "active" ? "Đang hiệu lực" : "Đã thu hồi"
          }`,
          key: assignment.assignmentKey,
          label: assignment.scopeLabel
        }))}
        title="Phạm vi vận hành"
      />
      <p className={styles.permissionNote}>
        {canManageRoles
          ? "Phiên hiện tại có quyền quản lý vai trò."
          : "Phiên hiện tại chỉ có quyền quản lý vòng đời người dùng."}
      </p>
    </div>
  );
}

function AdminCollection({
  empty,
  items,
  title
}: Readonly<{
  empty: string;
  items: readonly Readonly<{ detail: string; key: string; label: string }>[];
  title: string;
}>) {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeading}><h2>{title}</h2></div>
      {items.length === 0 ? <p className={styles.empty}>{empty}</p> : (
        <ul className={styles.collection}>
          {items.map((item) => (
            <li key={item.key}><strong>{item.label}</strong><span>{item.detail}</span></li>
          ))}
        </ul>
      )}
    </section>
  );
}
