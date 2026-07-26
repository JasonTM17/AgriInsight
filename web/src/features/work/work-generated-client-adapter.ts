import "server-only";

import type { ZodType } from "zod";

import type { AllowedOperationName } from "@/server/bff/allowed-operation";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

import {
  activityPageSchema,
  activitySchema,
  assignmentPageSchema,
  logPageSchema,
  logSchema,
  type WorkActivity,
  type WorkActivityAssignment,
  type WorkActivityLog,
  type WorkActivityStatus
} from "./work-generated-contract-schemas";

export {
  WORK_ACTIVITY_STATUSES,
  WORK_MAX_OFFSET,
  type WorkActivity,
  type WorkActivityAssignment,
  type WorkActivityLog,
  type WorkActivityStatus
} from "./work-generated-contract-schemas";

export const WORK_LOG_PAGE_SIZE = 50;

export type WorkPage<Item> = Readonly<{
  items: readonly Item[];
  limit: number;
  offset: number;
  hasMore: boolean;
}>;

export type WorkReadErrorKind =
  | "unauthenticated"
  | "denied"
  | "not_found"
  | "failure";

const ERROR_MESSAGES: Readonly<Record<WorkReadErrorKind, string>> = {
  unauthenticated: "The work session is not authenticated.",
  denied: "Work data is not available for this scope.",
  not_found: "The work record was not found in this scope.",
  failure: "Work data could not be loaded."
};

export class WorkReadError extends Error {
  constructor(
    readonly kind: WorkReadErrorKind,
    readonly status: 401 | 403 | 404 | 502
  ) {
    super(ERROR_MESSAGES[kind]);
    this.name = "WorkReadError";
  }
}

export type WorkReadContext = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
}>;

export function parseWorkActivityLog(payload: unknown): WorkActivityLog {
  return logSchema.parse(payload);
}

export async function getWorkActivityPage(
  context: WorkReadContext,
  filters: Readonly<{ status?: WorkActivityStatus; search?: string }> = {}
): Promise<WorkPage<WorkActivity>> {
  return requestWorkPayload(context, "activityCatalog", activityPageSchema, {
    limit: 25,
    offset: 0,
    search: filters.search,
    status: filters.status
  });
}

export async function getWorkActivity(
  context: WorkReadContext,
  activityId: string
): Promise<WorkActivity> {
  return requestWorkPayload(
    context,
    "activityById",
    activitySchema,
    {},
    { id: activityId }
  );
}

export async function getWorkActivityAssignments(
  context: WorkReadContext,
  activityId: string
): Promise<WorkPage<WorkActivityAssignment>> {
  return requestWorkPayload(
    context,
    "activityAssignments",
    assignmentPageSchema,
    { limit: 50, offset: 0 },
    { id: activityId }
  );
}

export async function getWorkActivityLogs(
  context: WorkReadContext,
  activityId: string,
  offset: number
): Promise<WorkPage<WorkActivityLog>> {
  const page = await requestWorkPayload(
    context,
    "activityLogs",
    logPageSchema,
    { limit: WORK_LOG_PAGE_SIZE, offset },
    { id: activityId }
  );
  return assertRequestedPage(page, offset);
}

export async function getWorkActivityLogHistory(
  context: WorkReadContext,
  activityId: string,
  logId: string,
  offset: number
): Promise<WorkPage<WorkActivityLog>> {
  const page = await requestWorkPayload(
    context,
    "activityLogHistory",
    logPageSchema,
    { limit: WORK_LOG_PAGE_SIZE, offset },
    { id: activityId, logId }
  );
  return assertRequestedPage(page, offset);
}

async function requestWorkPayload<Output>(
  context: WorkReadContext,
  operation: AllowedOperationName,
  schema: ZodType<Output>,
  query: Readonly<Record<string, number | string | undefined>> = {},
  pathParameters: Readonly<Record<string, string>> = {}
): Promise<Output> {
  try {
    const response = await executeAllowedOperation(
      context.env,
      operation,
      context.accessToken,
      context.correlationId,
      query,
      pathParameters
    );
    if (!response.ok) throw workErrorForStatus(response.status);
    return schema.parse(await response.json());
  } catch (error) {
    if (error instanceof WorkReadError) throw error;
    throw new WorkReadError("failure", 502);
  }
}

function assertRequestedPage<Item>(
  page: WorkPage<Item>,
  offset: number
): WorkPage<Item> {
  if (page.limit !== WORK_LOG_PAGE_SIZE || page.offset !== offset) {
    throw new WorkReadError("failure", 502);
  }
  return page;
}

function workErrorForStatus(status: number): WorkReadError {
  if (status === 401) return new WorkReadError("unauthenticated", 401);
  if (status === 403) return new WorkReadError("denied", 403);
  if (status === 404) return new WorkReadError("not_found", 404);
  return new WorkReadError("failure", 502);
}
