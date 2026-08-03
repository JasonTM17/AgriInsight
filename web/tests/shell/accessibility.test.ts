import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const webRoot = resolve(import.meta.dirname, "../..");
const read = (relativePath: string) => readFileSync(resolve(webRoot, relativePath), "utf8");

describe("Field Ledger shell accessibility guardrails", () => {
  it("keeps labeled landmarks, skip navigation, and reduced motion", () => {
    const layout = read("src/app/layout.tsx");
    const shell = read("src/components/app-shell/app-shell.tsx");
    const rail = read("src/components/app-shell/navigation-rail.tsx");
    const css = read("src/app/globals.css");

    expect(layout).toContain("SkipLink");
    expect(shell).toContain('id="main-content"');
    expect(shell).toContain('data-workspace');
    expect(rail).toContain('aria-label="Điều hướng chính"');
    expect(rail).toContain('aria-current={active ? "page" : undefined}');
    expect(rail).toContain('event.key === "Escape"');
    expect(rail).toContain('event.key !== "Tab"');
    expect(rail).toContain("event.shiftKey");
    expect(css).toContain("@media (prefers-reduced-motion: reduce)");
    expect(css).toContain("min-height: 100dvh");
    expect(css).not.toMatch(/href=["']#["']/);
  });

  it("keeps touch targets and visible focus rings in the tokenized stylesheet", () => {
    const css = read("src/app/globals.css");
    expect(css).toContain("min-height: 2.75rem");
    expect(css).toContain("outline: 3px solid var(--focus)");
    expect(css).toMatch(
      /@media \(max-width: 48rem\)[\s\S]*?\.navigation-rail\s*\{[^}]*display:\s*none;[\s\S]*?\.navigation-rail--open\s*\{[^}]*display:\s*flex;/s
    );
    expect(read("src/components/app-shell/navigation-rail.tsx")).toContain(
      'window.matchMedia("(max-width: 48rem)")'
    );
    const administrationCss = read(
      "src/features/admin/components/tenant-administration.module.css"
    );
    expect(administrationCss).toMatch(
      /\.dataTable thead th\s*\{[^}]*position:\s*relative;/s
    );
  });

  it("contains Overview filters and charts inside the mobile workspace", () => {
    const overviewCss = read(
      "src/features/overview/components/overview-farms.module.css"
    );
    expect(overviewCss).toMatch(/\.stack > \*\s*\{[^}]*min-width:\s*0;/s);
    expect(overviewCss).toMatch(/\.trendChart\s*\{[^}]*overflow-x:\s*auto;/s);
    expect(overviewCss).toMatch(/\.periodFilter\s*\{[^}]*flex-wrap:\s*wrap;/s);
  });

  it("does not allow raw Stitch exports or CDN-only runtime dependencies", () => {
    const source = [
      read("src/components/app-shell/app-shell.tsx"),
      read("src/components/app-shell/navigation-rail.tsx"),
      read("src/app/layout.tsx")
    ].join("\n");
    expect(source).not.toMatch(/stitch|fonts\.googleapis|cdn\./i);
  });

  it("keeps one localized parent boundary for layout-level HTTP 403 responses", () => {
    const boundary = read("src/app/(platform)/forbidden.tsx");
    const panel = read("src/components/app-shell/platform-forbidden.tsx");

    expect(boundary).toContain("PlatformForbidden");
    expect(panel).toContain("Truy cập bị từ chối");
    expect(panel).not.toMatch(/subject|employeeId|warehouseIds/i);
  });
});
