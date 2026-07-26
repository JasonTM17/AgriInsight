import { expect, test } from "@playwright/test";

import {
  expectNoCspViolations,
  expectNonceCspHeader,
  installCspViolationRecorder
} from "./helpers/csp-assertions";

const BAD_GATEWAY_TITLE = "Cổng nghiệp vụ chưa xác nhận giao dịch kho.";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Inventory E2E requires ${name}`);
  return value;
}

async function login(page: import("@playwright/test").Page, returnTo = "/inventory") {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(
    required("AGRIINSIGHT_WEB_E2E_INVENTORY_USERNAME")
  );
  await page.locator("#password").fill(
    required("AGRIINSIGHT_WEB_E2E_INVENTORY_PASSWORD")
  );
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/inventory\?warehouseId=[0-9a-f-]{36}$/);
}

async function expectNoHorizontalOverflow(
  page: import("@playwright/test").Page
) {
  await expect
    .poll(() =>
      page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth
      )
    )
    .toBe(true);
}

test("@inventory manager records a receipt, issue and ETag reversal", async ({
  page
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await installCspViolationRecorder(page);
  await login(page);

  await expect(page.getByTestId("inventory-control-page")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Kiểm soát tồn kho" })).toBeVisible();
  await expect(page.getByTestId("inventory-transaction-form")).toBeVisible();
  await expectNoHorizontalOverflow(page);

  const requestKeys: string[] = [];
  let dropFirstConfirmation = true;
  let rejectNextAsBadGateway = false;
  await page.route(
    /\/api\/inventory\/transactions$/,
    async (route) => {
      const request = route.request();
      requestKeys.push((await request.allHeaders())["idempotency-key"] ?? "");
      if (dropFirstConfirmation) {
        dropFirstConfirmation = false;
        const response = await route.fetch();
        expect(response.status()).toBe(201);
        await route.abort("failed");
        return;
      }
      if (rejectNextAsBadGateway) {
        rejectNextAsBadGateway = false;
        await route.fulfill({
          status: 502,
          contentType: "application/problem+json",
          body: JSON.stringify({ title: BAD_GATEWAY_TITLE })
        });
        return;
      }
      await route.continue();
    }
  );

  const batchCode = `E2E-${Date.now()}`;
  await page.getByLabel("Mã lô").fill(batchCode);
  await page.getByLabel("Đơn giá VND").fill("12500");
  await page.getByLabel("Ngày hết hạn").fill("2028-12-31");
  await page.getByLabel("Thời điểm nghiệp vụ").fill("2027-01-01T08:00");
  await page.getByLabel("Số lượng theo đơn vị gốc").fill("25");
  await page.getByTestId("inventory-transaction-submit").click();
  await expect(page.getByTestId("inventory-mutation-feedback")).toContainText(
    "giữ nguyên khóa chống trùng"
  );
  await page.getByTestId("inventory-transaction-submit").click();
  await expect(page).toHaveURL(/\/inventory\?warehouseId=[0-9a-f-]{36}$/);
  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).toBeTruthy();
  expect(requestKeys[1]).toBe(requestKeys[0]);
  await expect(page.getByTestId("inventory-transaction-row")).toHaveCount(1);

  await page.getByRole("button", { name: "Xuất kho" }).click();
  await page.getByLabel("Lý do xuất").fill("E2E cấp vật tư cho khu thử nghiệm");
  await page.getByLabel("Thời điểm nghiệp vụ").fill("2027-01-02T08:00");
  await page.getByLabel("Số lượng theo đơn vị gốc").fill("5");
  rejectNextAsBadGateway = true;
  await page.getByTestId("inventory-transaction-submit").click();
  await expect(page.getByTestId("inventory-mutation-feedback")).toContainText(
    BAD_GATEWAY_TITLE
  );
  await page.getByTestId("inventory-transaction-submit").click();
  await expect(page.getByTestId("inventory-transaction-row")).toHaveCount(2);
  expect(requestKeys).toHaveLength(4);
  expect(requestKeys[3]).toBe(requestKeys[2]);

  await page.getByText("Đảo một phần giao dịch", { exact: true }).click();
  const reversalKeys: string[] = [];
  const reversalEtags: string[] = [];
  let replaceFirstReversalConfirmation = true;
  await page.route(
    /\/api\/inventory\/transactions\/[0-9a-f-]+\/reversals$/,
    async (route) => {
      const headers = await route.request().allHeaders();
      reversalKeys.push(headers["idempotency-key"] ?? "");
      reversalEtags.push(headers["if-match"] ?? "");
      if (replaceFirstReversalConfirmation) {
        replaceFirstReversalConfirmation = false;
        const response = await route.fetch();
        expect(response.status()).toBe(201);
        await route.fulfill({
          status: 502,
          contentType: "application/problem+json",
          body: JSON.stringify({
            code: "upstream_unavailable",
            title: BAD_GATEWAY_TITLE
          })
        });
        return;
      }
      await route.continue();
    }
  );
  await page.getByLabel("Số lượng cần đảo").fill("2");
  await page.getByLabel("Lý do hiệu chỉnh").fill("E2E đối soát phiếu nhập");
  await page.getByTestId("inventory-reversal-submit").click();
  await expect(page.getByTestId("inventory-mutation-feedback")).toContainText(
    BAD_GATEWAY_TITLE
  );
  await page.getByTestId("inventory-reversal-submit").click();
  await expect(page.getByTestId("inventory-transaction-row")).toHaveCount(3);
  expect(reversalKeys).toHaveLength(2);
  expect(reversalKeys[0]).toBeTruthy();
  expect(reversalKeys[1]).toBe(reversalKeys[0]);
  expect(reversalEtags).toHaveLength(2);
  expect(reversalEtags[0]).toMatch(/^"\d+"$/);
  expect(reversalEtags[1]).toBe(reversalEtags[0]);
  await expectNoHorizontalOverflow(page);

  const response = await page.request.get(page.url());
  expectNonceCspHeader(response);
  await expectNoCspViolations(page);
});

test("@inventory supplier receives a generic denied scope", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await installCspViolationRecorder(page);
  await page.goto("/api/auth/login?returnTo=/inventory");
  await page.locator("#username").fill(
    required("AGRIINSIGHT_WEB_E2E_DENIED_USERNAME")
  );
  await page.locator("#password").fill(
    required("AGRIINSIGHT_WEB_E2E_DENIED_PASSWORD")
  );
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL("http://localhost:3100/inventory");
  await expect(page.getByText("Phiên hiện tại không có quyền đọc tồn kho.")).toBeVisible();
  await expect(page.locator("body")).not.toContainText(/access_token|refresh_token|Bearer\s/i);
  await expectNoHorizontalOverflow(page);
  await expectNoCspViolations(page);
});
