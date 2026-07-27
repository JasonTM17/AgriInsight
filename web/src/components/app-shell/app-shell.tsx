import type { ReactNode } from "react";

import { AppHeader } from "@/components/app-shell/app-header";
import { NavigationRail } from "@/components/app-shell/navigation-rail";
import type { AuthorizationContext } from "@/server/auth/authorization-context";
import { getActiveNavigationKey, getVisibleNavigation } from "@/lib/permission-navigation";

export function AppShell({
  identity,
  children,
  pathname = "/protected",
  searchParams
}: {
  identity: AuthorizationContext;
  children: ReactNode;
  pathname?: string;
  searchParams?: Readonly<Record<string, string | undefined>>;
}) {
  const items = getVisibleNavigation(identity);
  const activeKey = getActiveNavigationKey(pathname, searchParams);
  const pageLabel = items.find((item) => item.key === activeKey)?.label ?? "Tổng quan";

  return (
    <div className="app-shell">
      <NavigationRail items={items} tenantCode={identity.tenantCode} />
      <div className="app-shell__workspace" data-workspace>
        <AppHeader identity={identity} pageLabel={pageLabel} />
        <div className="scope-strip" aria-label="Phạm vi dữ liệu">
          <span><strong>Phạm vi tenant</strong> {identity.tenantCode}</span>
          <span className="scope-strip__freshness"><span aria-hidden="true" className="status-dot" /> Đồng bộ theo phiên máy chủ</span>
        </div>
        <main className="app-shell__main" id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
