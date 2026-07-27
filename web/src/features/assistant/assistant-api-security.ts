import "server-only";

import { randomUUID } from "node:crypto";

import type { NextRequest } from "next/server";

import {
  CSRF_COOKIE_NAME,
  SESSION_COOKIE_NAME
} from "@/server/auth/cookie-policy";
import { assertCsrf } from "@/server/auth/csrf";
import { assertSameOriginMutation } from "@/server/auth/origin-guard";
import { getAuthRuntime } from "@/server/auth/runtime";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { assertTrustedRequest } from "@/server/config/environment";
import { readBoundedJson, WorkApiError } from "@/features/work/work-api-security";
import { canAccessAssistant } from "@/lib/analytics-area-access";

export type AssistantQueryContext = Readonly<{
  accessToken: string;
  correlationId: string;
  env: ReturnType<typeof getAuthRuntime>["env"];
}>;

export class AssistantApiError extends WorkApiError {
  constructor(code: string, status: number, message: string) {
    super(code, status, message);
    this.name = "AssistantApiError";
  }
}

export async function authorizeAssistantQuery(
  request: NextRequest
): Promise<AssistantQueryContext> {
  const runtime = getAuthRuntime();
  assertTrustedRequest(request, runtime.env);
  assertSameOriginMutation(request, runtime.env);
  const sessionToken = request.cookies.get(SESSION_COOKIE_NAME)?.value;
  assertCsrf(
    request,
    request.cookies.get(CSRF_COOKIE_NAME)?.value,
    sessionToken,
    runtime.env.csrfKey
  );
  const session = await runtime.auth.requireSession(sessionToken);
  const correlationId = randomUUID();
  const identity = await getAuthorizationContext(
    runtime.env,
    session.accessToken,
    correlationId
  );
  if (!canAccessAssistant(identity)) {
    throw new AssistantApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có phạm vi để dùng trợ lý dữ liệu."
    );
  }
  return {
    accessToken: session.accessToken,
    correlationId,
    env: runtime.env
  };
}

export const readBoundedAssistantJson = readBoundedJson;
