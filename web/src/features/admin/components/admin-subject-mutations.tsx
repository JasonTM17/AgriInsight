"use client";

import type { AdminCapabilities } from "../admin-access";
import type { loadAdminSubject } from "../admin-read-model";
import { AdminActivityControls } from "./admin-activity-controls";
import { AdminIdentityControls } from "./admin-identity-controls";
import { AdminLifecycleRoleControls } from "./admin-lifecycle-role-controls";
import { AdminScopeControls } from "./admin-scope-controls";
import styles from "./admin-mutations.module.css";

type Subject = Awaited<ReturnType<typeof loadAdminSubject>>;

export function AdminSubjectMutations({
  capabilities,
  subject
}: Readonly<{ capabilities: AdminCapabilities; subject: Subject }>) {
  return (
    <div className={styles.stack}>
      {capabilities.userLifecycle || capabilities.roles ? (
        <AdminLifecycleRoleControls
          canManageRoles={capabilities.roles}
          canManageUser={capabilities.userLifecycle}
          subject={subject}
        />
      ) : null}
      {capabilities.identities ? (
        <AdminIdentityControls subject={subject} />
      ) : null}
      {capabilities.farms || capabilities.warehouses ? (
        <AdminScopeControls
          canManageFarms={capabilities.farms}
          canManageWarehouses={capabilities.warehouses}
          subject={subject}
        />
      ) : null}
      {capabilities.activities ? (
        <AdminActivityControls employeeKey={subject.userKey} />
      ) : null}
    </div>
  );
}
