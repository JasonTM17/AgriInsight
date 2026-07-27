import type { ReactNode } from "react";
import { forbidden, redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export default async function WorkLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/work");
  if (!context.identity.permissions.has("ACTIVITY_READ")) forbidden();
  return (
    <AppShell identity={context.identity} pathname="/work">
      {children}
    </AppShell>
  );
}
