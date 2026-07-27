import type { ReactNode } from "react";
import { forbidden, redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export default async function InventoryLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/inventory");
  if (!context.identity.permissions.has("INVENTORY_READ")) forbidden();
  return (
    <AppShell identity={context.identity} pathname="/inventory">
      {children}
    </AppShell>
  );
}
