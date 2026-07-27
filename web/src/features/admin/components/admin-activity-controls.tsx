"use client";

import type { FormEvent } from "react";

import { useIdempotentAdminMutation } from "../use-idempotent-admin-mutation";
import { AdminMutationFeedbackView } from "./admin-mutation-feedback";
import styles from "./admin-mutations.module.css";

export function AdminActivityControls({
  employeeKey
}: Readonly<{ employeeKey: string }>) {
  const mutation = useIdempotentAdminMutation("Đã cập nhật phân công hoạt động.");

  async function grant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const activityKey = data.get("activityKey");
    const version = Number(data.get("version"));
    await mutation.submit({
      activityKey,
      employeeKey,
      kind: "grantActivity"
    }, `"${version}"`);
  }

  async function revoke(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const version = Number(data.get("version"));
    await mutation.submit({
      activityKey: data.get("activityKey"),
      assignmentKey: data.get("assignmentKey"),
      kind: "revokeActivity"
    }, `"${version}"`);
  }

  return (
    <section className={styles.section}>
      <header className={styles.sectionHeading}>
        <p className="eyebrow">Staff assignment</p><h2>Phân công hoạt động</h2>
      </header>
      <form className={styles.form} onSubmit={grant}>
        <p>Cấp theo UUID hoạt động đã xác minh từ khu vực Công việc.</p>
        <label>UUID hoạt động<input name="activityKey" required /></label>
        <label>Phiên bản hiện tại<input defaultValue={0} min={0} name="version" required step={1} type="number" /></label>
        <div className={styles.actions}><button disabled={mutation.pending} type="submit">Cấp phân công</button></div>
      </form>
      <details className={styles.disclosure}>
        <summary>Thu hồi theo mã phân công</summary>
        <form className={styles.form} onSubmit={revoke}>
          <label>UUID hoạt động<input name="activityKey" required /></label>
          <label>UUID phân công<input name="assignmentKey" required /></label>
          <label>Phiên bản<input min={0} name="version" required step={1} type="number" /></label>
          <div className={styles.actions}><button className={styles.dangerButton} disabled={mutation.pending} type="submit">Thu hồi phân công</button></div>
        </form>
      </details>
      <AdminMutationFeedbackView feedback={mutation.feedback} />
    </section>
  );
}
