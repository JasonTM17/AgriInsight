import Link from "next/link";

import { RealtimeAlertEntry } from "@/features/realtime-alerts/components/realtime-alert-entry";
import { Icon } from "@/components/ui/icon";
import type { AuthorizationContext } from "@/server/auth/authorization-context";

export function AppHeader({
  identity,
  pageLabel
}: {
  identity: AuthorizationContext;
  pageLabel: string;
}) {
  const canReadRealtimeAlerts = identity.permissions.has("REALTIME_ALERT_READ");
  const canAcknowledgeRealtimeAlerts = identity.permissions.has(
    "REALTIME_ALERT_ACKNOWLEDGE"
  );

  return (
    <header className="app-header">
      <div className="app-header__title">
        <p className="app-header__eyebrow">Hệ thống vận hành</p>
        <h1>{pageLabel}</h1>
      </div>
      <div className="app-header__actions">
        <label className="search-field">
          <span className="sr-only">Tìm trong phạm vi hiện hành</span>
          <Icon name="search" size={18} />
          <input aria-label="Tìm trong phạm vi hiện hành" placeholder="Tìm trong phạm vi" type="search" />
        </label>
        {canReadRealtimeAlerts ? (
          <RealtimeAlertEntry
            canAcknowledge={canAcknowledgeRealtimeAlerts}
          />
        ) : null}
        <Link
          className="profile-chip"
          href="/protected?module=administration"
          prefetch={false}
        >
          <span aria-hidden="true" className="profile-chip__avatar">
            {(identity.displayName[0] ?? "A").toUpperCase()}
          </span>
          <span>
            <strong>{identity.displayName}</strong>
            <small>{identity.tenantCode}</small>
          </span>
        </Link>
      </div>
    </header>
  );
}
