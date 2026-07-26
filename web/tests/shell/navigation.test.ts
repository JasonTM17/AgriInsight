import { describe, expect, it } from "vitest";

import {
  getActiveNavigationKey,
  getVisibleNavigation
} from "@/lib/permission-navigation";

describe("Field Ledger permission navigation", () => {
  it("keeps overview and exposes only permissions granted by Spring", () => {
    const items = getVisibleNavigation(
      new Set(["FARM_READ", "INVENTORY_READ", "IDENTITY_USER_MANAGE"])
    );

    expect(items.map((item) => item.key)).toEqual([
      "overview",
      "farms",
      "inventory",
      "cropHealth",
      "dataQuality",
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
    expect(getActiveNavigationKey("/protected", { module: "data-quality" })).toBe("dataQuality");
    expect(getActiveNavigationKey("/protected", { module: "not-a-module" })).toBe("overview");
    expect(getActiveNavigationKey("/platform/farms")).toBe("overview");
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
});
