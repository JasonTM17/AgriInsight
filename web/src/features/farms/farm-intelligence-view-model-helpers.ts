import type { OverviewFilters } from "@/features/overview/overview-filter-schema";
import type { OperationalFarm } from "@/features/overview/resolve-analytics-codes";

import type { FarmAnalyticsItem } from "./load-farm-intelligence-view-model";

export function hasAnalyticScopeFilter(filters: OverviewFilters): boolean {
  return Boolean(
    filters.fieldId
    || filters.cropId
    || filters.seasonId
    || filters.datePreset !== "all"
  );
}

export function restrictOperationalFarms(
  farms: readonly OperationalFarm[],
  farmCode?: string,
  analyticFarmCodes?: ReadonlySet<string>
): readonly OperationalFarm[] {
  return farms.filter(
    (farm) =>
      (!farmCode || farm.code === farmCode)
      && (!analyticFarmCodes || analyticFarmCodes.has(farm.code))
  );
}

export function paginateFarms(
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

export function createPagination(
  page: number,
  pageSize: number,
  totalItems: number
) {
  return {
    page,
    pageSize,
    totalItems,
    totalPages: Math.max(1, Math.ceil(totalItems / pageSize))
  } as const;
}
