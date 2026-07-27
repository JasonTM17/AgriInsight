import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { expect, test } from "@playwright/test";

import { loginWithRealOidc } from "./helpers/real-oidc-login";

const MANIFEST = resolve(
  import.meta.dirname,
  "../../../artifacts/big-data/manifest.json"
);

const performanceViewports = [
  { name: "mobile", width: 375, height: 812 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "compact-desktop", width: 1024, height: 768 },
  { name: "desktop", width: 1440, height: 900 },
  { name: "mobile-landscape", width: 844, height: 390 }
] as const;

test("@performance verified big-data routes meet browser budgets", async ({
  page
}) => {
  test.setTimeout(180_000);
  const manifest = JSON.parse(await readFile(MANIFEST, "utf8")) as {
    configuration: { scale_profile: string };
    quality_status: string;
    row_counts: { silver: { sensor_readings: number } };
  };
  expect(manifest.configuration.scale_profile).toBe("big-data");
  expect(manifest.quality_status).toBe("passed");
  expect(manifest.row_counts.silver.sensor_readings).toBe(1_050_000);

  await page.addInitScript(() => {
    const metrics = {
      cls: 0,
      eventTimingSupported:
        PerformanceObserver.supportedEntryTypes.includes("event"),
      inp: 0,
      lcp: 0
    };
    Object.defineProperty(window, "__agriInsightVitals", { value: metrics });
    if (PerformanceObserver.supportedEntryTypes.includes("largest-contentful-paint")) {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) metrics.lcp = entry.startTime;
      }).observe({ type: "largest-contentful-paint", buffered: true });
    }
    if (PerformanceObserver.supportedEntryTypes.includes("layout-shift")) {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          const shift = entry as PerformanceEntry & {
            hadRecentInput: boolean;
            value: number;
          };
          if (!shift.hadRecentInput) metrics.cls += shift.value;
        }
      }).observe({ type: "layout-shift", buffered: true });
    }
    if (PerformanceObserver.supportedEntryTypes.includes("event")) {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          metrics.inp = Math.max(metrics.inp, entry.duration);
        }
      }).observe(
        { type: "event", buffered: true, durationThreshold: 16 } as PerformanceObserverInit
      );
    }
  });

  await loginWithRealOidc(page, "executive", "/overview");
  for (const route of ["/overview", "/crop-health"]) {
    const startedAt = Date.now();
    const response = await page.goto(route);
    expect(response?.status(), route).toBe(200);
    await expect(page.locator("main")).toBeVisible();
    expect(Date.now() - startedAt, `${route} render milliseconds`).toBeLessThanOrEqual(
      8_000
    );
  }

  for (const viewport of performanceViewports) {
    await page.setViewportSize(viewport);
    await page.goto("/overview");
    await expect(page.locator("main")).toBeVisible();
    await page.getByRole("button", { name: "Mở thông báo" }).click();
    await page.waitForTimeout(1_000);
    const vitals = await page.evaluate(
      () =>
        (
          window as typeof window & {
            __agriInsightVitals: {
              cls: number;
              eventTimingSupported: boolean;
              inp: number;
              lcp: number;
            };
          }
        ).__agriInsightVitals
    );
    expect(vitals.lcp, `${viewport.name} LCP`).toBeGreaterThan(0);
    expect(vitals.lcp, `${viewport.name} LCP`).toBeLessThanOrEqual(2_500);
    expect(vitals.eventTimingSupported, `${viewport.name} Event Timing`).toBe(true);
    expect(vitals.inp, `${viewport.name} INP`).toBeLessThanOrEqual(200);
    expect(vitals.cls, `${viewport.name} CLS`).toBeLessThanOrEqual(0.1);
  }
});

test("@performance data-quality route meets the big-data render budget", async ({
  page
}) => {
  await loginWithRealOidc(page, "analyst", "/data-quality");
  const startedAt = Date.now();
  const response = await page.goto("/data-quality");
  expect(response?.status()).toBe(200);
  await expect(page.locator("main")).toBeVisible();
  expect(Date.now() - startedAt).toBeLessThanOrEqual(8_000);
});
