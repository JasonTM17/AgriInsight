import { describe, expect, it } from "vitest";

import {
  correctCostEntrySchema,
  postCostEntrySchema
} from "@/features/costs/cost-mutation-contract";
import { costMutationResponse } from "@/features/costs/cost-route-responses";

const FARM_ID = "41000000-0000-0000-0000-000000000001";
const ENTRY_ID = "41000000-0000-0000-0000-000000000021";
const BASE = {
  amountVnd: 1250000,
  category: "MATERIAL" as const,
  occurredAt: "2026-07-27T02:00:00Z",
  targetType: "FARM" as const,
  targetId: FARM_ID
};
const RESPONSE = {
  amountVnd: 1250000,
  category: "MATERIAL",
  description: null,
  entryKind: "POSTING",
  id: ENTRY_ID,
  occurredAt: "2026-07-27T02:00:00Z",
  reversalOf: null,
  signedAmountVnd: 1250000,
  sourceReference: null,
  targetId: FARM_ID,
  targetType: "FARM",
  version: 0
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

  it("validates successful posting responses before forwarding", async () => {
    const valid = await costMutationResponse(
      Response.json(RESPONSE, { status: 201 }),
      "corr-cost-001",
      "posting"
    );
    expect(valid.status).toBe(201);
    expect(await valid.json()).toMatchObject({
      id: ENTRY_ID,
      entryKind: "POSTING"
    });

    const invalid = await costMutationResponse(
      Response.json({ id: ENTRY_ID }, { status: 201 }),
      "corr-cost-002",
      "posting"
    );
    expect(invalid.status).toBe(502);
    expect(await invalid.json()).toMatchObject({
      code: "invalid_upstream_response"
    });
  });

  it("requires both immutable correction records in the upstream response", async () => {
    const reversal = {
      ...RESPONSE,
      entryKind: "REVERSAL",
      id: "41000000-0000-0000-0000-000000000022",
      reversalOf: ENTRY_ID,
      signedAmountVnd: -1250000
    };
    const valid = await costMutationResponse(
      Response.json({ replacement: RESPONSE, reversal }, { status: 201 }),
      "corr-cost-003",
      "correction"
    );
    expect(valid.status).toBe(201);

    const invalid = await costMutationResponse(
      Response.json({ replacement: RESPONSE }, { status: 201 }),
      "corr-cost-004",
      "correction"
    );
    expect(invalid.status).toBe(502);
  });
});
