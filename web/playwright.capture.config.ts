import { defineConfig } from "@playwright/test";

/**
 * Media capture runs against the same guarded demo stack as the E2E gate but
 * lives in its own directory and config so it can never add a scenario to the
 * acceptance count. Invoke it explicitly:
 *   npx playwright test --config playwright.capture.config.ts
 */
export default defineConfig({
  testDir: "./tests/capture",
  timeout: 180_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: "line",
  outputDir: "../artifacts/media-capture/_playwright",
  use: {
    baseURL: "http://localhost:3100",
    browserName: "chromium",
    channel: "chrome",
    headless: true,
    deviceScaleFactor: 2,
    trace: "off"
  },
  webServer: {
    command: "npm run start -- --hostname 127.0.0.1",
    url: "http://localhost:3100",
    reuseExistingServer: true,
    timeout: 120_000
  }
});
