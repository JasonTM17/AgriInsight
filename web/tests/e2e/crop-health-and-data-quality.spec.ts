import { expect, test } from "@playwright/test";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Crop quality E2E requires ${name}`);
  return value;
}

async function login(
  page: import("@playwright/test").Page,
  returnTo: string,
  username = "AGRIINSIGHT_WEB_E2E_USERNAME",
  password = "AGRIINSIGHT_WEB_E2E_PASSWORD"
) {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(required(username));
  await page.locator("#password").fill(required(password));
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(new RegExp(`${returnTo.replace(/[?&=]/g, "\\$&")}$`));
}

async function expectNoHorizontalOverflow(page: import("@playwright/test").Page) {
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
}

test("@crop-health executive sees scoped evidence and data quality lineage", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, "/crop-health");
  await expect(page.getByTestId("crop-health-page")).toBeVisible();
  await expect(
    page
      .getByTestId("crop-health-page")
      .getByRole("heading", { name: "Sức khỏe cây trồng" })
  ).toBeVisible();
  await expect(page.getByTestId("crop-demo-warning")).toContainText("Ảnh minh họa do AI tạo");
  await expect(page.getByText("assessmentMethod=rule-based-heuristic")).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole("link", { name: /Khu Bắc/ }).click();
  await expect(page).toHaveURL(/\/crop-health\/[A-Za-z0-9_-]+$/);
  await expect(page.getByTestId("crop-health-detail-page")).toBeVisible();
  await expect(page.getByTestId("crop-demo-warning")).toContainText("không phải bằng chứng thực địa");

  await page.goto("/data-quality");
  await expect(page.getByTestId("data-quality-page")).toBeVisible();
  await expect(page.getByText("assessmentMethod=rule-based-heuristic")).toBeVisible();
  await expect(page.getByText("Dòng cách ly")).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("@crop-health supplier receives denied states for both analytics areas", async ({ page }) => {
  await login(
    page,
    "/crop-health",
    "AGRIINSIGHT_WEB_E2E_DENIED_USERNAME",
    "AGRIINSIGHT_WEB_E2E_DENIED_PASSWORD"
  );
  await expect(page.getByText("Truy cập bị từ chối")).toBeVisible();
  expect((await page.request.get("/crop-health")).status()).toBe(403);
  await page.goto("/data-quality");
  await expect(page.getByText("Truy cập bị từ chối")).toBeVisible();
  expect((await page.request.get("/data-quality")).status()).toBe(403);
  await expect(page.locator("body")).not.toContainText(/access_token|refresh_token|Bearer\s/i);
  await expectNoHorizontalOverflow(page);
});
