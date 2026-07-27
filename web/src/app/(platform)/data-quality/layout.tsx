import type { ReactNode } from "react";
import { redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export default async function DataQualityLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/data-quality");
  return (
    <AppShell identity={context.identity} pathname="/data-quality">
      {children}
    </AppShell>
  );
}
