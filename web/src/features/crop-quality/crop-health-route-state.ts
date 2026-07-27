import { z } from "zod";

type RawValue = string | readonly string[] | undefined;

export type CropHealthRouteState = Readonly<{
  farmCode?: string;
  limit: number;
  offset: number;
}>;

const allowedKeys = new Set(["farm", "limit", "offset"]);
const codeSchema = z.string().regex(/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/);

export function parseCropHealthRouteState(
  input: Readonly<Record<string, RawValue>>
): CropHealthRouteState | null {
  if (
    Object.keys(input).some((key) => !allowedKeys.has(key))
    || Object.values(input).some(Array.isArray)
  ) {
    return null;
  }
  const farmCode = scalar(input.farm);
  if (farmCode && !codeSchema.safeParse(farmCode).success) return null;
  const limit = boundedInteger(scalar(input.limit), 50, 1, 100);
  const offset = boundedInteger(scalar(input.offset), 0, 0, 10_000);
  if (limit === null || offset === null) return null;
  return { ...(farmCode ? { farmCode } : {}), limit, offset };
}

export function parseFieldCode(value: string): string | null {
  const result = codeSchema.safeParse(value);
  return result.success ? result.data : null;
}

export function cropHealthHref(
  state: CropHealthRouteState,
  offset = state.offset
): string {
  const query = new URLSearchParams();
  if (state.farmCode) query.set("farm", state.farmCode);
  if (state.limit !== 50) query.set("limit", String(state.limit));
  if (offset > 0) query.set("offset", String(offset));
  const serialized = query.toString();
  return `/crop-health${serialized ? `?${serialized}` : ""}`;
}

function scalar(value: RawValue): string | undefined {
  return typeof value === "string" && value.trim() !== ""
    ? value.trim()
    : undefined;
}

function boundedInteger(
  value: string | undefined,
  fallback: number,
  minimum: number,
  maximum: number
): number | null {
  if (value === undefined) return fallback;
  if (!/^\d{1,5}$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= minimum && parsed <= maximum
    ? parsed
    : null;
}
