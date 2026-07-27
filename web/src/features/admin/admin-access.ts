import type { AuthorizationContext } from "@/server/auth/authorization-context";

export function canAccessAdministration(
  identity: Pick<AuthorizationContext, "permissions" | "roles">
): boolean {
  return !identity.roles.has("SUPPLIER")
    && identity.permissions.has("IDENTITY_USER_MANAGE");
}

export function canManageAdminRoles(
  identity: Pick<AuthorizationContext, "permissions" | "roles">
): boolean {
  return canAccessAdministration(identity)
    && identity.permissions.has("IDENTITY_ROLE_MANAGE");
}
