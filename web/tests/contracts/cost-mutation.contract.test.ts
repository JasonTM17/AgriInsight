import { describe, expect, it } from "vitest";

import {
  correctCostEntrySchema,
  postCostEntrySchema
} from "@/features/costs/cost-mutation-contract";

const FARM_ID = "41000000-0000-0000-0000-000000000001";
const ENTRY_ID = "41000000-0000-0000-0000-000000000021";
const BASE = {
  amountVnd: 1250000,
  category: "MATERIAL" as const,
  occurredAt: "2026-07-27T02:00:00Z",
  targetType: "FARM" as const,
  targetId: FARM_ID
};

describe("cost mutation contract", () => {
  it("accepts a scoped operating-cost posting", () => {
    expect(postCostEntrySchema.parse(BASE)).toMatchObject(BASE);
  });

  it("requires targetId for non-tenant allocations", () => {
    expect(
      postCostEntrySchema.safeParse({
        ...BASE,
        targetId: undefined
      }).success
    ).toBe(false);
  });

  it("rejects a targetId on tenant allocations", () => {
    expect(
      postCostEntrySchema.safeParse({
        ...BASE,
        targetId: FARM_ID,
        targetType: "TENANT"
      }).success
    ).toBe(false);
  });

  it("requires a correction reason and stable entry id", () => {
    expect(
      correctCostEntrySchema.safeParse({
        ...BASE,
        entryId: ENTRY_ID,
        correctionReason: "Sửa phân bổ hóa đơn"
      }).success
    ).toBe(true);
    expect(
      correctCostEntrySchema.safeParse({
        ...BASE,
        entryId: "not-an-id",
        correctionReason: ""
      }).success
    ).toBe(false);
  });
});
