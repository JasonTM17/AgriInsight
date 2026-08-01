import { forbidden, notFound, redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { FarmDetail } from "@/features/farms/components/farm-detail";
import { loadFarmDetailViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";
import {
  assertCurrentAnalyticsFilterSupport,
  parseForecastOffset,
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
  if (!context.identity.permissions.has("FARM_READ")) forbidden();
  let viewModel: Awaited<ReturnType<typeof loadFarmDetailViewModel>> | null = null;
  let backHref = "/farms";
  let forecastPageHref = (offset: number) => `/farms/${farmId}?forecastOffset=${offset}`;
  try {
    const input = await searchParams;
    const filters = parseOverviewFilters(input);
    const forecastOffset = parseForecastOffset(input);
    assertCurrentAnalyticsFilterSupport(filters);
    const query = toFilterQuery(filters, { farmId: undefined });
    backHref = `/farms${query.size > 0 ? `?${query}` : ""}`;
    forecastPageHref = (offset: number) => {
      const forecastQuery = new URLSearchParams(query);
      if (offset > 0) forecastQuery.set("forecastOffset", String(offset));
      return `/farms/${farmId}${forecastQuery.size > 0 ? `?${forecastQuery}` : ""}`;
    };
    viewModel = await loadFarmDetailViewModel({
      env: context.env,
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      farmId,
      filters,
      forecastOffset
    });
  } catch (error) {
    if (error instanceof ScopeResolutionError) notFound();
  }
  return viewModel
    ? <FarmDetail backHref={backHref} forecastPageHref={forecastPageHref} viewModel={viewModel} />
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
