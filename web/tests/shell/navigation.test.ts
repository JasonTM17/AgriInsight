import { describe, expect, it } from "vitest";

import {
  getActiveNavigationKey,
  getVisibleNavigation
} from "@/lib/permission-navigation";

describe("Field Ledger permission navigation", () => {
  it("keeps overview and exposes only permissions granted by Spring", () => {
    const items = getVisibleNavigation(
      {
        permissions: new Set(["FARM_READ", "INVENTORY_READ", "IDENTITY_USER_MANAGE"]),
        roles: new Set(["DATA_ANALYST"])
      }
    );

    expect(items.map((item) => item.key)).toEqual([
      "overview",
      "farms",
      "inventory",
      "cropHealth",
      "dataQuality",
      "assistant",
      "administration"
    ]);
  });

  it("does not infer visibility from stale role labels", () => {
    const items = getVisibleNavigation({
      permissions: new Set(),
      roles: new Set(["ROLE_ADMIN"]),
      tenantCode: "tenant-a"
    } as never);

    expect(items.map((item) => item.key)).toEqual(["overview"]);
  });

  it("marks module query state without treating unknown routes as an area", () => {
    expect(getActiveNavigationKey("/overview")).toBe("overview");
    expect(getActiveNavigationKey("/farms")).toBe("farms");
    expect(getActiveNavigationKey("/farms/3eb92f10-60dd-45cb-9160-7c569c3258b4")).toBe("farms");
    expect(getActiveNavigationKey("/work")).toBe("work");
    expect(getActiveNavigationKey("/inventory")).toBe("inventory");
    expect(getActiveNavigationKey("/crop-health/FIELD-001")).toBe("cropHealth");
    expect(getActiveNavigationKey("/data-quality")).toBe("dataQuality");
    expect(getActiveNavigationKey("/assistant")).toBe("assistant");
    expect(getActiveNavigationKey("/admin")).toBe("administration");
    expect(getActiveNavigationKey("/admin/users/3eb92f10-60dd-45cb-9160-7c569c3258b4")).toBe("administration");
    expect(getActiveNavigationKey("/protected", { module: "data-quality" })).toBe("dataQuality");
    expect(getActiveNavigationKey("/protected", { module: "not-a-module" })).toBe("overview");
    expect(getActiveNavigationKey("/platform/farms")).toBe("overview");
  });

  it("exposes the assistant only to supported scoped roles", () => {
    const farmManager = getVisibleNavigation({
      permissions: new Set(["FARM_READ"]),
      roles: new Set(["FARM_MANAGER"])
    }).find((item) => item.key === "assistant");
    const inventoryManager = getVisibleNavigation({
      permissions: new Set(["INVENTORY_READ"]),
      roles: new Set(["INVENTORY_MANAGER"])
    }).find((item) => item.key === "assistant");
    const supplier = getVisibleNavigation({
      permissions: new Set(["FARM_READ", "INVENTORY_READ"]),
      roles: new Set(["SUPPLIER"])
    }).find((item) => item.key === "assistant");

    expect(farmManager?.href).toBe("/assistant");
    expect(inventoryManager?.href).toBe("/assistant");
    expect(supplier).toBeUndefined();
  });

  it("links the implemented work area directly", () => {
    const work = getVisibleNavigation(
      new Set(["ACTIVITY_READ"])
    ).find((item) => item.key === "work");

    expect(work?.href).toBe("/work");
  });

  it("links the implemented inventory area directly", () => {
    const inventory = getVisibleNavigation(
      new Set(["INVENTORY_READ"])
    ).find((item) => item.key === "inventory");

    expect(inventory?.href).toBe("/inventory");
  });

  it("links administration directly and denies supplier navigation", () => {
    const administrator = getVisibleNavigation({
      permissions: new Set(["IDENTITY_USER_MANAGE"]),
      roles: new Set(["TENANT_ADMIN"])
    }).find((item) => item.key === "administration");
    const supplier = getVisibleNavigation({
      permissions: new Set(["IDENTITY_USER_MANAGE"]),
      roles: new Set(["SUPPLIER"])
    }).find((item) => item.key === "administration");

    expect(administrator?.href).toBe("/admin");
    expect(supplier).toBeUndefined();
  });
});
