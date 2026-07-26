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

import {
  getVisibleWarehouses,
  type InventoryReadContext
} from "./inventory-generated-client-adapter";
import { InventoryApiError } from "./inventory-route-responses";

export type InventoryMutationContext = WorkMutationContext;

export async function authorizeInventoryMutation(
  request: NextRequest
): Promise<InventoryMutationContext> {
  const context = await authorizeWorkMutation(request);
  const identity = await getAuthorizationContext(
    context.env,
    context.accessToken,
    context.correlationId
  );
  if (!identity.permissions.has("INVENTORY_MANAGE")) {
    throw new InventoryApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền ghi giao dịch kho."
    );
  }
  return context;
}

export async function authorizeInventoryRead(
  request: NextRequest
): Promise<InventoryReadContext> {
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
  if (!identity.permissions.has("INVENTORY_READ")) {
    throw new InventoryApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền đọc giao dịch kho."
    );
  }
  return {
    accessToken: session.accessToken,
    correlationId,
    env: runtime.env
  };
}

export async function assertVisibleWarehouse(
  context: InventoryReadContext,
  warehouseId: string
): Promise<void> {
  const warehouses = await getVisibleWarehouses(context);
  if (!warehouses.some((warehouse) => warehouse.id === warehouseId)) {
    throw new InventoryApiError(
      "scope_denied",
      403,
      "Kho không còn trong phạm vi được phân công."
    );
  }
}

export const readBoundedInventoryJson = readBoundedJson;
