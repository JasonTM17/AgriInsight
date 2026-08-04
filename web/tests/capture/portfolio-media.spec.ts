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
      .poll(async () => page.evaluate(() => {
        const root = document.documentElement;
        const body = document.body;
        const interactiveSelector = [
          "a",
          "button",
          "input",
          "select",
          "textarea",
          "[tabindex]:not([tabindex='-1'])"
        ].join(",");
        if (
          root.scrollWidth > root.clientWidth + 1 ||
          body.scrollWidth > body.clientWidth + 1
        ) {
          return JSON.stringify({
            bodyClientWidth: body.clientWidth,
            bodyScrollWidth: body.scrollWidth,
            clientWidth: root.clientWidth,
            offenders: ["page-root"],
            scrollWidth: root.scrollWidth
          });
        }
        const hasContainingHorizontalBoundary = (element: HTMLElement): boolean => {
          let ancestor = element.parentElement;
          while (ancestor && ancestor !== document.body) {
            const overflowX = getComputedStyle(ancestor).overflowX;
            const rect = ancestor.getBoundingClientRect();
            const isBounded = rect.left >= -1 && rect.right <= root.clientWidth + 1;
            if (isBounded && ["auto", "scroll"].includes(overflowX)) return true;
            if (isBounded && ["hidden", "clip"].includes(overflowX)) {
              const containsInteractiveContent = Boolean(
                element.closest(interactiveSelector) ||
                element.querySelector(interactiveSelector)
              );
              if (!containsInteractiveContent) return true;
            }
            ancestor = ancestor.parentElement;
          }
          return false;
        };
        const offenders = [...document.querySelectorAll<HTMLElement>("body *")]
          .filter((element) => {
            const rect = element.getBoundingClientRect();
            const exceedsViewport = rect.left < -1 || rect.right > root.clientWidth + 1;
            return exceedsViewport && !hasContainingHorizontalBoundary(element);
          })
          .map((element) => ({
            className: element.className,
            rect: element.getBoundingClientRect().toJSON(),
            tagName: element.tagName
          }))
          .slice(0, 8);
        if (offenders.length === 0) return "fits";
        return JSON.stringify({
          clientWidth: root.clientWidth,
          offenders,
          scrollWidth: root.scrollWidth
        });
      }), {
        message: `${surface.name} ${viewportName} has horizontal overflow`
      })
      .toBe("fits");
    await page.screenshot({
      path: resolve(screenDirectory, `${surface.name}-${viewportName}.png`),
      fullPage: false
    });
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
