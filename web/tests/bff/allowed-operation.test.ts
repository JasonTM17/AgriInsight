import { describe, expect, it } from "vitest";

import {
  ALLOWED_OPERATIONS,
  resolveAllowedOperation
} from "@/server/bff/allowed-operation";

describe("exact upstream allowlist", () => {
  it("contains only fixed relative HTTP paths and GET foundation operations", () => {
    for (const operation of Object.values(ALLOWED_OPERATIONS)) {
      expect(operation.method).toBe("GET");
      expect(operation.path).toMatch(
        /^\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\})(?:\/(?:[A-Za-z0-9_-]+|\{[A-Za-z][A-Za-z0-9]*\}))*$/
      );
      expect(operation.path).not.toContain("..");
      expect(operation.path).not.toContain("\\");
    }
  });

  it.each([
    "http://metadata.invalid/latest",
    "../admin",
    "/api/v1/me",
    "analyticsOverview%2f..%2fadmin"
  ])("rejects caller-controlled operation %s", (candidate) => {
    expect(() => resolveAllowedOperation(candidate)).toThrow(
      "not allowlisted"
    );
  });
});
