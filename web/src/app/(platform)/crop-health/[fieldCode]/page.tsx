import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { CropHealthFieldDetail } from "@/features/crop-quality/components/crop-health-field-detail";
import { loadCropHealthFieldViewModel } from "@/features/crop-quality/load-crop-health-view-model";
import { parseFieldCode } from "@/features/crop-quality/crop-health-route-state";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import { canAccessCropHealth } from "@/lib/analytics-area-access";
import { AnalyticsUpstreamError } from "@/server/clients/analytics";

export const dynamic = "force-dynamic";

export default async function CropHealthFieldRoute({
  params
}: Readonly<{ params: Promise<{ fieldCode: string }> }>) {
  const { fieldCode: rawFieldCode } = await params;
  const fieldCode = parseFieldCode(rawFieldCode);
  const context = await loadPlatformPageContext();
  if (!context) redirect(`/login?returnTo=/crop-health/${encodeURIComponent(rawFieldCode)}`);
  if (!fieldCode || !canAccessCropHealth(context.identity)) {
    return (
      <StatePanel
        actionHref="/crop-health"
        correlationId={context.correlationId}
        message={!fieldCode ? "Mã khu vực không đúng định dạng an toàn." : "Phiên hiện tại không có quyền xem khu vực này."}
        state="denied"
      />
    );
  }
  try {
    const envelope = await loadCropHealthFieldViewModel(
      {
        env: context.env,
        accessToken: context.accessToken,
        correlationId: context.correlationId
      },
      fieldCode
    );
    return (
      <CropHealthFieldDetail
        correlationId={context.correlationId}
        envelope={envelope}
        fieldCode={fieldCode}
      />
    );
  } catch (error) {
    const denied = error instanceof AnalyticsUpstreamError && error.status === 403;
    return (
      <StatePanel
        actionHref="/crop-health"
        correlationId={context.correlationId}
        message={denied ? "Snapshot khu vực không thuộc phạm vi được cấp quyền." : "Không thể tải chi tiết khu vực ở lần thử này."}
        state={denied ? "denied" : "failed"}
      />
    );
  }
}
