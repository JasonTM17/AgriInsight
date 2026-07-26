import { z } from "zod";

export const COST_LENSES = ["operating", "procurement"] as const;
export const OPERATING_COST_CATEGORIES = [
  "LABOR",
  "MATERIAL",
  "MACHINERY",
  "TRANSPORT",
  "UTILITY",
  "OTHER"
] as const;

export type CostLens = (typeof COST_LENSES)[number];
export type OperatingCostCategory =
  (typeof OPERATING_COST_CATEGORIES)[number];

type RawValue = string | readonly string[] | undefined;

export type CostFilterState = Readonly<{
  lens: CostLens;
  filters: Readonly<{
    from?: string;
    to?: string;
    farmId?: string;
    seasonId?: string;
    activityId?: string;
    category?: OperatingCostCategory;
  }>;
}>;

const allowedKeys = new Set([
  "lens",
  "from",
  "to",
  "farmId",
  "seasonId",
  "activityId",
  "category"
]);
const lensSchema = z.enum(COST_LENSES);
const javaUuidSchema = z.string().regex(
  /^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/
);
const isoDateSchema = z.iso.date();
const categorySchema = z.enum(OPERATING_COST_CATEGORIES);

export function parseCostFilterState(
  input: Readonly<Record<string, RawValue>>
): CostFilterState | null {
  if (
    Object.keys(input).some((key) => !allowedKeys.has(key))
    || Object.values(input).some(Array.isArray)
  ) {
    return null;
  }
  const lensResult = lensSchema.safeParse(scalar(input.lens));
  if (!lensResult.success) return null;

  const from = scalar(input.from);
  const to = scalar(input.to);
  const farmId = scalar(input.farmId);
  const seasonId = scalar(input.seasonId);
  const activityId = scalar(input.activityId);
  const rawCategory = scalar(input.category);
  if (from && !isoDateSchema.safeParse(from).success) return null;
  if (to && !isoDateSchema.safeParse(to).success) return null;
  if (from && to && from > to) return null;
  if (farmId && !javaUuidSchema.safeParse(farmId).success) return null;
  if (seasonId && !javaUuidSchema.safeParse(seasonId).success) return null;
  if (activityId && !javaUuidSchema.safeParse(activityId).success) return null;
  if (rawCategory && !categorySchema.safeParse(rawCategory).success) {
    return null;
  }
  if (
    lensResult.data === "procurement"
    && (seasonId || activityId || rawCategory)
  ) {
    return null;
  }

  return {
    lens: lensResult.data,
    filters: compactFilters({
      from,
      to,
      farmId,
      seasonId,
      activityId,
      category: rawCategory as OperatingCostCategory | undefined
    })
  };
}

export function costAnalysisHref(state: CostFilterState): string {
  const query = new URLSearchParams({ lens: state.lens });
  const { from, to, farmId, seasonId, activityId, category } = state.filters;
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  if (farmId) query.set("farmId", farmId);
  if (seasonId) query.set("seasonId", seasonId);
  if (activityId) query.set("activityId", activityId);
  if (category) query.set("category", category);
  return `/costs?${query.toString()}`;
}

function scalar(value: RawValue): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

function compactFilters(
  filters: CostFilterState["filters"]
): CostFilterState["filters"] {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined)
  );
}
