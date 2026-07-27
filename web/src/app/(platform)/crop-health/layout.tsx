import type { ReactNode } from "react";
import { forbidden, redirect } from "next/navigation";

import { AppShell } from "@/components/app-shell/app-shell";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessCropHealth } from "@/lib/analytics-area-access";

export default async function CropHealthLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/crop-health");
  if (!canAccessCropHealth(context.identity)) forbidden();
  return (
    <AppShell identity={context.identity} pathname="/crop-health">
      {children}
    </AppShell>
  );
}
