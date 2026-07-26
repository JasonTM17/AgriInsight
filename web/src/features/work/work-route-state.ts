import { z } from "zod";

import {
  WORK_ACTIVITY_STATUSES,
  WORK_MAX_OFFSET,
  type WorkActivityStatus
} from "./work-generated-client-adapter";

type RawValue = string | readonly string[] | undefined;

export type WorkRouteFilters = Readonly<{
  search?: string;
  status?: WorkActivityStatus;
}>;

export type WorkRouteState = Readonly<{
  activityId?: string;
  filters: WorkRouteFilters;
  historyOffset: number;
  logId?: string;
  logOffset: number;
}>;

const uuidSchema = z.uuid();
const statusSchema = z.enum(WORK_ACTIVITY_STATUSES);
const offsetSchema = z.string()
  .regex(/^(?:0|[1-9]\d*)$/)
  .transform(Number)
  .pipe(
    z.number()
      .int()
      .min(0)
      .max(WORK_MAX_OFFSET)
      .refine((value) => value % 50 === 0)
  );

export function parseWorkRouteState(
  input: Readonly<Record<string, RawValue>>
): WorkRouteState | null {
  if (Object.values(input).some(Array.isArray)) return null;
  const activityId = scalar(input.activityId);
  const logId = scalar(input.logId);
  const logOffset = parseOffset(scalar(input.logOffset));
  const historyOffset = parseOffset(scalar(input.historyOffset));
  const rawSearch = scalar(input.search)?.trim();
  const rawStatus = scalar(input.status);
  if (logOffset === null || historyOffset === null) return null;
  if (activityId && !uuidSchema.safeParse(activityId).success) return null;
  if (logId && !uuidSchema.safeParse(logId).success) return null;
  if (logId && !activityId) return null;
  if (logOffset > 0 && !activityId) return null;
  if (historyOffset > 0 && !logId) return null;
  if (rawSearch && rawSearch.length > 100) return null;
  const parsedStatus = rawStatus
    ? statusSchema.safeParse(rawStatus)
    : undefined;
  if (parsedStatus && !parsedStatus.success) return null;
  return {
    activityId,
    historyOffset,
    logId,
    logOffset,
    filters: {
      search: rawSearch || undefined,
      status: parsedStatus?.data
    }
  };
}

export function workHref(
  state: WorkRouteState,
  selection: Readonly<{
    activityId?: string;
    historyOffset?: number;
    logId?: string;
    logOffset?: number;
  }> = {}
): string {
  const query = new URLSearchParams();
  const activityId = selection.activityId;
  const logId = activityId ? selection.logId : undefined;
  const logOffset = activityId ? selection.logOffset ?? 0 : 0;
  const historyOffset = logId ? selection.historyOffset ?? 0 : 0;
  if (state.filters.search) query.set("search", state.filters.search);
  if (state.filters.status) query.set("status", state.filters.status);
  if (activityId) query.set("activityId", activityId);
  if (logOffset > 0) query.set("logOffset", String(logOffset));
  if (logId) query.set("logId", logId);
  if (historyOffset > 0) {
    query.set("historyOffset", String(historyOffset));
  }
  const serialized = query.toString();
  return serialized ? `/work?${serialized}` : "/work";
}

function scalar(value: RawValue): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function parseOffset(value: string | undefined): number | null {
  if (value === undefined) return 0;
  const parsed = offsetSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
}
