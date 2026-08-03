import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

import { expect, test, type Page } from "@playwright/test";

import { loginWithRealOidc } from "../e2e/helpers/real-oidc-login";

const screenDirectory = resolve(
  import.meta.dirname,
  "../../../artifacts/media-capture/screens"
);
const desktop = { width: 1440, height: 900 } as const;
const mobile = { width: 390, height: 844 } as const;

type Surface = Readonly<{
  heading: string;
  name: string;
  route: string;
  testId?: string;
  text?: string;
}>;

async function expectSurfaceReady(page: Page, surface: Surface): Promise<void> {
  if (surface.testId) {
    await expect(page.getByTestId(surface.testId)).toBeVisible();
  }
  await expect(page.getByRole("heading", { name: surface.heading }).first()).toBeVisible();
  if (surface.text) await expect(page.getByText(surface.text).first()).toBeVisible();
  await expect(page.locator("body")).not.toContainText(
    /Truy cập bị từ chối|Dữ liệu chưa sẵn sàng|Không thể tải/i
  );
  await page.waitForLoadState("networkidle");
}

async function capturePair(page: Page, surface: Surface): Promise<void> {
  await mkdir(screenDirectory, { recursive: true });
  for (const [viewportName, viewport] of [
    ["desktop", desktop],
    ["mobile", mobile]
  ] as const) {
    await page.setViewportSize(viewport);
    const response = await page.goto(surface.route);
    expect(response?.status(), `${viewportName} GET ${surface.route}`).toBe(200);
    await expectSurfaceReady(page, surface);
    await expect
      .poll(() => page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1
      ))
      .toBe(true);
    await page.screenshot({
      path: resolve(screenDirectory, `${surface.name}-${viewportName}.png`),
      fullPage: true
    });
  }
}

test("@capture portfolio executive surfaces", async ({ page }) => {
  await loginWithRealOidc(page, "executive", "/overview");
  for (const surface of [
    {
      heading: "Điểm cần xem xét",
      name: "overview-dashboard",
      route: "/overview",
      text: "Phiên dữ liệu"
    },
    {
      heading: "Phân tích chi phí",
      name: "cost-analysis",
      route: "/costs?lens=operating",
      testId: "cost-analysis-page"
    },
    {
      heading: "Hỏi dữ liệu. Nhận câu trả lời có nguồn.",
      name: "assistant-evidence-first",
      route: "/assistant",
      testId: "assistant-workspace",
      text: "Bằng chứng trước, kết luận sau."
    }
  ] satisfies readonly Surface[]) {
    await capturePair(page, surface);
  }
});

test("@capture portfolio work operations", async ({ page }) => {
  await loginWithRealOidc(page, "field-worker", "/work");
  await capturePair(page, {
    heading: "Công việc trong phạm vi",
    name: "work-operations",
    route: "/work",
    testId: "work-activity-queue"
  });
  await expect(page.getByTestId("work-activity-card").first()).toBeVisible();
});

test("@capture portfolio analyst surfaces", async ({ page }) => {
  await loginWithRealOidc(page, "analyst", "/crop-health");
  for (const surface of [
    {
      heading: "Sức khỏe cây trồng",
      name: "crop-health",
      route: "/crop-health",
      testId: "crop-health-page",
      text: "assessmentMethod=rule-based-heuristic"
    },
    {
      heading: "Chất lượng dữ liệu",
      name: "data-quality",
      route: "/data-quality",
      testId: "data-quality-page",
      text: "Dòng cách ly"
    }
  ] satisfies readonly Surface[]) {
    await capturePair(page, surface);
  }
});

test("@capture portfolio tenant administration", async ({ page }) => {
  await loginWithRealOidc(page, "tenant-admin", "/admin");
  await capturePair(page, {
    heading: "Quản trị tenant",
    name: "tenant-administration",
    route: "/admin?search=tenant-admin&status=active",
    testId: "admin-directory-page",
    text: "Hồ sơ người dùng"
  });
});
