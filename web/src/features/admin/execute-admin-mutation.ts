import "server-only";

import { executeAllowedMutation } from "@/server/bff/upstream-client";
import type { AllowedMutationName } from "@/server/bff/allowed-operation";

import type { AdminMutationContext } from "./admin-api-security";
import type { AdminMutationCommand } from "./admin-mutation-contract";

export function executeAdminMutation(
  context: AdminMutationContext,
  command: AdminMutationCommand,
  ifMatch?: string
): Promise<Response> {
  switch (command.kind) {
    case "createUser":
      return send(context, "adminUserCreate", {
        displayName: command.displayName,
        email: command.email,
        issuer: command.issuer,
        reasonCode: "WEB_ADMIN_USER_CREATE",
        subject: command.subject
      }, {});
    case "deactivateUser":
    case "reactivateUser":
      return send(
        context,
        command.kind === "deactivateUser"
          ? "adminUserDeactivate"
          : "adminUserReactivate",
        {
          reasonCode: command.kind === "deactivateUser"
            ? "WEB_ADMIN_USER_DEACTIVATE"
            : "WEB_ADMIN_USER_REACTIVATE"
        },
        { id: command.userKey },
        ifMatch
      );
    case "grantRole":
      return send(context, "adminRoleGrant", {
        reasonCode: "WEB_ADMIN_ROLE_GRANT",
        roleCode: command.roleCode
      }, { id: command.userKey }, ifMatch);
    case "revokeRole":
      return send(context, "adminRoleRevoke", {
        reasonCode: "WEB_ADMIN_ROLE_REVOKE"
      }, { id: command.userKey, roleCode: command.roleCode }, ifMatch);
    case "linkIdentity":
      return send(context, "adminUserLinkIdentity", {
        issuer: command.issuer,
        reasonCode: "WEB_ADMIN_IDENTITY_LINK",
        subject: command.subject
      }, { id: command.userKey });
    case "unlinkIdentity":
      return send(context, "adminUserUnlinkIdentity", {
        reasonCode: "WEB_ADMIN_IDENTITY_UNLINK"
      }, { id: command.userKey, identityId: command.identityKey });
    case "grantFarm":
      return send(context, "adminFarmAssignmentGrant", {
        farmId: command.farmKey,
        reasonCode: "WEB_ADMIN_FARM_GRANT",
        userProfileId: command.userKey
      }, {}, ifMatch);
    case "revokeFarm":
      return send(context, "adminFarmAssignmentRevoke", {
        reasonCode: "WEB_ADMIN_FARM_REVOKE"
      }, { id: command.assignmentKey }, ifMatch);
    case "grantWarehouse":
      return send(context, "adminWarehouseAssignmentGrant", {
        reasonCode: "WEB_ADMIN_WAREHOUSE_GRANT",
        userProfileId: command.userKey,
        warehouseId: command.warehouseKey
      }, {}, ifMatch);
    case "revokeWarehouse":
      return send(context, "adminWarehouseAssignmentRevoke", {
        reasonCode: "WEB_ADMIN_WAREHOUSE_REVOKE"
      }, { id: command.assignmentKey }, ifMatch);
    case "grantActivity":
      return send(context, "adminActivityAssignmentGrant", {
        employeeId: command.employeeKey,
        reasonCode: "WEB_ADMIN_ACTIVITY_GRANT"
      }, { id: command.activityKey }, ifMatch);
    case "revokeActivity":
      return send(context, "adminActivityAssignmentRevoke", {
        reasonCode: "WEB_ADMIN_ACTIVITY_REVOKE"
      }, {
        assignmentId: command.assignmentKey,
        id: command.activityKey
      }, ifMatch);
  }
}

function send(
  context: AdminMutationContext,
  operation: AllowedMutationName,
  body: unknown,
  pathParameters: Readonly<Record<string, string>>,
  ifMatch?: string
): Promise<Response> {
  return executeAllowedMutation(
    context.env,
    operation,
    context.accessToken,
    context.correlationId,
    context.idempotencyKey,
    body,
    pathParameters,
    ifMatch
  );
}
