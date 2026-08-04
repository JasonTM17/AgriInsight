import { describe, expect, it } from "vitest";

import {
  hasContainingHorizontalBoundary,
  type HorizontalBoundary,
  type HorizontalOverflowCandidate
} from "./horizontal-overflow-boundary";

const scrollport: HorizontalBoundary = {
  explicitlyAllowsNonInteractiveClip: false,
  isBounded: true,
  left: 17,
  overflowX: "auto",
  right: 373
};

function candidate(
  boundaries: readonly HorizontalBoundary[],
  containsInteractiveContent = true
): HorizontalOverflowCandidate {
  return {
    boundaries,
    containsInteractiveContent,
    offender: {
      className: "dataTable",
      rect: {
        bottom: 200,
        height: 100,
        left: 17,
        right: 849,
        top: 100,
        width: 832,
        x: 17,
        y: 100
      },
      tagName: "TABLE"
    }
  };
}

describe("portfolio horizontal overflow containment", () => {
  it("accepts an interactive table when an outer clip contains its scrollport", () => {
    expect(hasContainingHorizontalBoundary(candidate([
      scrollport,
      { ...scrollport, overflowX: "hidden" }
    ]))).toBe(true);
  });

  it("rejects an outer clip that narrows an accepted scrollport", () => {
    expect(hasContainingHorizontalBoundary(candidate([
      scrollport,
      { ...scrollport, left: 30, overflowX: "hidden", right: 360 }
    ]))).toBe(false);
  });

  it("rejects interactive clipping before a later scroll boundary", () => {
    expect(hasContainingHorizontalBoundary(candidate([
      { ...scrollport, overflowX: "hidden" },
      scrollport
    ]))).toBe(false);
  });

  it("accepts only explicit non-interactive clipping without a scrollport", () => {
    expect(hasContainingHorizontalBoundary(candidate([{
      ...scrollport,
      explicitlyAllowsNonInteractiveClip: true,
      overflowX: "clip"
    }], false))).toBe(true);
    expect(hasContainingHorizontalBoundary(candidate([{
      ...scrollport,
      overflowX: "clip"
    }], false))).toBe(false);
  });
});
