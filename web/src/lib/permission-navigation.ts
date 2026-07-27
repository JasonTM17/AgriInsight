import type { AuthorizationContext } from "@/server/auth/authorization-context";

import {
  NAVIGATION_DESCRIPTIONS,
  NAVIGATION_LABELS,
  NAVIGATION_ORDER,
  type NavigationKey
} from "@/content/vi/navigation";

export type NavigationIconName =
  | "grid"
  | "farm"
  | "clipboard"
  | "boxes"
  | "receipt"
  | "sprout"
  | "shield-check"
  | "users";

export type NavigationItem = Readonly<{
  key: NavigationKey;
  label: string;
  description: string;
  href: string;
  icon: NavigationIconName;
  forbiddenRoles?: readonly string[];
  requiredPermissions: readonly string[];
  requiredRoles?: readonly string[];
}>;

const NAVIGATION_DEFINITIONS: Readonly<Record<NavigationKey, NavigationItem>> = {
  overview: {
    key: "overview",
    label: NAVIGATION_LABELS.overview,
    description: NAVIGATION_DESCRIPTIONS.overview,
    href: "/overview",
    icon: "grid",
    requiredPermissions: []
  },
  farms: {
    key: "farms",
    label: NAVIGATION_LABELS.farms,
    description: NAVIGATION_DESCRIPTIONS.farms,
    href: "/farms",
    icon: "farm",
    requiredPermissions: ["FARM_READ"]
  },
  work: {
    key: "work",
    label: NAVIGATION_LABELS.work,
    description: NAVIGATION_DESCRIPTIONS.work,
    href: "/work",
    icon: "clipboard",
    requiredPermissions: ["ACTIVITY_READ"]
  },
  inventory: {
    key: "inventory",
    label: NAVIGATION_LABELS.inventory,
    description: NAVIGATION_DESCRIPTIONS.inventory,
    href: "/inventory",
    icon: "boxes",
    requiredPermissions: ["INVENTORY_READ"]
  },
  costs: {
    key: "costs",
    label: NAVIGATION_LABELS.costs,
    description: NAVIGATION_DESCRIPTIONS.costs,
    href: "/costs?lens=operating",
    icon: "receipt",
    requiredPermissions: ["COST_READ"]
  },
  cropHealth: {
    key: "cropHealth",
    label: NAVIGATION_LABELS.cropHealth,
    description: NAVIGATION_DESCRIPTIONS.cropHealth,
    href: "/crop-health",
    icon: "sprout",
    requiredPermissions: ["FARM_READ"],
    requiredRoles: ["TENANT_ADMIN", "EXECUTIVE", "DATA_ANALYST", "FARM_MANAGER"]
  },
  dataQuality: {
    key: "dataQuality",
    label: NAVIGATION_LABELS.dataQuality,
    description: NAVIGATION_DESCRIPTIONS.dataQuality,
    href: "/data-quality",
    icon: "shield-check",
    requiredPermissions: [],
    requiredRoles: ["TENANT_ADMIN", "DATA_ANALYST"]
  },
  administration: {
    key: "administration",
    label: NAVIGATION_LABELS.administration,
    description: NAVIGATION_DESCRIPTIONS.administration,
    href: "/admin",
    icon: "users",
    forbiddenRoles: ["SUPPLIER"],
    requiredPermissions: ["IDENTITY_USER_MANAGE", "IDENTITY_ROLE_MANAGE"]
  }
};

function hasAnyPermission(
  permissions: ReadonlySet<string>,
  requiredPermissions: readonly string[]
): boolean {
  return requiredPermissions.length === 0
    || requiredPermissions.some((permission) => permissions.has(permission));
}

function hasAnyRole(
  roles: ReadonlySet<string> | undefined,
  requiredRoles: readonly string[] | undefined
): boolean {
  if (!requiredRoles || requiredRoles.length === 0) return true;
  return roles !== undefined && requiredRoles.some((role) => roles.has(role));
}

function hasForbiddenRole(
  roles: ReadonlySet<string> | undefined,
  forbiddenRoles: readonly string[] | undefined
): boolean {
  return roles !== undefined
    && forbiddenRoles !== undefined
    && forbiddenRoles.some((role) => roles.has(role));
}

export function getVisibleNavigation(
  permissions:
    | ReadonlySet<string>
    | Pick<AuthorizationContext, "permissions" | "roles">
): readonly NavigationItem[] {
  const permissionSet = "permissions" in permissions ? permissions.permissions : permissions;
  const roles = "roles" in permissions ? permissions.roles : undefined;
  return NAVIGATION_ORDER
    .map((key) => NAVIGATION_DEFINITIONS[key])
    .filter((item) => hasAnyPermission(permissionSet, item.requiredPermissions))
    .filter((item) => !hasForbiddenRole(roles, item.forbiddenRoles))
    .filter((item) => hasAnyRole(roles, item.requiredRoles));
}

export function getActiveNavigationKey(
  pathname: string,
  searchParams?: URLSearchParams | Readonly<Record<string, string | undefined>>
): NavigationKey {
  if (pathname === "/overview") return "overview";
  if (pathname === "/farms" || pathname.startsWith("/farms/")) return "farms";
  if (pathname === "/work") return "work";
  if (pathname === "/inventory" || pathname.startsWith("/inventory/")) {
    return "inventory";
  }
  if (pathname === "/costs" || pathname.startsWith("/costs/")) {
    return "costs";
  }
  if (pathname === "/crop-health" || pathname.startsWith("/crop-health/")) {
    return "cropHealth";
  }
  if (pathname === "/data-quality" || pathname.startsWith("/data-quality/")) {
    return "dataQuality";
  }
  if (pathname === "/admin" || pathname.startsWith("/admin/")) {
    return "administration";
  }
  if (pathname !== "/protected") return "overview";
  const moduleKey = searchParams instanceof URLSearchParams
    ? searchParams.get("module")
    : searchParams?.module;
  if (moduleKey === "crop-health") return "cropHealth";
  if (moduleKey === "data-quality") return "dataQuality";
  const entry = Object.values(NAVIGATION_DEFINITIONS).find((item) =>
    item.href.endsWith(`module=${moduleKey}`)
  );
  return entry?.key ?? "overview";
}

export function getNavigationDefinitions(): readonly NavigationItem[] {
  return NAVIGATION_ORDER.map((key) => NAVIGATION_DEFINITIONS[key]);
}
