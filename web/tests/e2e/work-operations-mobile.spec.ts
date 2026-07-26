import { expect, test } from "@playwright/test";

import {
  expectNoCspViolations,
  expectNonceCspHeader,
  installCspViolationRecorder
} from "./helpers/csp-assertions";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Work E2E requires ${name}`);
  return value;
}

async function login(
  page: import("@playwright/test").Page,
  usernameVariable: string,
  passwordVariable: string
) {
  await page.goto("/api/auth/login?returnTo=/work");
  await page.locator("#username").fill(required(usernameVariable));
  await page.locator("#password").fill(required(passwordVariable));
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL("http://localhost:3100/work");
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

test("@work field worker safely retries append and adds a correction", async ({
  page
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await installCspViolationRecorder(page);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_WORK_USERNAME",
    "AGRIINSIGHT_WEB_E2E_WORK_PASSWORD"
  );
  await expect(page.getByTestId("work-activity-queue")).toBeVisible();
  await expect(page.getByTestId("work-activity-card")).toHaveCount(3);
  await expectNoHorizontalOverflow(page);

  await page.getByTestId("work-activity-card").first().click();
  await expect(page).toHaveURL(/\/work\?activityId=[0-9a-f-]{36}$/);
  await expect(page.getByTestId("work-activity-detail")).toBeVisible();
  await expect(page.getByTestId("work-activity-queue")).toBeHidden();
  await expectNoHorizontalOverflow(page);

  const requestKeys: string[] = [];
  let dropFirstConfirmation = true;
  await page.route(
    /\/api\/work\/activities\/[0-9a-f-]+\/logs$/,
    async (route) => {
      requestKeys.push(
        (await route.request().allHeaders())["idempotency-key"] ?? ""
      );
      if (dropFirstConfirmation) {
        dropFirstConfirmation = false;
        const response = await route.fetch();
        expect(response.status()).toBe(201);
        await route.abort("failed");
        return;
      }
      await route.continue();
    }
  );

  const appendNote = "E2E ghi nhận tưới chống trùng";
  await page.getByLabel("Ghi chú hiện trường").first().fill(appendNote);
  await page.getByTestId("submit-work-log").click();
  await expect(page.getByTestId("work-mutation-feedback")).toContainText(
    "cùng khóa chống trùng"
  );
  await page.getByTestId("submit-work-log").click();
  await expect(page.getByText(appendNote, { exact: true })).toHaveCount(1);
  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).toBeTruthy();
  expect(requestKeys[1]).toBe(requestKeys[0]);

  await page.getByRole("link", {
    name: "Xem lịch sử và hiệu chỉnh"
  }).first().click();
  await expect(page).toHaveURL(
    /\/work\?activityId=[0-9a-f-]{36}&logId=[0-9a-f-]{36}$/
  );
  await expect(page.getByTestId("work-lineage-entry")).toHaveCount(1);

  const correctionNote = "E2E đã đối chiếu và thay thế ghi chú";
  await page.getByLabel("Lý do hiệu chỉnh").fill(
    "Ghi chú ban đầu thiếu kết quả đối chiếu"
  );
  await page.getByLabel("Ghi chú hiện trường").last().fill(correctionNote);
  await page.getByTestId("submit-work-correction").click();
  await expect
    .poll(() =>
      page
        .getByTestId("work-lineage-entry")
        .filter({ hasText: correctionNote })
        .count()
    )
    .toBe(1);
  await expect(page.getByText("Bản thay thế").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  const workResponse = await page.request.get(page.url());
  expectNonceCspHeader(workResponse);
  await expectNoCspViolations(page);
});

test("@work target navigation resets draft and retry identity", async ({
  page
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_WORK_USERNAME",
    "AGRIINSIGHT_WEB_E2E_WORK_PASSWORD"
  );
  await expect(page.getByTestId("work-activity-card")).toHaveCount(3);

  const requestKeys: string[] = [];
  const requestTargets: string[] = [];
  await page.route(
    /\/api\/work\/activities\/[0-9a-f-]+\/logs$/,
    async (route) => {
      const request = route.request();
      requestKeys.push((await request.allHeaders())["idempotency-key"] ?? "");
      requestTargets.push(new URL(request.url()).pathname);
      if (requestKeys.length === 1) {
        await route.abort("failed");
        return;
      }
      await route.continue();
    }
  );

  await page.getByTestId("work-activity-card").first().click();
  const repeatedDraft = "E2E bản nháp phải đổi khóa theo công việc";
  await page.getByLabel("Ghi chú hiện trường").first().fill(repeatedDraft);
  await page.getByTestId("submit-work-log").click();
  await expect(page.getByTestId("work-mutation-feedback")).toContainText(
    "cùng khóa chống trùng"
  );

  await page.getByRole("link", { name: "Hàng đợi công việc" }).click();
  await page.getByTestId("work-activity-card").nth(1).click();
  await expect(page.getByLabel("Ghi chú hiện trường").first()).toHaveValue("");
  await page.getByLabel("Ghi chú hiện trường").first().fill(repeatedDraft);
  await page.getByTestId("submit-work-log").click();
  await expect(page.getByText(repeatedDraft, { exact: true })).toHaveCount(1);

  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).toBeTruthy();
  expect(requestKeys[1]).toBeTruthy();
  expect(requestKeys[1]).not.toBe(requestKeys[0]);
  expect(requestTargets[1]).not.toBe(requestTargets[0]);
  await expectNoHorizontalOverflow(page);
});

test("@work supplier receives a generic denied scope", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await installCspViolationRecorder(page);
  await login(
    page,
    "AGRIINSIGHT_WEB_E2E_DENIED_USERNAME",
    "AGRIINSIGHT_WEB_E2E_DENIED_PASSWORD"
  );

  await expect(page.getByText("Phiên hiện tại không có quyền đọc công việc."))
    .toBeVisible();
  await expect(page.getByTestId("work-activity-queue")).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText(/employeeId|Bearer\s/i);
  await expectNoHorizontalOverflow(page);
  await expectNoCspViolations(page);
});
