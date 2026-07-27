import "server-only";

import type { NextRequest } from "next/server";

import {
  authorizeWorkMutation,
  readBoundedJson,
  type WorkMutationContext
} from "@/features/work/work-api-security";
import { getAuthorizationContext } from "@/server/auth/authorization-context";

import type { AdminMutationCommand } from "./admin-mutation-contract";
import { AdminApiError } from "./admin-route-responses";

export type AdminMutationContext = WorkMutationContext & Readonly<{
  permissions: ReadonlySet<string>;
  roles: ReadonlySet<string>;
}>;

export async function authorizeAdminMutation(
  request: NextRequest
): Promise<AdminMutationContext> {
  const context = await authorizeWorkMutation(request);
  const identity = await getAuthorizationContext(
    context.env,
    context.accessToken,
    context.correlationId
  );
  if (identity.roles.has("SUPPLIER")) {
    throw new AdminApiError(
      "scope_denied",
      403,
      "Nhà cung cấp không được phép thực hiện quản trị tenant."
    );
  }
  return {
    ...context,
    permissions: identity.permissions,
    roles: identity.roles
  };
}

export function assertAdminCommandPermission(
  context: AdminMutationContext,
  command: AdminMutationCommand
): void {
  const permission = permissionForCommand(command.kind);
  if (!context.permissions.has(permission)) {
    throw new AdminApiError(
      "scope_denied",
      403,
      "Phiên hiện tại không có quyền thực hiện thay đổi quản trị này."
    );
  }
}

function permissionForCommand(kind: AdminMutationCommand["kind"]): string {
  if (kind === "grantRole" || kind === "revokeRole") {
    return "IDENTITY_ROLE_MANAGE";
  }
  if (kind === "grantFarm" || kind === "revokeFarm") {
    return "FARM_ASSIGNMENT_MANAGE";
  }
  if (kind === "grantWarehouse" || kind === "revokeWarehouse") {
    return "INVENTORY_ASSIGNMENT_MANAGE";
  }
  if (kind === "grantActivity" || kind === "revokeActivity") {
    return "ACTIVITY_MANAGE";
  }
  return "IDENTITY_USER_MANAGE";
}

export const readBoundedAdminJson = readBoundedJson;
