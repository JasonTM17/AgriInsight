import { createHash, randomUUID } from "node:crypto";

import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Locator, type Page } from "@playwright/test";

import type {
  RealtimeOperationalAlert,
  RealtimeOperationalAlertFeed
} from "../../src/features/realtime-alerts/realtime-alert-contract";
import {
  createRealtimeAlertDatabasePool,
  deleteRealtimeAlertFixture,
  insertExecutiveOpenRealtimeAlert,
  type RealtimeAlertFixture
} from "./helpers/realtime-alert-database";
import {
  expectNoCspViolations,
  installCspViolationRecorder
} from "./helpers/csp-assertions";
import { loginWithRealOidc } from "./helpers/real-oidc-login";

const ALERT_TRIGGER_SELECTOR =
  'button[aria-controls="realtime-operational-alert-panel"]';
const ALERT_DIALOG_NAME = /Cảnh báo vận hành realtime/i;
const SECRET_PATTERN =
  /access_token|refresh_token|Bearer\s|eyJ[a-zA-Z0-9_-]+\.|client_secret/i;

test("@realtime executive reviews and acknowledges the seeded operational alert", async ({
  page
}) => {
  test.setTimeout(120_000);
  const pool = createRealtimeAlertDatabasePool();
  const alertId = randomUUID();
  const fixtureInput = {
    alertId,
    dedupeKey: createHash("sha256").update(alertId).digest("hex")
  };
  let fixture: RealtimeAlertFixture | undefined;
  let acknowledgementConfirmed = false;

  try {
    fixture = await insertExecutiveOpenRealtimeAlert(pool, fixtureInput);
    await page.setViewportSize({ width: 1440, height: 900 });
    await installCspViolationRecorder(page);
    await loginWithRealOidc(page, "executive", "/overview");
    await page.clock.install();
    let feedRequestCount = 0;
    page.on("request", (request) => {
      if (
        request.method() === "GET"
        && new URL(request.url()).pathname === "/api/realtime/alerts"
      ) {
        feedRequestCount += 1;
      }
    });

    const trigger = page.locator(ALERT_TRIGGER_SELECTOR);
    const dialog = page.getByRole("dialog", { name: ALERT_DIALOG_NAME });
    await expect(trigger).toBeVisible();
    await expect(trigger).toHaveAccessibleName("Mở thông báo");
    await expect(trigger).toHaveAttribute("aria-expanded", "false");

    const firstFeedResponse = waitForAlertResponse(
      page,
      "GET",
      "/api/realtime/alerts"
    );
    await trigger.focus();
    await trigger.press("Enter");
    await page.clock.runFor(1);
    const feed = await readSeededFeed(await firstFeedResponse, fixture.alertId);
    await expect(trigger).toHaveAttribute("aria-expanded", "true");
    await expect(trigger).toHaveAccessibleName("Đóng thông báo");
    await expect(dialog).toBeVisible();
    const seededRow = alertRowFor(dialog, feed, fixture.alertId);
    await expectSeededAlertLabels(seededRow);

    const pollResponse = waitForAlertResponse(
      page,
      "GET",
      "/api/realtime/alerts"
    );
    await page.clock.runFor(30_000);
    await readSeededFeed(await pollResponse, fixture.alertId);

    const backgroundPage = await page.context().newPage();
    await backgroundPage.bringToFront();
    await setDocumentVisibilityState(page, "hidden");
    await expect.poll(() =>
      page.evaluate(() => document.visibilityState)
    ).toBe("hidden");
    const requestCountWhileHidden = feedRequestCount;
    await page.clock.runFor(30_000);
    expect(feedRequestCount).toBe(requestCountWhileHidden);
    const visibilityRefresh = waitForAlertResponse(
      page,
      "GET",
      "/api/realtime/alerts"
    );
    await page.bringToFront();
    await setDocumentVisibilityState(page, "visible");
    await readSeededFeed(await visibilityRefresh, fixture.alertId);
    await backgroundPage.close();

    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
    await expect(trigger).toHaveAccessibleName("Mở thông báo");
    await expect(trigger).toBeFocused();
    const requestCountAfterClose = feedRequestCount;
    await page.clock.runFor(60_000);
    expect(feedRequestCount).toBe(requestCountAfterClose);

    const outsideCloseFeed = waitForAlertResponse(
      page,
      "GET",
      "/api/realtime/alerts"
    );
    await trigger.press("Enter");
    await page.clock.runFor(1);
    await readSeededFeed(await outsideCloseFeed, fixture.alertId);
    await page.locator(".app-header__title h1").click();
    await page.clock.runFor(17);
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();

    await page.setViewportSize({ width: 375, height: 812 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    const mobileFeedResponse = waitForAlertResponse(
      page,
      "GET",
      "/api/realtime/alerts"
    );
    await trigger.press("Space");
    await page.clock.runFor(1);
    await readSeededFeed(await mobileFeedResponse, fixture.alertId);
    await expect(dialog).toBeVisible();
    await expectPanelFitsViewport(page, dialog);
    await expectPanelControlsAreTouchSized(dialog);
    await expectPanelAccessible(page);

    const acknowledgementResponse = waitForAlertResponse(
      page,
      "POST",
      `/api/realtime/alerts/${fixture.alertId}/acknowledgements`
    );
    await seededRow
      .getByRole("button", {
        name: "Xác nhận đã xem cảnh báo vận hành này"
      })
      .click();
    const acknowledgement = await acknowledgementResponse;
    expect(acknowledgement.status()).toBe(200);
    expect(new URL(acknowledgement.url()).origin).toBe(
      "http://localhost:3100"
    );
    expect(acknowledgement.request().postData()).toBe("{}");
    const requestHeaders = await acknowledgement.request().allHeaders();
    expect(requestHeaders["idempotency-key"]).toBeTruthy();
    expect(requestHeaders["x-agriinsight-csrf"]).toBeTruthy();
    expect(requestHeaders.authorization).toBeUndefined();

    const acknowledged =
      (await acknowledgement.json()) as RealtimeOperationalAlert;
    expect(acknowledged).toMatchObject({
      acknowledged: true,
      evidence: { id: null, type: "TENANT_BACKLOG" },
      id: fixture.alertId,
      policy: "OUTBOX_PUBLISH_BACKLOG",
      severity: "CRITICAL",
      source: "realtime_operational",
      state: "OPEN"
    });
    expect(acknowledged.acknowledgedAt).toEqual(expect.any(String));
    acknowledgementConfirmed = true;
    await expect(
      seededRow.getByText("Đã xác nhận trên máy chủ", { exact: true })
    ).toBeVisible();
    await expect(
      seededRow.getByRole("button", {
        name: "Cảnh báo đã được xác nhận trên máy chủ"
      })
    ).toBeDisabled();
    for (const viewport of [
      { width: 768, height: 1024 },
      { width: 1024, height: 768 },
      { width: 1440, height: 900 },
      { width: 812, height: 375 }
    ]) {
      await page.setViewportSize(viewport);
      await expectPanelFitsViewport(page, dialog);
      await expectPanelControlsAreTouchSized(dialog);
    }
    expect(await page.content()).not.toMatch(SECRET_PATTERN);
    await expectNoCspViolations(page);

    const requestCountBeforeRouteChange = feedRequestCount;
    await page.setViewportSize({ width: 1440, height: 900 });
    await page
      .getByRole("navigation", { name: "Điều hướng chính" })
      .getByRole("link", { name: "Sức khỏe cây trồng", exact: true })
      .click();
    await expect(page).toHaveURL(/\/crop-health$/);
    await expect(dialog).toHaveCount(0);
    await page.clock.runFor(30_000);
    expect(feedRequestCount).toBe(requestCountBeforeRouteChange);
  } finally {
    try {
      if (fixture) {
        await deleteRealtimeAlertFixture(pool, fixture, {
          expectAcknowledgement: acknowledgementConfirmed
        });
      }
    } finally {
      await pool.end();
    }
  }
});

test("@realtime farm manager has no alert-panel trigger", async ({ page }) => {
  const realtimeRequests: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.startsWith("/api/realtime/alerts")) {
      realtimeRequests.push(request.url());
    }
  });

  await loginWithRealOidc(page, "farm-manager", "/overview");
  await expect(page.locator("main")).toBeVisible();
  await expect(
    page
      .getByRole("banner")
      .getByRole("button", { name: /cảnh báo vận hành|thông báo/i })
  ).toHaveCount(0);
  await expect(
    page.getByRole("dialog", { name: ALERT_DIALOG_NAME })
  ).toHaveCount(0);
  expect(realtimeRequests).toEqual([]);
});

function waitForAlertResponse(page: Page, method: string, pathname: string) {
  return page.waitForResponse((response) => {
    const request = response.request();
    return request.method() === method
      && new URL(response.url()).pathname === pathname;
  });
}

async function setDocumentVisibilityState(
  page: Page,
  state: "hidden" | "visible"
) {
  await page.evaluate((nextState) => {
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => nextState
    });
    document.dispatchEvent(new Event("visibilitychange"));
  }, state);
}

async function readSeededFeed(
  response: Awaited<ReturnType<typeof waitForAlertResponse>>,
  alertId: string
): Promise<RealtimeOperationalAlertFeed> {
  expect(response.status()).toBe(200);
  expect(new URL(response.url()).origin).toBe("http://localhost:3100");
  const feed = (await response.json()) as RealtimeOperationalAlertFeed;
  expect(feed.items).toContainEqual(
    expect.objectContaining({
      acknowledged: false,
      evidence: { id: null, type: "TENANT_BACKLOG" },
      id: alertId,
      policy: "OUTBOX_PUBLISH_BACKLOG",
      severity: "CRITICAL",
      source: "realtime_operational",
      state: "OPEN"
    })
  );
  return feed;
}

function alertRowFor(
  dialog: Locator,
  feed: RealtimeOperationalAlertFeed,
  alertId: string
): Locator {
  const alert = feed.items.find((item) => item.id === alertId);
  if (!alert) throw new Error("Seeded realtime alert missing from feed");
  return dialog
    .locator(`time[datetime="${alert.lastObservedAt}"]`)
    .locator("xpath=ancestor::article");
}

async function expectSeededAlertLabels(row: Locator): Promise<void> {
  await expect(row).toBeVisible();
  for (const label of [
    "Vận hành realtime",
    "Mức độ: Nghiêm trọng",
    "Trạng thái: OPEN",
    "Tồn đọng xuất bản",
    "Tồn đọng cấp doanh nghiệp",
    "Chưa xác nhận"
  ]) {
    await expect(row.getByText(label, { exact: true })).toBeVisible();
  }
}

async function expectPanelFitsViewport(
  page: Page,
  dialog: Locator
): Promise<void> {
  await expect.poll(async () => dialog.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return rect.left >= -1
      && rect.top >= -1
      && rect.right <= window.innerWidth + 1
      && rect.bottom <= window.innerHeight + 1
      && document.documentElement.scrollWidth
        <= document.documentElement.clientWidth + 1;
  })).toBe(true);
}

async function expectPanelControlsAreTouchSized(dialog: Locator): Promise<void> {
  const sizes = await dialog.getByRole("button").evaluateAll((buttons) =>
    buttons.map((button) => {
      const rect = button.getBoundingClientRect();
      return { height: rect.height, width: rect.width };
    })
  );
  expect(sizes.length).toBeGreaterThan(0);
  expect(sizes.every(({ height, width }) => height >= 44 && width >= 44))
    .toBe(true);
}

async function expectPanelAccessible(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const blockers = results.violations.filter((violation) =>
    ["critical", "serious"].includes(violation.impact ?? "")
  );
  expect(blockers.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    targets: violation.nodes.flatMap((node) => node.target)
  }))).toEqual([]);
}
