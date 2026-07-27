import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { DataQualityPage } from "@/features/crop-quality/components/data-quality-page";
import { loadDataQualityViewModel } from "@/features/crop-quality/load-data-quality-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessDataQuality } from "@/lib/analytics-area-access";
import { AnalyticsUpstreamError } from "@/server/clients/analytics";

export const dynamic = "force-dynamic";

export default async function DataQualityRoute() {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/data-quality");
  if (!canAccessDataQuality(context.identity)) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại không có quyền xem chất lượng dữ liệu."
        state="denied"
      />
    );
  }
  let envelope: Awaited<ReturnType<typeof loadDataQualityViewModel>>;
  try {
    envelope = await loadDataQualityViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId
    });
  } catch (error) {
    const denied = error instanceof AnalyticsUpstreamError && error.status === 403;
    return (
      <StatePanel
        correlationId={context.correlationId}
        message={denied ? "Snapshot chất lượng dữ liệu không thuộc phạm vi được cấp quyền." : "Không thể tải chất lượng dữ liệu ở lần thử này."}
        state={denied ? "denied" : "failed"}
      />
    );
  }
  return <DataQualityPage correlationId={context.correlationId} envelope={envelope} />;
}
