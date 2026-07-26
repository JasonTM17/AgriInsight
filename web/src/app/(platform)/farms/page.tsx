import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { FarmList } from "@/features/farms/components/farm-list";
import { loadFarmListViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import {
  assertCurrentAnalyticsFilterSupport,
  parseOverviewFilters,
  toFilterQuery,
  type FilterInput,
  type OverviewFilters
} from "@/features/overview/overview-filter-schema";

export const dynamic = "force-dynamic";

export default async function FarmsPage({
  searchParams
}: {
  searchParams: Promise<FilterInput>;
}) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/farms");
  let filters: OverviewFilters | null = null;
  try {
    filters = parseOverviewFilters(await searchParams);
    assertCurrentAnalyticsFilterSupport(filters);
  } catch {}
  if (!filters) {
    return (
      <StatePanel
        actionHref="/farms"
        actionLabel="Xóa bộ lọc"
        correlationId={context.correlationId}
        label="Bộ lọc chưa được hỗ trợ"
        message="Liên kết này chứa bộ lọc chưa thể áp dụng cho danh sách hiện tại. Hãy xóa bộ lọc để trở về phạm vi an toàn."
        state="failed"
      />
    );
  }
  if (filters.farmId) {
    const query = toFilterQuery(filters, { farmId: undefined });
    redirect(`/farms/${filters.farmId}${query.size > 0 ? `?${query}` : ""}`);
  }
  let viewModel: Awaited<ReturnType<typeof loadFarmListViewModel>> | null = null;
  try {
    viewModel = await loadFarmListViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      filters
    });
  } catch {}
  return viewModel
    ? <FarmList viewModel={viewModel} />
    : (
      <StatePanel
        actionHref="/farms"
        correlationId={context.correlationId}
        message="Không thể tải danh sách nông trại ở lần thử này. Hãy tải lại cùng phạm vi hiện tại."
        state="failed"
      />
    );
}
