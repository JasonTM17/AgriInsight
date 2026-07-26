import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { loadWorkViewModel } from "@/features/work/load-work-view-model";
import { WorkOperationsPage } from "@/features/work/components/work-operations-page";
import {
  parseWorkRouteState
} from "@/features/work/work-route-state";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";

type WorkSearchParams = Record<
  string,
  string | readonly string[] | undefined
>;

export default async function WorkPage({
  searchParams
}: Readonly<{ searchParams: Promise<WorkSearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/work");
  if (!context.identity.permissions.has("ACTIVITY_READ")) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại không có quyền đọc công việc."
        state="denied"
      />
    );
  }
  const routeState = parseWorkRouteState(await searchParams);
  if (!routeState) {
    return (
      <StatePanel
        actionHref="/work"
        actionLabel="Xóa bộ lọc"
        correlationId={context.correlationId}
        label="Liên kết công việc không hợp lệ"
        message="Bộ lọc hoặc mã lựa chọn không đúng định dạng an toàn."
        state="failed"
      />
    );
  }
  let viewModel: Awaited<ReturnType<typeof loadWorkViewModel>>;
  try {
    viewModel = await loadWorkViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      filters: routeState.filters,
      historyOffset: routeState.historyOffset,
      logOffset: routeState.logOffset,
      selectedActivityId: routeState.activityId,
      selectedLogId: routeState.logId
    });
  } catch {
    return (
      <StatePanel
        actionHref="/work"
        correlationId={context.correlationId}
        message="Không thể tải phạm vi công việc ở lần thử này."
        state="failed"
      />
    );
  }
  return (
      <WorkOperationsPage
        canWriteLogs={context.identity.permissions.has("ACTIVITY_LOG_APPEND")}
        correlationId={context.correlationId}
      routeState={routeState}
      viewModel={viewModel}
    />
  );
}
