import type { ReactNode } from "react";
import { forbidden, redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessAssistant } from "@/lib/analytics-area-access";

export default async function AssistantLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/assistant");
  if (!canAccessAssistant(context.identity)) forbidden();
  return (
    <AppShell identity={context.identity} pathname="/assistant">
      {children}
    </AppShell>
  );
}
