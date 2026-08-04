import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const webRoot = resolve(import.meta.dirname, "../..");
const read = (relative: string) => readFileSync(resolve(webRoot, relative), "utf8");

describe("portfolio responsive layout contracts", () => {
  it("keeps the overview lead value inside a balanced desktop grid", () => {
    const css = read("src/features/overview/components/overview-farms.module.css");
    expect(css).toMatch(/\.summaryBand\s*\{[^}]*minmax\(24rem,\s*1fr\)/s);
    expect(css).toMatch(/\.summaryLead\s*\{[^}]*min-width:\s*0/s);
  });

  it("keeps cost KPIs readable across desktop, tablet, and mobile", () => {
    const css = read("src/features/costs/components/cost-analysis.module.css");
    expect(css).toMatch(/\.kpiCard strong\s*\{[^}]*white-space:\s*nowrap/s);
    expect(css).toMatch(/@media \(max-width:\s*70rem\)[\s\S]*?\.kpiGrid\s*\{[^}]*repeat\(2/s);
    expect(css).toMatch(/@media \(max-width:\s*48rem\)[\s\S]*?\.kpiGrid\s*\{[^}]*1fr/s);
  });

  it("uses readable single-column evidence and admin navigation on mobile", () => {
    const evidenceCss = read("src/features/crop-quality/components/crop-quality.module.css");
    const adminCss = read("src/features/admin/components/tenant-administration.module.css");
    expect(evidenceCss).toMatch(/@media \(max-width:\s*35rem\)[\s\S]*?\.contractBanner\s*\{[^}]*1fr/s);
    expect(adminCss).toMatch(/@media \(max-width:\s*35rem\)[\s\S]*?\.tabs\s*\{[^}]*grid/s);
  });

  it("runs geometry checks before every portfolio screenshot", () => {
    const capture = read("tests/capture/portfolio-media.spec.ts");
    expect(capture).toContain("await expectPortfolioLayout(page, surface, viewportName)");
    expect(capture).toContain("boxesIntersect(leadBox, metricBox)");
    expect(capture).toContain("Evidence contract must use one readable mobile column");
    expect(capture).toContain("Admin tabs require hidden horizontal discovery");
  });
});
