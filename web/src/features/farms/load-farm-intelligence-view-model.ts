import "server-only";

import { getAnalyticsPayload, type AnalyticsResponse } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import { loadOperationalFarms } from "@/features/overview/load-operational-farms";
import type { OverviewFilters } from "@/features/overview/overview-filter-schema";
import {
  mergeFarmAnalyticsByCode,
  resolveFarmCode,
  type OperationalFarm
} from "@/features/overview/resolve-analytics-codes";
import type { SourceResult } from "@/features/overview/load-overview-view-model";

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

export type FarmDetailViewModel = Readonly<{
  farm: OperationalFarm;
  analytics: SourceResult<FarmAnalyticsEnvelope>;
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
  const [farmResult, analyticsResult] = await Promise.allSettled([
    loadOperationalFarms(env, accessToken, correlationId, {
      active,
      search: filters.search
    }),
    getAnalyticsPayload(env, "analyticsFarms", accessToken, correlationId, {
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
    const merged = paginateFarms(
      mergeFarmAnalyticsByCode(farmResult.value, []),
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
      farmResult.value,
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

export async function loadFarmDetailViewModel({
  env,
  accessToken,
  correlationId,
  farmId
}: {
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  farmId: string;
}): Promise<FarmDetailViewModel> {
  const farms = await loadOperationalFarms(env, accessToken, correlationId);
  const farm = resolveFarmCode(farms, farmId);
  try {
    const analytics = await getAnalyticsPayload(
      env,
      "analyticsFarms",
      accessToken,
      correlationId,
      {
        farm_code: farm.code,
        limit: 1,
        offset: 0,
        sort: "farm_code"
      }
    );
    return { farm, analytics: { status: "ready", data: analytics } };
  } catch {
    return {
      farm,
      analytics: {
        status: "failed",
        message: "Thông tin nông trại đã xác minh, nhưng dữ liệu Gold đang gián đoạn.",
        correlationId
      }
    };
  }
}

function paginateFarms(
  farms: readonly Readonly<{
    farm: OperationalFarm;
    analytics: FarmAnalyticsItem | null;
  }>[],
  filters: OverviewFilters,
  pageSize: number
) {
  const ordered = filters.sort === "profit_desc"
    ? [...farms].sort((left, right) => {
        const leftProfit = left.analytics?.profitVnd;
        const rightProfit = right.analytics?.profitVnd;
        if (leftProfit === undefined && rightProfit === undefined) {
          return left.farm.code.localeCompare(right.farm.code);
        }
        if (leftProfit === undefined) return 1;
        if (rightProfit === undefined) return -1;
        return rightProfit - leftProfit || left.farm.code.localeCompare(right.farm.code);
      })
    : [...farms].sort((left, right) => left.farm.code.localeCompare(right.farm.code));
  const pagination = createPagination(filters.page, pageSize, ordered.length);
  const offset = (pagination.page - 1) * pageSize;
  return {
    items: ordered.slice(offset, offset + pageSize),
    pagination
  } as const;
}

function createPagination(page: number, pageSize: number, totalItems: number) {
  return {
    page,
    pageSize,
    totalItems,
    totalPages: Math.max(1, Math.ceil(totalItems / pageSize))
  } as const;
}
