import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

import { expect, test, type Page } from "@playwright/test";

const OUTPUT_ROOT = resolve(
  import.meta.dirname,
  "../../../artifacts/media-capture"
);
const SCREEN_DIR = resolve(OUTPUT_ROOT, "screens");
const FRAME_DIR = resolve(OUTPUT_ROOT, "frames");
const DESKTOP = { width: 1440, height: 900 } as const;
const MOBILE = { width: 390, height: 844 } as const;

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Media capture requires ${name}`);
  return value;
}

async function login(page: Page, userEnv: string, passwordEnv: string, returnTo: string) {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(required(userEnv));
  await page.locator("#password").fill(required(passwordEnv));
  await page.locator("#kc-login").click();
}

/**
 * A screenshot is only worth publishing if the page actually rendered data.
 * This refuses to capture an empty, denied, loading or error surface, so a bad
 * frame fails the run instead of landing in the README.
 */
async function expectPopulated(page: Page, heading?: string) {
  const title = page.getByRole("heading", { level: 1 }).first();
  await expect(title).toBeVisible();
  if (heading) {
    await expect(title).toContainText(heading);
  }
  // A one-character heading would satisfy a loose matcher while the page is
  // still a shell, so require real text before anything is photographed.
  await expect
    .poll(async () => (await title.innerText()).trim().length)
    .toBeGreaterThan(2);
  const body = page.locator("body");
  await expect(body).not.toContainText(/không có quyền|Liên kết .* không hợp lệ/i);
  await expect(body).not.toContainText(/Đang tải|Chưa có dữ liệu/i);
  await expect
    .poll(async () => page.locator("main :is(table tbody tr, article, li)").count())
    .toBeGreaterThan(0);
  await page.waitForLoadState("networkidle");
}

async function shoot(page: Page, name: string) {
  await mkdir(SCREEN_DIR, { recursive: true });
  await page.screenshot({
    path: resolve(SCREEN_DIR, `${name}.png`),
    fullPage: true
  });
}

async function shootViewport(page: Page, name: string) {
  await mkdir(SCREEN_DIR, { recursive: true });
  await page.screenshot({
    path: resolve(SCREEN_DIR, `${name}.png`),
    fullPage: false
  });
}

async function frame(page: Page, index: number) {
  await mkdir(FRAME_DIR, { recursive: true });
  await page.screenshot({
    path: resolve(FRAME_DIR, `tour-${String(index).padStart(2, "0")}.png`),
    fullPage: false
  });
}

async function forecastFrame(page: Page, index: number) {
  await mkdir(FRAME_DIR, { recursive: true });
  await page.screenshot({
    path: resolve(FRAME_DIR, `forecast-${String(index).padStart(2, "0")}.png`),
    fullPage: false
  });
}

test("@capture executive intelligence surfaces", async ({ page }) => {
  await page.setViewportSize(DESKTOP);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_USERNAME",
    "AGRIINSIGHT_WEB_E2E_PASSWORD",
    "/overview"
  );

  await expect(page).toHaveURL(/\/overview$/);
  await expectPopulated(page);
  await shoot(page, "01-overview");

  await page.goto("/farms");
  await expectPopulated(page);
  await shoot(page, "02-farms");

  const firstFarm = page.locator('main a[href^="/farms/"]').first();
  await expect(firstFarm).toBeVisible();
  await firstFarm.click();
  await expect(page).toHaveURL(/\/farms\/[0-9a-f-]{36}/);
  await expectPopulated(page);
  await shoot(page, "03-farm-detail");
});

test("@capture warehouse inventory control", async ({ page }) => {
  await page.setViewportSize(DESKTOP);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_INVENTORY_USERNAME",
    "AGRIINSIGHT_WEB_E2E_INVENTORY_PASSWORD",
    "/inventory"
  );

  await expect(page).toHaveURL(/\/inventory\?warehouseId=[0-9a-f-]{36}$/);
  await expect(page.getByTestId("inventory-control-page")).toBeVisible();
  await expect(page.getByTestId("inventory-balance-table")).toBeVisible();
  await expectPopulated(page, "Kiểm soát tồn kho");
  await shoot(page, "04-inventory-control");

  const forecastPanel = page.locator('section[aria-labelledby="forecast-title"]');
  const forecastTableScroll = forecastPanel.locator("table").locator("..");
  await expect(
    page.getByRole("heading", { name: "Bằng chứng dự báo nhu cầu" })
  ).toBeVisible();
  await forecastPanel.scrollIntoViewIfNeeded();
  await shootViewport(page, "inventory-demand-forecast-desktop");
  await forecastFrame(page, 1);

  await forecastTableScroll.evaluate((element) => {
    element.scrollLeft = element.scrollWidth;
  });
  const firstDisclosure = forecastPanel.locator("details").first();
  await firstDisclosure.locator("summary").click();
  await expect(firstDisclosure).toHaveAttribute("open", "");
  await forecastFrame(page, 2);

  await forecastTableScroll.evaluate((element) => {
    element.scrollLeft = element.scrollWidth / 2;
  });
  await forecastFrame(page, 3);

  await page.setViewportSize(MOBILE);
  await forecastPanel.scrollIntoViewIfNeeded();
  await shootViewport(page, "inventory-demand-forecast-mobile");
  await expect(page.getByTestId("inventory-transaction-form")).toBeVisible();
  await shoot(page, "05-inventory-mobile");
});

test("@capture mobile work operations", async ({ page }) => {
  await page.setViewportSize(MOBILE);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_WORK_USERNAME",
    "AGRIINSIGHT_WEB_E2E_WORK_PASSWORD",
    "/work"
  );

  await expect(page).toHaveURL(/\/work/);
  await expect(page.getByTestId("work-activity-queue")).toBeVisible();
  await expect(page.getByTestId("work-activity-card").first()).toBeVisible();
  await shoot(page, "06-work-queue");

  await page.getByTestId("work-activity-card").first().click();
  await expect(page.getByTestId("work-activity-detail")).toBeVisible();
  await shoot(page, "07-work-detail");
});

test("@capture navigation tour frames", async ({ page }) => {
  await page.setViewportSize(DESKTOP);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_USERNAME",
    "AGRIINSIGHT_WEB_E2E_PASSWORD",
    "/overview"
  );

  await expectPopulated(page);
  await frame(page, 1);

  await page.goto("/farms");
  await expectPopulated(page);
  await frame(page, 2);

  const firstFarm = page.locator('main a[href^="/farms/"]').first();
  await firstFarm.click();
  await expect(page).toHaveURL(/\/farms\/[0-9a-f-]{36}/);
  await expectPopulated(page);
  await frame(page, 3);
  await page.mouse.wheel(0, 700);
  await page.waitForTimeout(400);
  await frame(page, 4);
});
