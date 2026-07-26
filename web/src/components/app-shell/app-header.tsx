import Link from "next/link";

import { Icon } from "@/components/ui/icon";
import type { AuthorizationContext } from "@/server/auth/authorization-context";

export function AppHeader({
  identity,
  pageLabel
}: {
  identity: AuthorizationContext;
  pageLabel: string;
}) {
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
        <button aria-label="Mở thông báo" className="icon-button" type="button">
          <Icon name="bell" size={20} />
        </button>
        <Link className="profile-chip" href="/protected?module=administration">
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
