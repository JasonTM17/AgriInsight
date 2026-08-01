import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

import { expect, test, type Locator, type Page } from "@playwright/test";

const outputRoot = resolve(import.meta.dirname, "../../../artifacts/media-capture");
const screenDirectory = resolve(outputRoot, "screens");
const frameDirectory = resolve(outputRoot, "frames");
const desktop = { width: 1440, height: 900 } as const;
const mobile = { width: 390, height: 844 } as const;

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Yield forecast media capture requires ${name}`);
  return value;
}

async function login(page: Page): Promise<void> {
  await page.goto("/api/auth/login?returnTo=%2Ffarms");
  await page.locator("#username").fill(required("AGRIINSIGHT_WEB_E2E_USERNAME"));
  await page.locator("#password").fill(required("AGRIINSIGHT_WEB_E2E_PASSWORD"));
  await page.locator("#kc-login").click();
}

async function expectReadyPage(page: Page): Promise<void> {
  const title = page.getByRole("heading", { level: 1 }).first();
  await expect(title).toBeVisible();
  await expect.poll(async () => (await title.innerText()).trim().length).toBeGreaterThan(2);
  await expect(page.locator("body")).not.toContainText(/không có quyền|Liên kết .* không hợp lệ/i);
  await expect(page.locator("body")).not.toContainText(/Đang tải|Chưa có dữ liệu/i);
  await page.waitForLoadState("networkidle");
}

async function captureViewport(page: Page, directory: string, name: string): Promise<void> {
  await mkdir(directory, { recursive: true });
  await page.screenshot({ path: resolve(directory, `${name}.png`), fullPage: false });
}

async function alignPanelAtViewportTop(page: Page, panel: Locator): Promise<void> {
  const expectedScrollY = await panel.evaluate((element) => {
    const panelTop = window.scrollY + element.getBoundingClientRect().top;
    const targetScrollY = Math.max(0, panelTop - 32);
    window.scrollTo(0, targetScrollY);
    return Math.round(targetScrollY);
  });
  await expect
    .poll(() => page.evaluate(() => ({ x: Math.round(window.scrollX), y: Math.round(window.scrollY) })))
    .toEqual({ x: 0, y: expectedScrollY });
}

test("@capture yield forecast evidence", async ({ page }) => {
  await page.setViewportSize(desktop);
  await login(page);
  await expect(page).toHaveURL(/\/farms$/);
  await expectReadyPage(page);

  const firstFarm = page.locator('main a[href^="/farms/"]').first();
  await expect(firstFarm).toBeVisible();
  await firstFarm.click();
  await expect(page).toHaveURL(/\/farms\/[0-9a-f-]{36}/);
  await expectReadyPage(page);

  const panel = page.locator('section[aria-labelledby="yield-forecast-title"]');
  const evidenceTable = panel.getByRole("region", {
    name: "Bảng bằng chứng dự báo sản lượng có thể cuộn"
  });
  await expect(
    panel.getByRole("heading", { name: "Dự báo sản lượng theo mùa vụ" })
  ).toBeVisible();
  await expect(evidenceTable).toBeVisible();
  await expect(evidenceTable.locator("tbody tr").first()).toBeVisible();

  await evidenceTable.evaluate((element) => { element.scrollLeft = 0; });
  await alignPanelAtViewportTop(page, panel);
  await captureViewport(page, screenDirectory, "yield-forecast-desktop");
  await captureViewport(page, frameDirectory, "yield-forecast-01");

  const disclosure = panel.locator("details").first();
  await disclosure.locator("summary").click();
  await expect(disclosure).toHaveAttribute("open", "");
  await evidenceTable.evaluate((element) => {
    element.scrollLeft = element.scrollWidth - element.clientWidth;
  });
  await alignPanelAtViewportTop(page, panel);
  await captureViewport(page, frameDirectory, "yield-forecast-02");

  await page.setViewportSize(mobile);
  await evidenceTable.evaluate((element) => { element.scrollLeft = 0; });
  await alignPanelAtViewportTop(page, panel);
  await captureViewport(page, screenDirectory, "yield-forecast-mobile");
});
