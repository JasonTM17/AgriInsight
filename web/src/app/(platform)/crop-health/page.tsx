import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { CropHealthPage } from "@/features/crop-quality/components/crop-health-page";
import { loadCropHealthViewModel } from "@/features/crop-quality/load-crop-health-view-model";
import { parseCropHealthRouteState } from "@/features/crop-quality/crop-health-route-state";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessCropHealth } from "@/lib/analytics-area-access";
import { AnalyticsUpstreamError } from "@/server/clients/analytics";

export const dynamic = "force-dynamic";

type CropHealthSearchParams = Record<
  string,
  string | readonly string[] | undefined
>;

export default async function CropHealthRoute({
  searchParams
}: Readonly<{ searchParams: Promise<CropHealthSearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/crop-health");
  if (!canAccessCropHealth(context.identity)) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại không có quyền xem sức khỏe cây trồng."
        state="denied"
      />
    );
  }

  const routeState = parseCropHealthRouteState(await searchParams);
  if (!routeState) {
    return (
      <StatePanel
        actionHref="/crop-health"
        actionLabel="Xóa bộ lọc"
        correlationId={context.correlationId}
        label="Bộ lọc cây trồng không hợp lệ"
        message="Mã nông trại hoặc phân trang không đúng định dạng an toàn."
        state="failed"
      />
    );
  }

  let envelope: Awaited<ReturnType<typeof loadCropHealthViewModel>>;
  try {
    envelope = await loadCropHealthViewModel(
      {
        env: context.env,
        accessToken: context.accessToken,
        correlationId: context.correlationId
      },
      routeState
    );
  } catch (error) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        label={error instanceof AnalyticsUpstreamError && error.status === 403 ? "Không có quyền" : undefined}
        message={error instanceof AnalyticsUpstreamError && error.status === 403
          ? "Snapshot cây trồng không thuộc phạm vi được cấp quyền."
          : "Không thể tải bằng chứng cây trồng ở lần thử này. Hãy tải lại cùng phạm vi hiện tại."}
        state={error instanceof AnalyticsUpstreamError && error.status === 403 ? "denied" : "failed"}
      />
    );
  }
  return (
    <CropHealthPage
      correlationId={context.correlationId}
      envelope={envelope}
      state={routeState}
    />
  );
}
