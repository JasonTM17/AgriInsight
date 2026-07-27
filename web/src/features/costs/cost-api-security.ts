import "server-only";

import { randomUUID } from "node:crypto";

import type { NextRequest } from "next/server";

import {
  authorizeWorkMutation,
  readBoundedJson,
  type WorkMutationContext
} from "@/features/work/work-api-security";
import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";
import { assertTrustedRequest } from "@/server/config/environment";

import { CostApiError } from "./cost-route-responses";
import type { CostReadContext } from "./map-operational-ids-to-codes";

export type CostMutationContext = WorkMutationContext;

export async function authorizeCostRead(
  request: NextRequest
): Promise<CostReadContext> {
  const runtime = getAuthRuntime();
  assertTrustedRequest(request, runtime.env);
  const sessionToken = request.cookies.get(SESSION_COOKIE_NAME)?.value;
  const session = await runtime.auth.requireSession(sessionToken);
  const correlationId = randomUUID();
  const identity = await getAuthorizationContext(
    runtime.env,
    session.accessToken,
    correlationId
  );
  if (!identity.permissions.has("COST_READ")) {
    throw new CostApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền đọc dữ liệu chi phí."
    );
  }
  return {
    accessToken: session.accessToken,
    correlationId,
    env: runtime.env
  };
}

export async function authorizeCostMutation(
  request: NextRequest
): Promise<CostMutationContext> {
  const context = await authorizeWorkMutation(request);
  const identity = await getAuthorizationContext(
    context.env,
    context.accessToken,
    context.correlationId
  );
  if (!identity.permissions.has("COST_MANAGE")) {
    throw new CostApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền ghi chi phí vận hành."
    );
  }
  return context;
}

export const readBoundedCostJson = readBoundedJson;
