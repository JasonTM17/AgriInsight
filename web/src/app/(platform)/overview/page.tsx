import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { OverviewDashboard } from "@/features/overview/components/overview-dashboard";
import { loadOverviewViewModel } from "@/features/overview/load-overview-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import {
  assertCurrentAnalyticsFilterSupport,
  parseOverviewFilters,
  type FilterInput,
  type OverviewFilters
} from "@/features/overview/overview-filter-schema";

export const dynamic = "force-dynamic";

export default async function OverviewPage({
  searchParams
}: {
  searchParams: Promise<FilterInput>;
}) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/overview");
  let filters: OverviewFilters | null = null;
  try {
    filters = parseOverviewFilters(await searchParams);
    assertCurrentAnalyticsFilterSupport(filters);
  } catch {}
  if (!filters) {
    return (
      <StatePanel
        actionHref="/overview"
        actionLabel="Xóa bộ lọc"
        correlationId={context.correlationId}
        label="Bộ lọc chưa được hỗ trợ"
        message="Liên kết này chứa bộ lọc chưa thể áp dụng cho dữ liệu phân tích hiện tại. Hãy xóa bộ lọc để trở về tổng quan an toàn."
        state="failed"
      />
    );
  }
  let viewModel: Awaited<ReturnType<typeof loadOverviewViewModel>> | null = null;
  try {
    viewModel = await loadOverviewViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      filters
    });
  } catch {}
  return viewModel
    ? <OverviewDashboard viewModel={viewModel} />
    : (
      <StatePanel
        actionHref="/overview"
        correlationId={context.correlationId}
        message="Không thể tải tổng quan ở lần thử này. Hãy tải lại cùng phạm vi hiện tại."
        state="failed"
      />
    );
}
