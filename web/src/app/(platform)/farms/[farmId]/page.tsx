import { notFound, redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { FarmDetail } from "@/features/farms/components/farm-detail";
import { loadFarmDetailViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import {
  assertCurrentAnalyticsFilterSupport,
  parseOverviewFilters,
  toFilterQuery,
  type FilterInput
} from "@/features/overview/overview-filter-schema";
import { ScopeResolutionError } from "@/features/overview/resolve-analytics-codes";

export const dynamic = "force-dynamic";

export default async function FarmDetailPage({
  params,
  searchParams
}: {
  params: Promise<{ farmId: string }>;
  searchParams: Promise<FilterInput>;
}) {
  const { farmId } = await params;
  const context = await loadPlatformPageContext();
  if (!context) redirect(`/login?returnTo=/farms/${farmId}`);
  let viewModel: Awaited<ReturnType<typeof loadFarmDetailViewModel>> | null = null;
  let backHref = "/farms";
  try {
    const filters = parseOverviewFilters(await searchParams);
    assertCurrentAnalyticsFilterSupport(filters);
    const query = toFilterQuery(filters, { farmId: undefined });
    backHref = `/farms${query.size > 0 ? `?${query}` : ""}`;
    viewModel = await loadFarmDetailViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      farmId,
      filters
    });
  } catch (error) {
    if (error instanceof ScopeResolutionError) notFound();
  }
  return viewModel
    ? <FarmDetail backHref={backHref} viewModel={viewModel} />
    : (
      <StatePanel
        actionHref={backHref}
        actionLabel="Quay lại danh sách"
        correlationId={context.correlationId}
        message="Không thể mở hồ sơ này với bộ lọc hiện tại."
        state="failed"
      />
    );
}
