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
  requiredPermissions: readonly string[];
}>;

const NAVIGATION_DEFINITIONS: Readonly<Record<NavigationKey, NavigationItem>> = {
  overview: {
    key: "overview",
    label: NAVIGATION_LABELS.overview,
    description: NAVIGATION_DESCRIPTIONS.overview,
    href: "/protected",
    icon: "grid",
    requiredPermissions: []
  },
  farms: {
    key: "farms",
    label: NAVIGATION_LABELS.farms,
    description: NAVIGATION_DESCRIPTIONS.farms,
    href: "/protected?module=farms",
    icon: "farm",
    requiredPermissions: ["FARM_READ"]
  },
  work: {
    key: "work",
    label: NAVIGATION_LABELS.work,
    description: NAVIGATION_DESCRIPTIONS.work,
    href: "/protected?module=work",
    icon: "clipboard",
    requiredPermissions: ["ACTIVITY_READ", "WORKFORCE_PICKER_READ"]
  },
  inventory: {
    key: "inventory",
    label: NAVIGATION_LABELS.inventory,
    description: NAVIGATION_DESCRIPTIONS.inventory,
    href: "/protected?module=inventory",
    icon: "boxes",
    requiredPermissions: ["INVENTORY_READ"]
  },
  costs: {
    key: "costs",
    label: NAVIGATION_LABELS.costs,
    description: NAVIGATION_DESCRIPTIONS.costs,
    href: "/protected?module=costs",
    icon: "receipt",
    requiredPermissions: ["COST_READ"]
  },
  cropHealth: {
    key: "cropHealth",
    label: NAVIGATION_LABELS.cropHealth,
    description: NAVIGATION_DESCRIPTIONS.cropHealth,
    href: "/protected?module=crop-health",
    icon: "sprout",
    requiredPermissions: ["FARM_READ", "ACTIVITY_READ"]
  },
  dataQuality: {
    key: "dataQuality",
    label: NAVIGATION_LABELS.dataQuality,
    description: NAVIGATION_DESCRIPTIONS.dataQuality,
    href: "/protected?module=data-quality",
    icon: "shield-check",
    requiredPermissions: ["FARM_READ", "COST_READ"]
  },
  administration: {
    key: "administration",
    label: NAVIGATION_LABELS.administration,
    description: NAVIGATION_DESCRIPTIONS.administration,
    href: "/protected?module=administration",
    icon: "users",
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

export function getVisibleNavigation(
  permissions: ReadonlySet<string> | Pick<AuthorizationContext, "permissions">
): readonly NavigationItem[] {
  const permissionSet = "permissions" in permissions ? permissions.permissions : permissions;
  return NAVIGATION_ORDER
    .map((key) => NAVIGATION_DEFINITIONS[key])
    .filter((item) => hasAnyPermission(permissionSet, item.requiredPermissions));
}

export function getActiveNavigationKey(
  pathname: string,
  searchParams?: URLSearchParams | Readonly<Record<string, string | undefined>>
): NavigationKey {
  if (pathname !== "/protected") return "overview";
  const moduleKey = searchParams instanceof URLSearchParams
    ? searchParams.get("module")
    : searchParams?.module;
  const entry = Object.values(NAVIGATION_DEFINITIONS).find((item) =>
    item.href.endsWith(`module=${moduleKey}`)
  );
  return entry?.key ?? "overview";
}

export function getNavigationDefinitions(): readonly NavigationItem[] {
  return NAVIGATION_ORDER.map((key) => NAVIGATION_DEFINITIONS[key]);
}
