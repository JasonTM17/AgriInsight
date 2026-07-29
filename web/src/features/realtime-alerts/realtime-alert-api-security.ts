import "server-only";

import { randomUUID } from "node:crypto";

import type { NextRequest } from "next/server";

import {
  authorizeWorkMutation,
  readBoundedJson,
  WorkApiError,
  type WorkMutationContext
} from "@/features/work/work-api-security";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

export type RealtimeAlertReadContext = Readonly<{
  accessToken: string;
  correlationId: string;
  env: ReturnType<typeof getAuthRuntime>["env"];
}>;

export type RealtimeAlertMutationContext = WorkMutationContext;

export class RealtimeAlertApiError extends WorkApiError {
  constructor(code: string, status: number, message: string) {
    super(code, status, message);
    this.name = "RealtimeAlertApiError";
  }
}

export async function authorizeRealtimeAlertRead(
  request: NextRequest
): Promise<RealtimeAlertReadContext> {
  const runtime = getAuthRuntime();
  const url = assertTrustedRequest(request, runtime.env);
  rejectUnexpectedQuery(url.searchParams);
  const sessionToken = request.cookies.get(SESSION_COOKIE_NAME)?.value;
  const session = await runtime.auth.requireSession(sessionToken);
  const correlationId = randomUUID();
  const identity = await getAuthorizationContext(
    runtime.env,
    session.accessToken,
    correlationId
  );
  requirePermission(identity.permissions, "REALTIME_ALERT_READ");
  return {
    accessToken: session.accessToken,
    correlationId,
    env: runtime.env
  };
}

export async function authorizeRealtimeAlertMutation(
  request: NextRequest
): Promise<RealtimeAlertMutationContext> {
  const context = await authorizeWorkMutation(request);
  rejectUnexpectedQuery(request.nextUrl.searchParams);
  const identity = await getAuthorizationContext(
    context.env,
    context.accessToken,
    context.correlationId
  );
  requirePermission(identity.permissions, "REALTIME_ALERT_ACKNOWLEDGE");
  return context;
}

export const readBoundedRealtimeAlertJson = readBoundedJson;

function rejectUnexpectedQuery(searchParams: URLSearchParams): void {
  if (searchParams.size > 0) {
    throw new RealtimeAlertApiError(
      "unexpected_query",
      400,
      "Yêu cầu cảnh báo không chấp nhận tham số truy vấn."
    );
  }
}

function requirePermission(
  permissions: ReadonlySet<string>,
  permission: "REALTIME_ALERT_ACKNOWLEDGE" | "REALTIME_ALERT_READ"
): void {
  if (!permissions.has(permission)) {
    throw new RealtimeAlertApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền truy cập cảnh báo vận hành."
    );
  }
}
