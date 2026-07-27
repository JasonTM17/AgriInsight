import { describe, expect, it } from "vitest";

import { dataQualityEnvelopeSchema } from "@/features/crop-quality/analytics-evidence-contract";
import { dataQualityEnvelopeFixture } from "../support/crop-quality-fixtures";

describe("Phase 2 data quality consumer contract", () => {
  it("preserves method, status, severity and safe lineage verbatim", () => {
    const parsed = dataQualityEnvelopeSchema.parse(dataQualityEnvelopeFixture);
    expect(parsed.payload.assessmentMethod).toBe("rule-based-heuristic");
    expect(parsed.payload.status).toBe("passed");
    expect(parsed.payload.severity).toBe("none");
    expect(parsed.freshness.dataStatus).toBe("current");
    expect(parsed.lineage.runId).toBe("synthetic-2026-07-18");
  });

  it.each(["current", "stale", "partial", "missing"] as const)(
    "accepts exact dataStatus %s",
    (dataStatus) => {
      expect(
        dataQualityEnvelopeSchema.safeParse({
          ...dataQualityEnvelopeFixture,
          freshness: { ...dataQualityEnvelopeFixture.freshness, dataStatus }
        }).success
      ).toBe(true);
    }
  );

  it("rejects an invented assessment method or severity", () => {
    expect(
      dataQualityEnvelopeSchema.safeParse({
        ...dataQualityEnvelopeFixture,
        payload: {
          ...dataQualityEnvelopeFixture.payload,
          assessmentMethod: "estimated"
        }
      }).success
    ).toBe(false);
    expect(
      dataQualityEnvelopeSchema.safeParse({
        ...dataQualityEnvelopeFixture,
        payload: { ...dataQualityEnvelopeFixture.payload, severity: "critical" }
      }).success
    ).toBe(false);
  });
});
