import { expect, test } from "@playwright/test";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Cost E2E requires ${name}`);
  return value;
}

async function login(
  page: import("@playwright/test").Page,
  returnTo = "/costs?lens=operating"
) {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(required("AGRIINSIGHT_WEB_E2E_USERNAME"));
  await page.locator("#password").fill(required("AGRIINSIGHT_WEB_E2E_PASSWORD"));
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/costs\?lens=operating/);
}

async function expectNoHorizontalOverflow(page: import("@playwright/test").Page) {
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
}

test("@costs executive switches the two lenses and sees safe export links", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await expect(page.getByTestId("cost-analysis-page")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Phân tích chi phí" })).toBeVisible();
  await expect(page.getByRole("tab", { name: /Vận hành/ })).toHaveAttribute(
    "aria-selected",
    "true"
  );
  await expectNoHorizontalOverflow(page);

  await page.getByRole("tab", { name: /Mua hàng/ }).click();
  await expect(page).toHaveURL(/\/costs\?lens=procurement/);
  await expect(page.getByText("FASTAPI GOLD SNAPSHOT")).toBeVisible();
  const exportLink = page.getByRole("link", { name: "Tải CSV" });
  await expect(exportLink).toHaveAttribute("href", /\/api\/costs\/export\?/);
  await expect(exportLink).toHaveAttribute("href", /scope=procurement/);
  await expectNoHorizontalOverflow(page);
});

test("@costs supplier receives a generic denied scope", async ({ page }) => {
  await page.goto("/api/auth/login?returnTo=%2Fcosts%3Flens%3Doperating");
  await page.locator("#username").fill(required("AGRIINSIGHT_WEB_E2E_DENIED_USERNAME"));
  await page.locator("#password").fill(required("AGRIINSIGHT_WEB_E2E_DENIED_PASSWORD"));
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL("http://localhost:3100/costs?lens=operating");
  await expect(page.getByText("Phiên hiện tại không có quyền đọc dữ liệu chi phí.")).toBeVisible();
  await expect(page.locator("body")).not.toContainText(/access_token|refresh_token|Bearer\s/i);
  await expectNoHorizontalOverflow(page);
});
