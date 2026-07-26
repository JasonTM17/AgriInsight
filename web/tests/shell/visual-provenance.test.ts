import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { describe, expect, it } from "vitest";

import catalog from "../../../dashboard/assets/generated/catalog.json";
import { ReviewedVisual } from "@/components/media/reviewed-visual";
import {
  assertVisualCatalog,
  VISUAL_CATALOG
} from "@/lib/visual-catalog";

const repoRoot = resolve(import.meta.dirname, "../../..");

describe("visual provenance", () => {
  it("validates the canonical eight-entry catalog", () => {
    expect(() => assertVisualCatalog(catalog)).not.toThrow();
    expect(VISUAL_CATALOG).toEqual(catalog);
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
    const nextConfig = readFileSync(resolve(repoRoot, "web/next.config.ts"), "utf8");
    expect(ignore).toContain("web/public/visuals/");
    expect(syncScript).toContain("provenance-manifest.json");
    expect(nextConfig).toContain("const repositoryRoot = fileURLToPath");
    expect(nextConfig).toContain("turbopack:");
    expect(nextConfig).toContain("root: repositoryRoot");
    expect(syncScript).not.toMatch(/stitch\.withgoogle|<html|fonts\.googleapis/i);
  });

  it("renders reviewed assets directly without CSP-blocked inline styles", () => {
    const markup = renderToStaticMarkup(
      createElement(ReviewedVisual, {
        alt: "Bối cảnh nông trại đã duyệt",
        filename: "overview-fields.webp",
        height: 800,
        width: 1200
      })
    );
    expect(markup).toContain('src="/visuals/overview-fields.webp"');
    expect(markup).toContain('width="1200"');
    expect(markup).toContain('height="800"');
    expect(markup).not.toContain("style=");
  });
});
