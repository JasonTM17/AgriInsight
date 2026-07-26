import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const webRoot = resolve(import.meta.dirname, "../..");
const read = (relative: string) => readFileSync(resolve(webRoot, relative), "utf8");

describe("overview and farm presentation contracts", () => {
  it("keeps KPI rendering on the server and exposes tabular equivalents", () => {
    const overview = read("src/features/overview/components/overview-dashboard.tsx");
    const trend = read("src/features/overview/components/monthly-financial-trend.tsx");
    const farms = read("src/features/farms/components/farm-list.tsx");
    expect(overview).not.toContain('"use client"');
    expect(farms).not.toContain('"use client"');
    expect(overview).toContain("<table>");
    expect(overview).toContain("<caption");
    expect(farms).toContain("<table>");
    expect(farms).toContain("<caption");
    expect(trend).toContain('aria-hidden="true"');
    expect(trend).toContain("Bảng tương đương của biểu đồ");
    expect(trend).toContain("value < 0");
    expect(trend).not.toContain("notation: \"compact\"");
    expect(overview).not.toMatch(/\.reduce\(|localStorage|sessionStorage/);
  });

  it("renders only safe lineage fields and contextual image semantics", () => {
    const lineage = read("src/features/overview/components/lineage-banner.tsx");
    const overview = read("src/features/overview/components/overview-dashboard.tsx");
    expect(lineage).toContain("artifactAgeHours");
    expect(lineage).toContain("contractVersion");
    expect(lineage).toContain("manifestFingerprint");
    expect(lineage).toContain("runId");
    expect(lineage).not.toMatch(/tenantId|filesystem|manifestPath|sources/);
    expect(overview).toContain("alt={visual.alt}");
    expect(overview).toContain("không phải bằng chứng số liệu");
    expect(lineage).toContain("formatScope");
  });

  it("keeps loading and errors inside stable route shells", () => {
    const overviewLayout = read("src/app/(platform)/overview/layout.tsx");
    const farmsLayout = read("src/app/(platform)/farms/layout.tsx");
    const overviewLoading = read("src/app/(platform)/overview/loading.tsx");
    const farmsLoading = read("src/app/(platform)/farms/loading.tsx");
    expect(overviewLayout).toContain("<AppShell");
    expect(farmsLayout).toContain("<AppShell");
    expect(overviewLoading).not.toContain("<main");
    expect(farmsLoading).not.toContain("<main");
  });

  it("keeps overview and farms behind the proxy boundary", () => {
    const proxy = read("src/proxy.ts");
    expect(proxy).toContain('"/overview"');
    expect(proxy).toContain('"/farms"');
  });
});
