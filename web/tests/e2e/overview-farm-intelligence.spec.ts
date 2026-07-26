import { expect, test } from "@playwright/test";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Overview E2E requires ${name}`);
  return value;
}

test("@overview real executive can drill from overview to a scoped farm", async ({
  page
}) => {
  await page.goto("/api/auth/login?returnTo=/overview");
  await page.locator("#username").fill(required("AGRIINSIGHT_WEB_E2E_USERNAME"));
  await page.locator("#password").fill(required("AGRIINSIGHT_WEB_E2E_PASSWORD"));
  await page.locator("#kc-login").click();

  await expect(page).toHaveURL("http://localhost:3100/overview");
  await expect(
    page.getByRole("heading", { name: "Điểm cần xem xét" })
  ).toBeVisible();
  await expect(page.getByText("Phiên dữ liệu")).toBeVisible();
  await expect(page.getByText("Dấu vân tay dữ liệu")).toBeVisible();
  await expect(page.locator("body")).not.toContainText(/access_token|refresh_token|Bearer\s/i);

  await page.locator('select[name="datePreset"]').selectOption("last-30-days");
  await page.getByRole("button", { name: "Áp dụng kỳ" }).click();
  await expect(page).toHaveURL(/\/overview\?datePreset=last-30-days$/);
  await expect(page.getByText(/30 ngày gần nhất \(/).first()).toBeVisible();

  await page.getByRole("link", { name: "Xem hiệu quả nông trại" }).click();
  await expect(page).toHaveURL(/\/farms\?datePreset=last-30-days$/);
  await expect(page.getByRole("heading", { name: "Hiệu quả nông trại" })).toBeVisible();
  await expect(page.getByText(/30 ngày gần nhất \(/).first()).toBeVisible();

  const firstFarm = page.locator("tbody a").first();
  await expect(firstFarm).toBeVisible();
  await expect(firstFarm).toHaveAttribute(
    "href",
    /^\/farms\/[0-9a-f-]{36}\?datePreset=last-30-days$/
  );
  await firstFarm.click();
  await expect(page).toHaveURL(
    /\/farms\/[0-9a-f-]{36}\?datePreset=last-30-days$/
  );
  await expect(page.getByText(/30 ngày gần nhất \(/).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Trạng thái hiện hành" })).toBeVisible();
  await expect(page.getByText("Mã nông trại")).toBeVisible();
});
