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

export function getAdminCapabilities(
  identity: Pick<AuthorizationContext, "permissions" | "roles">
) {
  const allowed = canAccessAdministration(identity);
  return Object.freeze({
    activities: allowed && identity.permissions.has("ACTIVITY_MANAGE"),
    farms: allowed && identity.permissions.has("FARM_ASSIGNMENT_MANAGE"),
    identities: allowed,
    roles: allowed && identity.permissions.has("IDENTITY_ROLE_MANAGE"),
    userLifecycle: allowed,
    warehouses:
      allowed && identity.permissions.has("INVENTORY_ASSIGNMENT_MANAGE")
  });
}

export type AdminCapabilities = ReturnType<typeof getAdminCapabilities>;
