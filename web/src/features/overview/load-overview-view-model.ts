import "server-only";

import type { AnalyticsResponse } from "@/server/clients/analytics";
import { getAnalyticsPayload } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import {
  resolveOperationalAnalyticsMasters,
  toAnalyticsFilterQuery
} from "./load-operational-analytics-masters";
import { loadOperationalFarms } from "./load-operational-farms";
import type { OverviewFilters } from "./overview-filter-schema";

export type AnalyticsOverviewEnvelope = AnalyticsResponse<"analyticsOverview">;

export type SourceResult<T> =
  | Readonly<{ status: "ready"; data: T }>
  | Readonly<{ status: "failed"; message: string; correlationId: string }>;

export type OverviewViewModel = Readonly<{
  filters: OverviewFilters;
  farms: SourceResult<Awaited<ReturnType<typeof loadOperationalFarms>>>;
  analytics: SourceResult<AnalyticsOverviewEnvelope>;
  partial: boolean;
}>;

export async function loadOverviewViewModel({
  env,
  accessToken,
  correlationId,
  filters
}: {
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  filters: OverviewFilters;
}): Promise<OverviewViewModel> {
  const active = filters.status === "all" ? undefined : filters.status === "active";
  const resolved = await resolveOperationalAnalyticsMasters(
    { env, accessToken, correlationId },
    filters
  );
  const [farms, analytics] = await Promise.allSettled([
    loadOperationalFarms(env, accessToken, correlationId, {
      active,
      search: filters.search
    }),
    getAnalyticsPayload(
      env,
      "analyticsOverview",
      accessToken,
      correlationId,
      toAnalyticsFilterQuery(filters, resolved)
    )
  ]);
  const farmResult: OverviewViewModel["farms"] = farms.status === "fulfilled"
    ? { status: "ready", data: farms.value }
    : sourceFailure(correlationId, "Không thể tải danh mục nông trại hiện hành.");
  const analyticsResult: OverviewViewModel["analytics"] = analytics.status === "fulfilled"
    ? { status: "ready", data: analytics.value }
    : sourceFailure(correlationId, "Không thể tải tổng hợp Gold đã kiểm chứng.");
  return {
    filters,
    farms: farmResult,
    analytics: analyticsResult,
    partial: farmResult.status === "failed" || analyticsResult.status === "failed"
  };
}

function sourceFailure(
  correlationId: string,
  message: string
): Readonly<{ status: "failed"; message: string; correlationId: string }> {
  return { status: "failed", message, correlationId };
}
