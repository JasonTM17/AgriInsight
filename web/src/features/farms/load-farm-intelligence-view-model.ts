import "server-only";

import { getAnalyticsPayload, type AnalyticsResponse } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import { loadOperationalFarms } from "@/features/overview/load-operational-farms";
import {
  resolveOperationalAnalyticsMasters,
  toAnalyticsFilterQuery
} from "@/features/overview/load-operational-analytics-masters";
import type { OverviewFilters } from "@/features/overview/overview-filter-schema";
import {
  mergeFarmAnalyticsByCode,
  type OperationalFarm
} from "@/features/overview/resolve-analytics-codes";
import type { SourceResult } from "@/features/overview/load-overview-view-model";

import {
  createPagination,
  hasAnalyticScopeFilter,
  paginateFarms,
  restrictOperationalFarms
} from "./farm-intelligence-view-model-helpers";

export type FarmAnalyticsEnvelope = AnalyticsResponse<"analyticsFarms">;
export type FarmAnalyticsItem = FarmAnalyticsEnvelope["payload"]["items"][number];

export type FarmListViewModel = Readonly<{
  filters: OverviewFilters;
  farms: SourceResult<
    readonly Readonly<{ farm: OperationalFarm; analytics: FarmAnalyticsItem | null }>[]
  >;
  analyticsMetadata: FarmAnalyticsEnvelope | null;
  partial: boolean;
  pagination: Readonly<{
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
  }>;
}>;

export async function loadFarmListViewModel({
  env,
  accessToken,
  correlationId,
  filters
}: {
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  filters: OverviewFilters;
}): Promise<FarmListViewModel> {
  const pageSize = 20;
  const active = filters.status === "all" ? undefined : filters.status === "active";
  const resolved = await resolveOperationalAnalyticsMasters(
    { env, accessToken, correlationId },
    filters
  );
  const [farmResult, analyticsResult] = await Promise.allSettled([
    loadOperationalFarms(env, accessToken, correlationId, {
      active,
      search: filters.search
    }),
    getAnalyticsPayload(env, "analyticsFarms", accessToken, correlationId, {
      ...toAnalyticsFilterQuery(filters, resolved),
      limit: 100,
      offset: 0,
      sort: filters.sort
    })
  ]);
  if (farmResult.status === "rejected") {
    return {
      filters,
      farms: {
        status: "failed",
        message: "Không thể xác minh danh mục nông trại trong phạm vi hiện hành.",
        correlationId
      },
      analyticsMetadata: null,
      partial: true,
      pagination: createPagination(filters.page, pageSize, 0)
    };
  }
  if (analyticsResult.status === "rejected") {
    if (hasAnalyticScopeFilter(filters)) {
      return {
        filters,
        farms: {
          status: "failed",
          message: "Không thể xác minh kết quả theo bộ lọc phân tích hiện tại.",
          correlationId
        },
        analyticsMetadata: null,
        partial: true,
        pagination: createPagination(filters.page, pageSize, 0)
      };
    }
    const merged = paginateFarms(
      mergeFarmAnalyticsByCode(
        restrictOperationalFarms(farmResult.value, resolved.farmCode),
        []
      ),
      filters,
      pageSize
    );
    return {
      filters,
      farms: {
        status: "ready",
        data: merged.items
      },
      analyticsMetadata: null,
      partial: true,
      pagination: merged.pagination
    };
  }
  const merged = paginateFarms(
    mergeFarmAnalyticsByCode(
      restrictOperationalFarms(
        farmResult.value,
        resolved.farmCode,
        hasAnalyticScopeFilter(filters)
          ? new Set(
              analyticsResult.value.payload.items.map((item) => item.farmCode)
            )
          : undefined
      ),
      analyticsResult.value.payload.items
    ),
    filters,
    pageSize
  );
  return {
    filters,
    farms: {
      status: "ready",
      data: merged.items
    },
    analyticsMetadata: analyticsResult.value,
    partial: false,
    pagination: merged.pagination
  };
}

export {
  loadFarmDetailViewModel,
  type FarmDetailViewModel
} from "./load-farm-detail-view-model";
