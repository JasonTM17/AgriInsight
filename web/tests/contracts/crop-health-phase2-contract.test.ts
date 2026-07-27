import { describe, expect, it } from "vitest";

import { cropHealthEnvelopeSchema } from "@/features/crop-quality/analytics-evidence-contract";
import {
  cropHealthHref,
  parseCropHealthRouteState,
  parseFieldCode
} from "@/features/crop-quality/crop-health-route-state";
import { cropHealthEnvelopeFixture } from "../support/crop-quality-fixtures";

describe("Phase 2 crop health consumer contract", () => {
  it("preserves server-owned taxonomy, evidence and provenance", () => {
    const parsed = cropHealthEnvelopeSchema.parse(cropHealthEnvelopeFixture);
    expect(parsed.freshness.dataStatus).toBe("current");
    expect(parsed.payload.assessmentMethod).toBe("rule-based-heuristic");
    expect(parsed.payload.severity).toBe("low");
    expect(parsed.lineage).toMatchObject({
      asOf: "2026-07-18",
      generatedAt: "2026-07-18T01:00:00Z",
      runId: "synthetic-2026-07-18"
    });
    expect(parsed.payload.evidenceSignals).toEqual([
      { name: "monitoredFields", unit: null, value: 1 }
    ]);
  });

  it.each(["fresh", "offline", "unknown"])(
    "rejects non-contract dataStatus %s",
    (dataStatus) => {
      expect(
        cropHealthEnvelopeSchema.safeParse({
          ...cropHealthEnvelopeFixture,
          freshness: { ...cropHealthEnvelopeFixture.freshness, dataStatus }
        }).success
      ).toBe(false);
    }
  );

  it("rejects extra response fields and unsafe route inputs", () => {
    expect(
      cropHealthEnvelopeSchema.safeParse({
        ...cropHealthEnvelopeFixture,
        payload: { ...cropHealthEnvelopeFixture.payload, confidence: 0.9 }
      }).success
    ).toBe(false);
    expect(parseCropHealthRouteState({ farm: "../other" })).toBeNull();
    expect(parseCropHealthRouteState({ offset: ["0", "1"] })).toBeNull();
    expect(parseCropHealthRouteState({ tenantId: "hidden" })).toBeNull();
    expect(parseFieldCode("../FIELD-001")).toBeNull();
  });

  it("builds stable bounded pagination links", () => {
    const state = parseCropHealthRouteState({
      farm: "FARM-001",
      limit: "25",
      offset: "50"
    });
    expect(state).not.toBeNull();
    expect(cropHealthHref(state!, 75)).toBe(
      "/crop-health?farm=FARM-001&limit=25&offset=75"
    );
  });
});
