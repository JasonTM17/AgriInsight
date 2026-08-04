import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

import { expect, test, type Locator, type Page } from "@playwright/test";

import { loginWithRealOidc } from "../e2e/helpers/real-oidc-login";
import {
  collectHorizontalOverflowSnapshot,
  hasContainingHorizontalBoundary
} from "./horizontal-overflow-boundary";

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

type LayoutBox = NonNullable<Awaited<ReturnType<Locator["boundingBox"]>>>;

function boxesIntersect(first: LayoutBox, second: LayoutBox): boolean {
  return (
    first.x < second.x + second.width - 1 &&
    first.x + first.width > second.x + 1 &&
    first.y < second.y + second.height - 1 &&
    first.y + first.height > second.y + 1
  );
}

async function requiredBox(locator: Locator, label: string): Promise<LayoutBox> {
  const box = await locator.boundingBox();
  expect(box, `${label} must have a rendered bounding box`).not.toBeNull();
  return box as LayoutBox;
}

async function expectOverviewKpisSeparated(page: Page): Promise<void> {
  const summary = page.getByRole("region", { name: "Chỉ số điều hành" });
  const leadValue = summary.locator("strong").first();
  const leadBox = await requiredBox(leadValue, "Overview revenue");
  const metricValues = summary.locator("dd");
  for (let index = 0; index < await metricValues.count(); index += 1) {
    const metricBox = await requiredBox(
      metricValues.nth(index),
      `Overview secondary KPI ${index + 1}`
    );
    expect(
      boxesIntersect(leadBox, metricBox),
      `Overview revenue overlaps secondary KPI ${index + 1}`
    ).toBe(false);
  }
}

async function expectCostKpisReadable(page: Page): Promise<void> {
  const cards = page.locator('section[aria-label="KPI chi phí mua hàng"] > div');
  await expect(cards).toHaveCount(4);
  for (let index = 0; index < await cards.count(); index += 1) {
    const layout = await cards.nth(index).locator("strong").evaluate((element) => {
      const valueRect = element.getBoundingClientRect();
      const cardRect = element.parentElement?.getBoundingClientRect();
      const range = document.createRange();
      range.selectNodeContents(element);
      return {
        cardRight: cardRect?.right ?? 0,
        lineCount: [...range.getClientRects()].filter((rect) => rect.width > 0).length,
        valueRight: valueRect.right
      };
    });
    expect(layout.lineCount, `Cost KPI ${index + 1} wraps across lines`).toBe(1);
    expect(layout.valueRight, `Cost KPI ${index + 1} escapes its card`).toBeLessThanOrEqual(
      layout.cardRight + 1
    );
  }
}

async function expectMobileContractRows(page: Page): Promise<void> {
  const banner = page.getByRole("region", { name: "Contract bằng chứng" });
  const columnCount = await banner.evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").filter(Boolean).length
  );
  expect(columnCount, "Evidence contract must use one readable mobile column").toBe(1);
}

async function expectMobileAdminTabsVisible(page: Page): Promise<void> {
  const tabs = page.getByRole("navigation", { name: "Khu vực quản trị" });
  const layout = await tabs.evaluate((element) => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth
  }));
  expect(layout.scrollWidth, "Admin tabs require hidden horizontal discovery").toBeLessThanOrEqual(
    layout.clientWidth + 1
  );
  const tabsBox = await requiredBox(tabs, "Admin tab list");
  const links = tabs.getByRole("link");
  for (let index = 0; index < await links.count(); index += 1) {
    const linkBox = await requiredBox(links.nth(index), `Admin tab ${index + 1}`);
    expect(linkBox.x).toBeGreaterThanOrEqual(tabsBox.x - 1);
    expect(linkBox.x + linkBox.width).toBeLessThanOrEqual(tabsBox.x + tabsBox.width + 1);
  }
}

async function expectPortfolioLayout(
  page: Page,
  surface: Surface,
  viewportName: "desktop" | "mobile"
): Promise<void> {
  if (surface.name === "overview-dashboard") await expectOverviewKpisSeparated(page);
  if (surface.name === "cost-analysis") await expectCostKpisReadable(page);
  if (
    viewportName === "mobile" &&
    ["crop-health", "data-quality"].includes(surface.name)
  ) {
    await expectMobileContractRows(page);
  }
  if (viewportName === "mobile" && surface.name === "tenant-administration") {
    await expectMobileAdminTabsVisible(page);
  }
}

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
  const assistantQueryRequests: string[] = [];
  if (surface.name === "assistant-evidence-first") {
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (request.method() === "POST" && url.pathname === "/api/assistant/query") {
        assistantQueryRequests.push(request.url());
      }
    });
  }
  for (const [viewportName, viewport] of [
    ["desktop", desktop],
    ["mobile", mobile]
  ] as const) {
    await page.setViewportSize(viewport);
    const response = await page.goto(surface.route);
    expect(response?.status(), `${viewportName} GET ${surface.route}`).toBe(200);
    await expectSurfaceReady(page, surface);
    await expectPortfolioLayout(page, surface, viewportName);
    if (surface.name === "assistant-evidence-first") {
      const workspace = page.getByTestId("assistant-workspace");
      await expect(workspace.getByRole("heading", {
        name: "Bằng chứng trước, kết luận sau.",
        exact: true
      })).toBeVisible();
      expect(
        assistantQueryRequests,
        `${viewportName} Assistant capture must not query the provider path`
      ).toEqual([]);
    }
    console.log(`PORTFOLIO_CAPTURE surface=${surface.name} viewport=${viewportName}`);
    await expect
      .poll(async () => {
        const snapshot = await page.evaluate(collectHorizontalOverflowSnapshot);
        if (
          snapshot.scrollWidth > snapshot.clientWidth + 1 ||
          snapshot.bodyScrollWidth > snapshot.bodyClientWidth + 1
        ) {
          return JSON.stringify({
            bodyClientWidth: snapshot.bodyClientWidth,
            bodyScrollWidth: snapshot.bodyScrollWidth,
            clientWidth: snapshot.clientWidth,
            offenders: ["page-root"],
            scrollWidth: snapshot.scrollWidth
          });
        }
        const offenders = snapshot.candidates
          .filter((candidate) => !hasContainingHorizontalBoundary(candidate))
          .map((candidate) => candidate.offender)
          .slice(0, 8);
        if (offenders.length === 0) return "fits";
        return JSON.stringify({
          clientWidth: snapshot.clientWidth,
          offenders,
          scrollWidth: snapshot.scrollWidth
        });
      }, {
        message: `${surface.name} ${viewportName} has horizontal overflow`
      })
      .toBe("fits");
    await page.screenshot({
      path: resolve(screenDirectory, `${surface.name}-${viewportName}.png`),
      fullPage: false
    });
    if (surface.name === "assistant-evidence-first") {
      expect(
        assistantQueryRequests,
        `${viewportName} Assistant screenshot must remain provider-query-free`
      ).toEqual([]);
    }
  }
}

const executiveSurfaces = [
  {
    heading: "Điểm cần xem xét",
    name: "overview-dashboard",
    route: "/overview",
    text: "Phiên dữ liệu"
  },
  {
    heading: "Phân tích chi phí",
    name: "cost-analysis",
    route: "/costs?lens=procurement",
    testId: "cost-analysis-page",
    text: "FASTAPI GOLD SNAPSHOT"
  },
  {
    heading: "Hỏi dữ liệu. Nhận câu trả lời có nguồn.",
    name: "assistant-evidence-first",
    route: "/assistant",
    testId: "assistant-workspace",
    text: "Bằng chứng trước, kết luận sau."
  }
] satisfies readonly Surface[];

for (const surface of executiveSurfaces) {
  test(`@capture portfolio executive ${surface.name}`, async ({ page }) => {
    await loginWithRealOidc(page, "executive", surface.route);
    await capturePair(page, surface);
  });
}

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
