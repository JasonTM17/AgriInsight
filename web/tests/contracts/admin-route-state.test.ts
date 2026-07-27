import { describe, expect, it } from "vitest";

import {
  parseAdminAuditState,
  parseAdminDirectoryState
} from "@/features/admin/admin-route-state";

describe("tenant administration route state", () => {
  it("normalizes bounded directory filters", () => {
    expect(parseAdminDirectoryState({
      offset: "50",
      search: "Nguyễn",
      status: "all"
    })).toEqual({
      active: undefined,
      offset: 50,
      search: "Nguyễn",
      status: "all"
    });
  });

  it.each([
    { offset: "-1" },
    { offset: "25" },
    { offset: "10001" },
    { search: "x".repeat(121) },
    { status: "removed" },
    { unexpected: "value" }
  ])("rejects unsafe directory input %#", (input) => {
    expect(parseAdminDirectoryState(input)).toBeNull();
  });

  it("accepts only fixed audit outcomes and bounded codes", () => {
    expect(parseAdminAuditState({
      action: "USER_DEACTIVATE",
      offset: "0",
      outcome: "DENIED",
      targetType: "USER_PROFILE"
    })).toEqual({
      action: "USER_DEACTIVATE",
      offset: 0,
      outcome: "DENIED",
      targetType: "USER_PROFILE"
    });
    expect(parseAdminAuditState({ outcome: "UNKNOWN" })).toBeNull();
  });
});
