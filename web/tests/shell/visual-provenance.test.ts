import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import catalog from "../../../dashboard/assets/generated/catalog.json";
import { assertVisualCatalog } from "@/lib/visual-catalog";

const repoRoot = resolve(import.meta.dirname, "../../..");

describe("visual provenance", () => {
  it("validates the canonical eight-entry catalog", () => {
    expect(() => assertVisualCatalog(catalog)).not.toThrow();
    expect(catalog.entries).toHaveLength(8);
    expect(catalog.entries.filter((entry) => entry.demoEvidence).map((entry) => entry.area)).toEqual(["crop-health"]);
  });

  it("matches every reviewed WebP signature, size, and hash", () => {
    for (const entry of catalog.entries) {
      const bytes = readFileSync(resolve(repoRoot, "dashboard/assets/generated", entry.filename));
      expect(bytes.subarray(0, 4).toString("ascii")).toBe("RIFF");
      expect(bytes.subarray(8, 12).toString("ascii")).toBe("WEBP");
      expect(bytes.byteLength).toBe(entry.bytes);
      expect(createHash("sha256").update(bytes).digest("hex")).toBe(entry.sha256);
    }
  });

  it("keeps generated web output ignored and deterministic", () => {
    const ignore = readFileSync(resolve(repoRoot, ".gitignore"), "utf8");
    const syncScript = readFileSync(resolve(repoRoot, "web/scripts/sync-dashboard-assets.mjs"), "utf8");
    expect(ignore).toContain("web/public/visuals/");
    expect(syncScript).toContain("provenance-manifest.json");
    expect(syncScript).not.toMatch(/stitch\.withgoogle|<html|fonts\.googleapis/i);
  });
});
