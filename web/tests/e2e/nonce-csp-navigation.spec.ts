import { expect, test } from "@playwright/test";

import {
  expectNoCspViolations,
  expectNonceCspHeader,
  installCspViolationRecorder
} from "./helpers/csp-assertions";

test("@security nonce CSP hydrates landing and not-found routes", async ({
  page
}) => {
  await installCspViolationRecorder(page);
  const landingResponse = await page.goto("/", { waitUntil: "networkidle" });
  expect(landingResponse).not.toBeNull();
  expectNonceCspHeader(landingResponse!);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expectNoCspViolations(page);

  await page.evaluate(() => {
    Object.defineProperty(window, "__agriInsightHydrationMarker", {
      configurable: true,
      value: "preserved"
    });
  });
  await page.getByRole("link", { name: /Đăng nhập vào hệ thống/i }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(
    await page.evaluate(
      () =>
        (window as Window & {
          __agriInsightHydrationMarker?: string;
        }).__agriInsightHydrationMarker
    )
  ).toBe("preserved");
  await expectNoCspViolations(page);

  const response = await page.goto("/route-khong-ton-tai", {
    waitUntil: "networkidle"
  });
  expect(response?.status()).toBe(404);
  expect(response).not.toBeNull();
  expectNonceCspHeader(response!);
  await expectNoCspViolations(page);
});
