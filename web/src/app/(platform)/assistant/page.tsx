import { forbidden, redirect } from "next/navigation";

import { AssistantWorkspace } from "@/features/assistant/components/assistant-workspace";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessAssistant } from "@/lib/analytics-area-access";

export const dynamic = "force-dynamic";

export default async function AssistantPage() {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/assistant");
  if (!canAccessAssistant(context.identity)) forbidden();
  return <AssistantWorkspace />;
}
