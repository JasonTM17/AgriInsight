import { z } from "zod";

const optionalUuid = z.preprocess(
  (value) => value === "" ? undefined : value,
  z.uuid().optional()
);

const optionalSearch = z.preprocess(
  (value) => value === "" ? undefined : value,
  z.string().trim().min(1).max(80).optional()
);

const forecastOffsetSchema = z.coerce.number().int().min(0).max(9_950).default(0);

const filterSchema = z.object({
  farmId: optionalUuid,
  fieldId: optionalUuid,
  cropId: optionalUuid,
  seasonId: optionalUuid,
  datePreset: z.enum(["all", "last-30-days", "season-to-date"]).default("all"),
  status: z.enum(["active", "inactive", "all"]).default("active"),
  search: optionalSearch,
  sort: z.enum(["farm_code", "profit_desc"]).default("farm_code"),
  page: z.coerce.number().int().min(1).max(5).default(1)
}).strict();

export type OverviewFilters = Readonly<z.infer<typeof filterSchema>>;
export type FilterInput = Readonly<Record<string, string | string[] | undefined>>;

export class UnsupportedAnalyticsFilterError extends Error {
  constructor(public readonly filters: readonly string[]) {
    super(`Analytics contract does not support filters: ${filters.join(", ")}`);
    this.name = "UnsupportedAnalyticsFilterError";
  }
}

export function parseOverviewFilters(input: FilterInput): OverviewFilters {
  const normalized = Object.fromEntries(
    Object.entries(input).map(([key, value]) => [
      key,
      Array.isArray(value) ? value[0] : value
    ])
  );
  const overviewInput = Object.fromEntries(
    Object.entries(normalized).filter(([key]) => key !== "forecastOffset")
  );
  return Object.freeze(filterSchema.parse(overviewInput));
}

export function parseForecastOffset(input: FilterInput): number {
  const value = Array.isArray(input.forecastOffset)
    ? input.forecastOffset[0]
    : input.forecastOffset;
  return forecastOffsetSchema.parse(value);
}

export function assertCurrentAnalyticsFilterSupport(
  filters: OverviewFilters
): void {
  const unsupported = [
    filters.datePreset === "season-to-date" && !filters.seasonId
      ? "seasonId"
      : null
  ].filter((name): name is string => name !== null);
  if (unsupported.length > 0) throw new UnsupportedAnalyticsFilterError(unsupported);
}

export function toFilterQuery(
  filters: OverviewFilters,
  overrides: Partial<OverviewFilters> = {}
): URLSearchParams {
  const merged = { ...filters, ...overrides };
  const query = new URLSearchParams();
  for (const key of ["farmId", "fieldId", "cropId", "seasonId", "search"] as const) {
    if (merged[key]) query.set(key, merged[key]);
  }
  if (merged.datePreset !== "all") query.set("datePreset", merged.datePreset);
  if (merged.status !== "active") query.set("status", merged.status);
  if (merged.sort !== "farm_code") query.set("sort", merged.sort);
  if (merged.page > 1) query.set("page", String(merged.page));
  return query;
}
