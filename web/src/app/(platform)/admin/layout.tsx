import type { ReactNode } from "react";
import { forbidden, redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { canAccessAdministration } from "@/features/admin/admin-access";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export default async function AdminLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/admin");
  if (!canAccessAdministration(context.identity)) forbidden();
  return (
    <AppShell identity={context.identity} pathname="/admin">
      {children}
    </AppShell>
  );
}
