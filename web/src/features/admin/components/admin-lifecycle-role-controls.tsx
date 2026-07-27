"use client";

import type { FormEvent } from "react";

import {
  adminRoleCodeSchema,
  type AdminRoleCode
} from "../admin-mutation-contract";
import type { loadAdminSubject } from "../admin-read-model";
import { useIdempotentAdminMutation } from "../use-idempotent-admin-mutation";
import { AdminMutationFeedbackView } from "./admin-mutation-feedback";
import styles from "./admin-mutations.module.css";

type Subject = Awaited<ReturnType<typeof loadAdminSubject>>;
const ROLE_LABELS: Readonly<Record<AdminRoleCode, string>> = {
  DATA_ANALYST: "Chuyên viên dữ liệu",
  EXECUTIVE: "Điều hành",
  FARM_MANAGER: "Quản lý nông trại",
  FIELD_WORKER: "Nhân viên hiện trường",
  INVENTORY_MANAGER: "Quản lý kho",
  SUPPLIER: "Nhà cung cấp",
  TENANT_ADMIN: "Quản trị tenant"
};

export function AdminLifecycleRoleControls({
  canManageRoles,
  canManageUser,
  subject
}: Readonly<{
  canManageRoles: boolean;
  canManageUser: boolean;
  subject: Subject;
}>) {
  const lifecycle = useIdempotentAdminMutation("Đã cập nhật vòng đời người dùng.");
  const roles = useIdempotentAdminMutation("Đã cập nhật vai trò.");
  const activeRoles = subject.roles.filter((role) => role.status === "active");
  const grantableRoles = adminRoleCodeSchema.options.filter((code) =>
    !activeRoles.some((role) => role.code === code)
  );

  async function submitLifecycle() {
    await lifecycle.submit({
      kind: subject.status === "active" ? "deactivateUser" : "reactivateUser",
      userKey: subject.userKey
    }, subject.etag);
  }

  async function grantRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = String(new FormData(event.currentTarget).get("roleCode"));
    const prior = subject.roles.find((role) => role.code === code);
    await roles.submit({
      kind: "grantRole",
      roleCode: code,
      userKey: subject.userKey
    }, `"${prior?.version ?? 0}"`);
  }

  async function revokeRole(roleCode: AdminRoleCode, version: number) {
    await roles.submit({
      kind: "revokeRole",
      roleCode,
      userKey: subject.userKey
    }, `"${version}"`);
  }

  return (
    <section className={styles.section}>
      <header className={styles.sectionHeading}>
        <p className="eyebrow">Access governance</p><h2>Vòng đời & vai trò</h2>
      </header>
      {canManageUser ? (
        <form className={styles.form} onSubmit={(event) => {
          event.preventDefault();
          void submitLifecycle();
        }}>
          <p>Thao tác dùng phiên bản hồ sơ hiện tại để ngăn ghi đè đồng thời.</p>
          {subject.status === "active" ? (
            <label>
              <span><input name="confirmed" required type="checkbox" /> Tôi xác nhận vô hiệu hồ sơ này.</span>
            </label>
          ) : null}
          <div className={styles.actions}>
            <button className={subject.status === "active" ? styles.dangerButton : undefined} disabled={lifecycle.pending} type="submit">
              {subject.status === "active" ? "Vô hiệu người dùng" : "Kích hoạt lại"}
            </button>
          </div>
          <AdminMutationFeedbackView feedback={lifecycle.feedback} />
        </form>
      ) : null}
      {canManageRoles ? (
        <>
          {grantableRoles.length > 0 ? (
            <form className={styles.form} onSubmit={grantRole}>
              <label>Vai trò cần cấp<select name="roleCode">
                {grantableRoles.map((code) => <option key={code} value={code}>{ROLE_LABELS[code]}</option>)}
              </select></label>
              <div className={styles.actions}><button disabled={roles.pending} type="submit">Cấp vai trò</button></div>
            </form>
          ) : null}
          <ul className={styles.rowList}>
            {activeRoles.map((role) => (
              <li key={role.code}>
                <span><strong>{role.label}</strong><small>Phiên bản {role.version}</small></span>
                <button className={styles.dangerButton} disabled={roles.pending} onClick={() => void revokeRole(role.code, role.version)} type="button">Thu hồi</button>
              </li>
            ))}
          </ul>
          <AdminMutationFeedbackView feedback={roles.feedback} />
        </>
      ) : null}
    </section>
  );
}
