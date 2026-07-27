"use client";

import type { FormEvent } from "react";

import type { loadAdminSubject } from "../admin-read-model";
import { useIdempotentAdminMutation } from "../use-idempotent-admin-mutation";
import { AdminMutationFeedbackView } from "./admin-mutation-feedback";
import styles from "./admin-mutations.module.css";

type Subject = Awaited<ReturnType<typeof loadAdminSubject>>;

export function AdminScopeControls({
  canManageFarms,
  canManageWarehouses,
  subject
}: Readonly<{
  canManageFarms: boolean;
  canManageWarehouses: boolean;
  subject: Subject;
}>) {
  const mutation = useIdempotentAdminMutation("Đã cập nhật phạm vi vận hành.");
  const active = subject.assignments.filter((item) => item.status === "active");
  const farms = subject.availableFarms.filter((item) =>
    !active.some((assignment) => assignment.scopeType === "farm" && assignment.scopeKey === item.key)
  );
  const warehouses = subject.availableWarehouses.filter((item) =>
    !active.some((assignment) => assignment.scopeType === "warehouse" && assignment.scopeKey === item.key)
  );

  async function grant(event: FormEvent<HTMLFormElement>, scope: "farm" | "warehouse") {
    event.preventDefault();
    const key = String(new FormData(event.currentTarget).get("scopeKey"));
    const prior = subject.assignments.find((assignment) =>
      assignment.scopeType === scope && assignment.scopeKey === key
    );
    await mutation.submit(scope === "farm" ? {
      farmKey: key,
      kind: "grantFarm",
      userKey: subject.userKey
    } : {
      kind: "grantWarehouse",
      userKey: subject.userKey,
      warehouseKey: key
    }, `"${prior?.version ?? 0}"`);
  }

  async function revoke(assignment: Subject["assignments"][number]) {
    await mutation.submit({
      assignmentKey: assignment.assignmentKey,
      kind: assignment.scopeType === "farm" ? "revokeFarm" : "revokeWarehouse"
    }, `"${assignment.version}"`);
  }

  return (
    <section className={styles.section}>
      <header className={styles.sectionHeading}>
        <p className="eyebrow">Operational scope</p><h2>Nông trại & kho</h2>
      </header>
      {canManageFarms && farms.length > 0 ? (
        <ScopeGrantForm items={farms} label="Nông trại cần cấp" onSubmit={(event) => void grant(event, "farm")} pending={mutation.pending} />
      ) : null}
      {canManageWarehouses && warehouses.length > 0 ? (
        <ScopeGrantForm items={warehouses} label="Kho cần cấp" onSubmit={(event) => void grant(event, "warehouse")} pending={mutation.pending} />
      ) : null}
      <ul className={styles.rowList}>
        {active.filter((assignment) =>
          assignment.scopeType === "farm" ? canManageFarms : canManageWarehouses
        ).map((assignment) => (
          <li key={assignment.assignmentKey}>
            <span><strong>{assignment.scopeLabel}</strong><small>{assignment.scopeType === "farm" ? "Nông trại" : "Kho"} · phiên bản {assignment.version}</small></span>
            <button className={styles.dangerButton} disabled={mutation.pending} onClick={() => void revoke(assignment)} type="button">Thu hồi</button>
          </li>
        ))}
      </ul>
      <AdminMutationFeedbackView feedback={mutation.feedback} />
    </section>
  );
}

function ScopeGrantForm({
  items,
  label,
  onSubmit,
  pending
}: Readonly<{
  items: readonly Readonly<{ key: string; label: string }>[];
  label: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  pending: boolean;
}>) {
  return (
    <form className={styles.form} onSubmit={onSubmit}>
      <label>{label}<select name="scopeKey">
        {items.map((item) => <option key={item.key} value={item.key}>{item.label}</option>)}
      </select></label>
      <div className={styles.actions}><button disabled={pending} type="submit">Cấp phạm vi</button></div>
    </form>
  );
}
