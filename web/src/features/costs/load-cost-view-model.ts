import "server-only";

import type { WebEnvironment } from "@/server/config/environment";

import {
  getOperatingCostPage,
  getOperatingCostSummary,
  getProcurementCosts,
} from "./cost-generated-client-adapter";
import type {
  OperatingCostPage,
  OperatingCostSummary,
  ProcurementCostsEnvelope
} from "./cost-generated-contract-schemas";
import {
  mapFarmIdToCode,
  CostScopeResolutionError
} from "./map-operational-ids-to-codes";
import type { CostReadContext } from "./map-operational-ids-to-codes";
import {
  resolveCostDateRange,
  type CostFilterState
} from "./cost-filter-schema";
import { loadOperationalFarms } from "../overview/load-operational-farms";
import type { OperationalFarm } from "../overview/resolve-analytics-codes";

export type CostSourceResult<T> =
  | Readonly<{ status: "ready"; data: T }>
  | Readonly<{ status: "failed"; message: string; correlationId: string }>;

export type OperatingCostBundle = Readonly<{
  page: OperatingCostPage;
  summary: OperatingCostSummary;
}>;

export type CostViewModel =
  | Readonly<{ kind: "foreign_farm" }>
  | Readonly<{ kind: "farm_scope_unavailable" }>
  | Readonly<{
      kind: "ready";
      dateRange: Readonly<{ from: string; to: string }>;
      farms: CostSourceResult<readonly OperationalFarm[]>;
      filters: CostFilterState;
      operating: CostSourceResult<OperatingCostBundle> | null;
      procurement: CostSourceResult<ProcurementCostsEnvelope> | null;
      partial: boolean;
      selectedFarm: OperationalFarm | null;
    }>;

export type LoadCostViewModelInput = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  filters: CostFilterState;
}>;

export async function loadCostViewModel({
  env,
  accessToken,
  correlationId,
  filters
}: LoadCostViewModelInput): Promise<CostViewModel> {
  const context: CostReadContext = { env, accessToken, correlationId };
  const farmsResult = await loadFarms(
    env,
    accessToken,
    correlationId
  );
  const selectedFarm = resolveSelectedFarm(filters.filters.farmId, farmsResult);
  if (selectedFarm === "foreign") return { kind: "foreign_farm" };
  if (filters.filters.farmId && selectedFarm === null && farmsResult.status === "failed") {
    return { kind: "farm_scope_unavailable" };
  }

  const dateRange = resolveCostDateRange(filters.filters);
  if (filters.lens === "operating") {
    const operating = await loadOperatingCosts(context, dateRange, filters.filters);
    return {
      kind: "ready",
      dateRange,
      farms: farmsResult,
      filters,
      operating,
      procurement: null,
      partial: farmsResult.status === "failed" || operating.status === "failed",
      selectedFarm
    };
  }

  let farmCode: string | undefined;
  try {
    farmCode = filters.filters.farmId
      ? await mapFarmIdToCode(context, filters.filters.farmId)
      : undefined;
  } catch (error) {
    if (error instanceof CostScopeResolutionError) return { kind: "foreign_farm" };
    return {
      kind: "ready",
      dateRange,
      farms: farmsResult,
      filters,
      operating: null,
      procurement: sourceFailure(
        correlationId,
        "Không thể xác minh phạm vi nông trại trước khi tải mua hàng."
      ),
      partial: true,
      selectedFarm
    };
  }
  const procurement = await loadProcurementCosts(context, dateRange, farmCode);
  return {
    kind: "ready",
    dateRange,
    farms: farmsResult,
    filters,
    operating: null,
    procurement,
    partial: farmsResult.status === "failed" || procurement.status === "failed",
    selectedFarm
  };
}

async function loadFarms(
  env: WebEnvironment,
  accessToken: string,
  correlationId: string
): Promise<CostSourceResult<readonly OperationalFarm[]>> {
  try {
    return {
      status: "ready",
      data: await loadOperationalFarms(env, accessToken, correlationId, { active: true })
    };
  } catch {
    return sourceFailure(correlationId, "Không thể tải danh mục nông trại để xác minh bộ lọc.");
  }
}

function resolveSelectedFarm(
  farmId: string | undefined,
  farms: CostSourceResult<readonly OperationalFarm[]>
): OperationalFarm | null | "foreign" {
  if (!farmId) return null;
  if (farms.status === "failed") return null;
  return farms.data.find((farm) => farm.id === farmId) ?? "foreign";
}

async function loadOperatingCosts(
  context: CostReadContext,
  dateRange: Readonly<{ from: string; to: string }>,
  filters: CostFilterState["filters"]
): Promise<CostSourceResult<OperatingCostBundle>> {
  const results = await Promise.allSettled([
    getOperatingCostPage(context, dateRange, filters),
    getOperatingCostSummary(context, dateRange, filters)
  ]);
  if (results[0].status === "fulfilled" && results[1].status === "fulfilled") {
    return {
      status: "ready",
      data: { page: results[0].value, summary: results[1].value }
    };
  }
  return sourceFailure(
    context.correlationId,
    "Không thể tải sổ chi phí vận hành hoặc phần tổng hợp tương ứng."
  );
}

async function loadProcurementCosts(
  context: CostReadContext,
  dateRange: Readonly<{ from: string; to: string }>,
  farmCode: string | undefined
): Promise<CostSourceResult<ProcurementCostsEnvelope>> {
  try {
    return {
      status: "ready",
      data: await getProcurementCosts(context, {
        farmCode,
        monthFrom: dateRange.from.slice(0, 7),
        monthTo: dateRange.to.slice(0, 7)
      })
    };
  } catch {
    return sourceFailure(
      context.correlationId,
      "Không thể tải snapshot chi phí mua hàng trong phạm vi hiện hành."
    );
  }
}

function sourceFailure<T>(
  correlationId: string,
  message: string
): CostSourceResult<T> {
  return { status: "failed", message, correlationId };
}
