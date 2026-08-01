import "server-only";

import {
  getAnalyticsPayload,
  type AnalyticsResponse
} from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import {
  resolveOperationalAnalyticsMasters,
  toAnalyticsFilterQuery
} from "@/features/overview/load-operational-analytics-masters";
import type { SourceResult } from "@/features/overview/load-overview-view-model";
import type { OverviewFilters } from "@/features/overview/overview-filter-schema";

import type { FarmAnalyticsEnvelope } from "./load-farm-intelligence-view-model";
import type { OperationalFarm } from "@/features/overview/resolve-analytics-codes";
import {
  parseScopedYieldForecast,
  type YieldForecastEnvelope
} from "./yield-forecast-contract-schema";

export type FarmDetailViewModel = Readonly<{
  farm: OperationalFarm;
  analytics: SourceResult<FarmAnalyticsEnvelope>;
  forecast: SourceResult<YieldForecastEnvelope>;
}>;

export async function loadFarmDetailViewModel({
  env,
  accessToken,
  correlationId,
  farmId,
  filters,
  forecastOffset
}: {
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  farmId: string;
  filters: OverviewFilters;
  forecastOffset: number;
}): Promise<FarmDetailViewModel> {
  const resolved = await resolveOperationalAnalyticsMasters(
    { env, accessToken, correlationId },
    { ...filters, farmId }
  );
  const farm = resolved.farm;
  if (!farm) throw new Error("Resolved farm master is required");
  const analyticsFilters = toAnalyticsFilterQuery(filters, resolved);
  const [analyticsResult, forecastResult] = await Promise.allSettled([
    getAnalyticsPayload(env, "analyticsFarms", accessToken, correlationId, {
      ...analyticsFilters,
      farm_code: farm.code,
      limit: 1,
      offset: 0,
      sort: "farm_code"
    }),
    getAnalyticsPayload(env, "analyticsYieldForecast", accessToken, correlationId, {
      crop_code: analyticsFilters.crop_code,
      farm_code: farm.code,
      field_code: analyticsFilters.field_code,
      limit: 50,
      offset: forecastOffset,
      season_code: analyticsFilters.season_code
    })
  ]);
  const analytics: FarmDetailViewModel["analytics"] = analyticsResult.status === "fulfilled"
    ? { status: "ready", data: analyticsResult.value }
    : analyticsFailure(correlationId);
  return {
    farm,
    analytics,
    forecast: resolveForecastResult(forecastResult, farm.code, correlationId)
  };
}

function analyticsFailure(correlationId: string): FarmDetailViewModel["analytics"] {
  return {
    status: "failed",
    message: "Thông tin nông trại đã xác minh, nhưng dữ liệu Gold đang gián đoạn.",
    correlationId
  };
}

function resolveForecastResult(
  result: PromiseSettledResult<AnalyticsResponse<"analyticsYieldForecast">>,
  farmCode: string,
  correlationId: string
): FarmDetailViewModel["forecast"] {
  if (result.status === "rejected") return forecastFailure(correlationId);
  try {
    return { status: "ready", data: parseScopedYieldForecast(result.value, farmCode) };
  } catch {
    return forecastFailure(correlationId);
  }
}

function forecastFailure(correlationId: string): FarmDetailViewModel["forecast"] {
  return {
    status: "failed",
    message: "Thông tin nông trại đã xác minh, nhưng bằng chứng dự báo đang gián đoạn.",
    correlationId
  };
}
