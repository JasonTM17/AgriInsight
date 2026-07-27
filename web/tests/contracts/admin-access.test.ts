import { describe, expect, it } from "vitest";

import {
  canAccessAdministration,
  canManageAdminRoles
} from "@/features/admin/admin-access";

describe("tenant administration access", () => {
  it("requires user-management permission", () => {
    expect(canAccessAdministration({
      permissions: new Set(),
      roles: new Set(["TENANT_ADMIN"])
    })).toBe(false);
    expect(canAccessAdministration({
      permissions: new Set(["IDENTITY_USER_MANAGE"]),
      roles: new Set(["TENANT_ADMIN"])
    })).toBe(true);
  });

  it("denies suppliers even if a stale permission claim exists", () => {
    expect(canAccessAdministration({
      permissions: new Set(["IDENTITY_USER_MANAGE", "IDENTITY_ROLE_MANAGE"]),
      roles: new Set(["SUPPLIER"])
    })).toBe(false);
  });

  it("keeps role management behind its dedicated permission", () => {
    const identity = {
      permissions: new Set(["IDENTITY_USER_MANAGE"]),
      roles: new Set(["TENANT_ADMIN"])
    };

    expect(canManageAdminRoles(identity)).toBe(false);
    expect(canManageAdminRoles({
      ...identity,
      permissions: new Set(["IDENTITY_USER_MANAGE", "IDENTITY_ROLE_MANAGE"])
    })).toBe(true);
  });
});
