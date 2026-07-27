import "server-only";

import type { ZodType } from "zod";

import {
  getAnalyticsPayload
} from "@/server/clients/analytics";
import { executeAllowedOperation } from "@/server/bff/upstream-client";

import {
  operatingCostPageSchema,
  operatingCostSummarySchema,
  procurementCostsEnvelopeSchema,
  type OperatingCostPage,
  type OperatingCostSummary,
  type ProcurementCostsEnvelope
} from "./cost-generated-contract-schemas";
import type { CostReadContext } from "./map-operational-ids-to-codes";

const PAGE_SIZE = 50;

export type CostSourceErrorKind =
  | "unauthenticated"
  | "denied"
  | "not_found"
  | "failure";

const ERROR_MESSAGES: Readonly<Record<CostSourceErrorKind, string>> = {
  unauthenticated: "Phiên làm việc chưa được xác thực.",
  denied: "Dữ liệu chi phí không khả dụng trong phạm vi này.",
  not_found: "Không tìm thấy dữ liệu chi phí trong phạm vi này.",
  failure: "Không thể tải dữ liệu chi phí."
};

export class CostSourceError extends Error {
  constructor(
    readonly kind: CostSourceErrorKind,
    readonly status: 401 | 403 | 404 | 502
  ) {
    super(ERROR_MESSAGES[kind]);
    this.name = "CostSourceError";
  }
}

export async function getOperatingCostPage(
  context: CostReadContext,
  dates: Readonly<{ from: string; to: string }>,
  filters: Readonly<{
    activityId?: string;
    category?: string;
    farmId?: string;
    seasonId?: string;
  }>
): Promise<OperatingCostPage> {
  return requestBackend(
    context,
    "operatingCostEntries",
    operatingCostPageSchema,
    {
      activityId: filters.activityId,
      category: filters.category,
      farmId: filters.farmId,
      limit: PAGE_SIZE,
      occurredFrom: `${dates.from}T00:00:00Z`,
      occurredTo: `${dates.to}T23:59:59.999Z`,
      offset: 0,
      seasonId: filters.seasonId
    }
  );
}

export async function getOperatingCostSummary(
  context: CostReadContext,
  dates: Readonly<{ from: string; to: string }>,
  filters: Readonly<{ category?: string; farmId?: string; seasonId?: string }>
): Promise<OperatingCostSummary> {
  return requestBackend(
    context,
    "operatingCostSummaries",
    operatingCostSummarySchema,
    {
      category: filters.category,
      farmId: filters.farmId,
      groupBy: "MONTH",
      occurredFrom: `${dates.from}T00:00:00Z`,
      occurredTo: `${dates.to}T23:59:59.999Z`,
      seasonId: filters.seasonId
    }
  );
}

export async function getProcurementCosts(
  context: CostReadContext,
  query: Readonly<{
    farmCode?: string;
    monthFrom?: string;
    monthTo?: string;
  }>
): Promise<ProcurementCostsEnvelope> {
  try {
    const response = await getAnalyticsPayload(
      context.env,
      "analyticsProcurementCosts",
      context.accessToken,
      context.correlationId,
      {
        farm_code: query.farmCode,
        limit: PAGE_SIZE,
        month_from: query.monthFrom,
        month_to: query.monthTo,
        offset: 0
      }
    );
    return procurementCostsEnvelopeSchema.parse(response);
  } catch (error) {
    if (error instanceof CostSourceError) throw error;
    throw new CostSourceError("failure", 502);
  }
}

async function requestBackend<Output>(
  context: CostReadContext,
  operation: "operatingCostEntries" | "operatingCostSummaries",
  schema: ZodType<Output>,
  query: Readonly<Record<string, number | string | undefined>>
): Promise<Output> {
  try {
    const response = await executeAllowedOperation(
      context.env,
      operation,
      context.accessToken,
      context.correlationId,
      query
    );
    if (!response.ok) throw errorForStatus(response.status);
    return schema.parse(await response.json());
  } catch (error) {
    if (error instanceof CostSourceError) throw error;
    throw new CostSourceError("failure", 502);
  }
}

function errorForStatus(status: number): CostSourceError {
  if (status === 401) return new CostSourceError("unauthenticated", 401);
  if (status === 403) return new CostSourceError("denied", 403);
  if (status === 404) return new CostSourceError("not_found", 404);
  return new CostSourceError("failure", 502);
}
