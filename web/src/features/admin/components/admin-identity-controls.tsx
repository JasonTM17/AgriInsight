"use client";

import type { FormEvent } from "react";

import type { loadAdminSubject } from "../admin-read-model";
import { useIdempotentAdminMutation } from "../use-idempotent-admin-mutation";
import { AdminMutationFeedbackView } from "./admin-mutation-feedback";
import styles from "./admin-mutations.module.css";

type Subject = Awaited<ReturnType<typeof loadAdminSubject>>;

export function AdminIdentityControls({
  subject
}: Readonly<{ subject: Subject }>) {
  const mutation = useIdempotentAdminMutation("Đã cập nhật liên kết đăng nhập.");

  async function link(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await mutation.submit({
      issuer: data.get("issuer"),
      kind: "linkIdentity",
      subject: data.get("subject"),
      userKey: subject.userKey
    });
  }

  async function unlink(identityKey: string) {
    await mutation.submit({
      identityKey,
      kind: "unlinkIdentity",
      userKey: subject.userKey
    });
  }

  return (
    <section className={styles.section}>
      <header className={styles.sectionHeading}>
        <p className="eyebrow">Federated identity</p><h2>Liên kết đăng nhập OIDC</h2>
      </header>
      <form className={styles.form} onSubmit={link}>
        <p>Subject chỉ được gửi đến BFF trong yêu cầu này, không hiển thị lại hoặc ghi vào phản hồi.</p>
        <label>OIDC issuer<input autoComplete="off" maxLength={2048} name="issuer" required type="url" /></label>
        <label>OIDC subject<input autoComplete="off" maxLength={512} name="subject" required type="password" /></label>
        <div className={styles.actions}><button disabled={mutation.pending} type="submit">Liên kết</button></div>
      </form>
      <ul className={styles.rowList}>
        {subject.providerLinks.filter((provider) => provider.status === "active").map((provider) => (
          <li key={provider.identityKey}>
            <span><strong>{provider.providerLabel}</strong><small>Liên kết đang hoạt động</small></span>
            <button className={styles.dangerButton} disabled={mutation.pending} onClick={() => void unlink(provider.identityKey)} type="button">Ngắt liên kết</button>
          </li>
        ))}
      </ul>
      <AdminMutationFeedbackView feedback={mutation.feedback} />
    </section>
  );
}
