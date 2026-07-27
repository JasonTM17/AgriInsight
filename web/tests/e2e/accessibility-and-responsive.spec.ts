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
              return {
                clientWidth: root.clientWidth,
                fits,
                offenders: fits
                  ? []
                  : [...document.querySelectorAll<HTMLElement>("body *")]
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
            });
          } catch {
            return { fits: false };
          }
        },
        { message: `${viewport.name} ${route} has horizontal overflow` }
      )
      .toMatchObject({ fits: true });
  }
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
