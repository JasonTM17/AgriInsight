import type { AuthorizationContext } from "@/server/auth/authorization-context";

type AnalyticsIdentity = Pick<AuthorizationContext, "permissions" | "roles">;

const CROP_HEALTH_ROLES = new Set([
  "TENANT_ADMIN",
  "EXECUTIVE",
  "DATA_ANALYST",
  "FARM_MANAGER"
]);
const DATA_QUALITY_ROLES = new Set(["TENANT_ADMIN", "DATA_ANALYST"]);

export function canAccessCropHealth(identity: AnalyticsIdentity): boolean {
  return identity.permissions.has("FARM_READ")
    && intersects(identity.roles, CROP_HEALTH_ROLES);
}

export function canAccessDataQuality(identity: AnalyticsIdentity): boolean {
  return intersects(identity.roles, DATA_QUALITY_ROLES);
}

function intersects(
  values: ReadonlySet<string>,
  expected: ReadonlySet<string>
): boolean {
  return [...values].some((value) => expected.has(value));
}
