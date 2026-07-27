"use client";

import type { FormEvent } from "react";

import { useIdempotentAdminMutation } from "../use-idempotent-admin-mutation";
import { AdminMutationFeedbackView } from "./admin-mutation-feedback";
import styles from "./admin-mutations.module.css";

export function AdminCreateUserForm() {
  const mutation = useIdempotentAdminMutation("Đã tạo hồ sơ người dùng.");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    await mutation.submit({
      displayName: data.get("displayName"),
      email: data.get("email"),
      issuer: data.get("issuer"),
      kind: "createUser",
      subject: data.get("subject")
    });
  }

  return (
    <details className={styles.disclosure}>
      <summary>Tạo hồ sơ người dùng</summary>
      <form className={styles.form} onSubmit={submit}>
        <p>
          Tạo trực tiếp trong tenant và liên kết một chủ thể OIDC đã được xác minh.
          Hệ thống không gửi thư mời hoặc tạo mật khẩu.
        </p>
        <div className={styles.formGrid}>
          <label>Họ tên<input maxLength={200} name="displayName" required /></label>
          <label>Email liên hệ<input autoComplete="email" maxLength={320} name="email" type="email" /></label>
          <label className={styles.fullWidth}>OIDC issuer<input autoComplete="off" maxLength={2048} name="issuer" required type="url" /></label>
          <label className={styles.fullWidth}>OIDC subject<input autoComplete="off" maxLength={512} name="subject" required type="password" /></label>
        </div>
        <div className={styles.actions}>
          <button disabled={mutation.pending} type="submit">
            {mutation.pending ? "Đang tạo…" : "Tạo người dùng"}
          </button>
        </div>
        <AdminMutationFeedbackView feedback={mutation.feedback} />
      </form>
    </details>
  );
}
