import "server-only";

import { z } from "zod";

import type { OverviewFilters } from "@/features/overview/overview-filter-schema";
import {
  ScopeResolutionError,
  type ResolvedAnalyticsCodes
} from "@/features/overview/resolve-analytics-codes";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { AllowedOperationName } from "@/server/bff/allowed-operation";
import type { WebEnvironment } from "@/server/config/environment";

const masterBaseSchema = z.object({
  active: z.boolean(),
  code: z.string().trim().min(1).max(64),
  displayName: z.string().trim().min(1).max(160),
  id: z.uuid(),
  version: z.number().int().nonnegative()
});
const farmSchema = masterBaseSchema;
const fieldSchema = masterBaseSchema.extend({ farmId: z.uuid() });
const cropSchema = masterBaseSchema;
const seasonSchema = z.object({
  code: z.string().trim().min(1).max(64),
  cropId: z.uuid(),
  displayName: z.string().trim().min(1).max(160),
  farmId: z.uuid(),
  fieldId: z.uuid(),
  id: z.uuid(),
  status: z.enum(["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"]),
  version: z.number().int().nonnegative()
});

type MasterContext = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
}>;

export async function resolveOperationalAnalyticsMasters(
  context: MasterContext,
  filters: OverviewFilters
): Promise<ResolvedAnalyticsCodes> {
  const season = filters.seasonId
    ? await loadMaster(context, "seasonById", filters.seasonId, seasonSchema)
    : null;
  if (season?.status === "CANCELLED") throw inactiveMaster("mùa vụ");

  const farmId = reconcileId("farmId", filters.farmId, season?.farmId);
  const fieldId = reconcileId("fieldId", filters.fieldId, season?.fieldId);
  const cropId = reconcileId("cropId", filters.cropId, season?.cropId);
  const field = fieldId
    ? await loadMaster(context, "fieldById", fieldId, fieldSchema)
    : null;
  if (field && !field.active) throw inactiveMaster("khu vực");
  const resolvedFarmId = reconcileId("farmId", farmId, field?.farmId);

  const [farm, crop] = await Promise.all([
    resolvedFarmId
      ? loadMaster(context, "farmById", resolvedFarmId, farmSchema)
      : Promise.resolve(null),
    cropId
      ? loadMaster(context, "cropById", cropId, cropSchema)
      : Promise.resolve(null)
  ]);
  if (farm && !farm.active) throw inactiveMaster("nông trại");
  if (crop && !crop.active) throw inactiveMaster("cây trồng");

  return {
    cropCode: crop?.code,
    farm: farm ?? undefined,
    farmCode: farm?.code,
    fieldCode: field?.code,
    seasonCode: season?.code
  };
}

export function toAnalyticsFilterQuery(
  filters: OverviewFilters,
  resolved: ResolvedAnalyticsCodes
) {
  return {
    crop_code: resolved.cropCode,
    date_preset: filters.datePreset,
    farm_code: resolved.farmCode,
    field_code: resolved.fieldCode,
    season_code: resolved.seasonCode
  } as const;
}

async function loadMaster<Output>(
  context: MasterContext,
  operation: AllowedOperationName,
  id: string,
  schema: z.ZodType<Output>
): Promise<Output> {
  const response = await executeAllowedOperation(
    context.env,
    operation,
    context.accessToken,
    context.correlationId,
    {},
    { id }
  );
  if (response.status === 403 || response.status === 404) {
    throw new ScopeResolutionError(
      "unknown",
      "Không thể xác minh bộ lọc trong phạm vi được cấp quyền."
    );
  }
  if (!response.ok) {
    throw new Error(`Operational master request failed with status ${response.status}`);
  }
  return schema.parse(await response.json());
}

function reconcileId(
  name: string,
  requested: string | undefined,
  related: string | undefined
): string | undefined {
  if (requested && related && requested !== related) {
    throw new ScopeResolutionError(
      "conflict",
      `${name} không thuộc cùng quan hệ nghiệp vụ đã xác minh.`
    );
  }
  return requested ?? related;
}

function inactiveMaster(label: string): ScopeResolutionError {
  return new ScopeResolutionError(
    "inactive",
    `${label} đã ngừng hoạt động và không thể mở rộng phạm vi phân tích.`
  );
}
