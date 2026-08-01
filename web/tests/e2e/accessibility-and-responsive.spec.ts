import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { loginWithRealOidc } from "./helpers/real-oidc-login";

const viewports = [
  { name: "mobile", width: 375, height: 812 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "compact-desktop", width: 1024, height: 768 },
  { name: "desktop", width: 1440, height: 900 },
  { name: "mobile-landscape", width: 844, height: 390 }
] as const;

const executiveRoutes = [
  "/overview",
  "/farms",
  "/work",
  "/inventory",
  "/costs?lens=operating",
  "/crop-health"
] as const;

async function expectAccessible(page: Page, route: string) {
  let results;

  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      await page.waitForLoadState("domcontentloaded");
      await expect(page.locator("main")).toBeVisible();
      results = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
        .analyze();
      break;
    } catch (error) {
      const navigationReset =
        error instanceof Error &&
        error.message.includes("Execution context was destroyed");
      if (!navigationReset || attempt === 3) {
        throw error;
      }
    }
  }

  if (!results) {
    throw new Error(`Accessibility scan did not complete for ${route}`);
  }

  const blockers = results.violations.filter((violation) =>
    ["critical", "serious"].includes(violation.impact ?? "")
  );
  expect(
    blockers.map((violation) => ({
      help: violation.help,
      id: violation.id,
      impact: violation.impact,
      route,
      targets: violation.nodes.flatMap((node) => node.target)
    }))
  ).toEqual([]);
}

async function expectResponsive(page: Page, route: string) {
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    const response = await page.goto(route);
    expect(response?.status(), `${viewport.name} GET ${route}`).toBe(200);
    await expect(page.locator("main")).toBeVisible();
    await expect
      .poll(
        async () => {
          try {
            return await page.evaluate(() => {
              const root = document.documentElement;
              const fits = root.scrollWidth <= root.clientWidth + 1;
              const diagnostics = {
                clientWidth: root.clientWidth,
                offenders: [...document.querySelectorAll<HTMLElement>("body *")]
                      .map((element) => ({
                        className: element.className,
                        id: element.id,
                        rect: element.getBoundingClientRect().toJSON(),
                        tagName: element.tagName
                      }))
                      .filter(({ rect }) =>
                        rect.left < -1 || rect.right > root.clientWidth + 1
                      )
                      .slice(0, 8),
                scrollWidth: root.scrollWidth
              };
              return fits ? "fits" : JSON.stringify(diagnostics);
            });
          } catch {
            return "page-evaluation-failed";
          }
        },
        { message: `${viewport.name} ${route} has horizontal overflow` }
      )
      .toBe("fits");
  }
}

async function expectFarmDetailYieldEvidence(page: Page): Promise<void> {
  const panel = page.locator('section[aria-labelledby="yield-forecast-title"]');
  const evidenceTable = panel.getByRole("region", {
    name: "Bảng bằng chứng dự báo sản lượng có thể cuộn"
  });
  await expect(panel).toBeVisible();
  await expect(evidenceTable).toBeVisible();
  await expect(evidenceTable.locator("tbody tr").first()).toBeVisible();
  await evidenceTable.focus();
  await expect(evidenceTable).toBeFocused();
  const disclosure = panel.locator("details").first();
  await disclosure.locator("summary").focus();
  await expect(disclosure.locator("summary")).toBeFocused();
}

test("@quality executive routes pass WCAG and responsive gates", async ({ page }) => {
  test.setTimeout(240_000);
  await loginWithRealOidc(page, "executive", "/overview");

  for (const route of executiveRoutes) {
    await page.setViewportSize({ width: 1440, height: 900 });
    const response = await page.goto(route);
    expect(response?.status(), `desktop GET ${route}`).toBe(200);
    await expect(page.locator("main")).toBeVisible();
    await expectAccessible(page, route);
    await expectResponsive(page, route);
  }

  await page.setViewportSize({ width: 1440, height: 900 });
  const farmsResponse = await page.goto("/farms");
  expect(farmsResponse?.status(), "desktop GET /farms for detail link").toBe(200);
  const farmHref = await page.locator('main a[href^="/farms/"]').first().getAttribute("href");
  expect(farmHref).toMatch(/^\/farms\/[0-9a-f-]{36}(?:\?.*)?$/);
  await expectAccessible(page, farmHref!);
  await expectResponsive(page, farmHref!);

  // A 720px layout viewport is the effective width of the 1440px desktop
  // view at 200% browser zoom; the evidence must remain usable at that width.
  await page.setViewportSize({ width: 720, height: 450 });
  const zoomResponse = await page.goto(farmHref!);
  expect(zoomResponse?.status(), "200% zoom-equivalent farm detail GET").toBe(200);
  await expectFarmDetailYieldEvidence(page);

  await page.emulateMedia({ reducedMotion: "reduce" });
  const reducedMotionResponse = await page.goto(farmHref!);
  expect(reducedMotionResponse?.status(), "reduced-motion farm detail GET").toBe(200);
  await expectFarmDetailYieldEvidence(page);
});

test("@quality analyst data quality passes WCAG and responsive gates", async ({
  page
}) => {
  test.setTimeout(120_000);
  await loginWithRealOidc(page, "analyst", "/data-quality");
  await expect(page.locator("main")).toBeVisible();
  await expectAccessible(page, "/data-quality");
  await expectResponsive(page, "/data-quality");
});

test("@quality tenant administration passes WCAG and responsive gates", async ({
  page
}) => {
  test.setTimeout(120_000);
  await loginWithRealOidc(page, "tenant-admin", "/admin");
  await expect(page.locator("main")).toBeVisible();
  await expectAccessible(page, "/admin");
  await expectResponsive(page, "/admin");
});
