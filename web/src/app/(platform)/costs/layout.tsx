import type { ReactNode } from "react";
import { redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export default async function CostsLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/costs");
  return (
    <AppShell identity={context.identity} pathname="/costs">
      {children}
    </AppShell>
  );
}
