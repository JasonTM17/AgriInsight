import { expect, test } from "@playwright/test";

import { loginWithRealOidc } from "./helpers/real-oidc-login";

const routes = [
  "/overview",
  "/farms",
  "/work",
  "/inventory",
  "/costs?lens=operating",
  "/crop-health",
  "/data-quality",
  "/admin"
] as const;

type Route = (typeof routes)[number];

const personas: ReadonlyArray<{
  username: string;
  allowed: ReadonlySet<Route>;
}> = [
  { username: "tenant-admin", allowed: new Set(routes) },
  {
    username: "executive",
    allowed: new Set([
      "/overview",
      "/farms",
      "/work",
      "/inventory",
      "/costs?lens=operating",
      "/crop-health"
    ])
  },
  {
    username: "farm-manager",
    allowed: new Set([
      "/overview",
      "/farms",
      "/work",
      "/inventory",
      "/costs?lens=operating",
      "/crop-health"
    ])
  },
  { username: "inventory-manager", allowed: new Set(["/inventory"]) },
  {
    username: "analyst",
    allowed: new Set([
      "/overview",
      "/farms",
      "/work",
      "/inventory",
      "/costs?lens=operating",
      "/crop-health",
      "/data-quality"
    ])
  },
  { username: "field-worker", allowed: new Set(["/work"]) },
  { username: "supplier", allowed: new Set() }
];

for (const persona of personas) {
  test(`@authorization ${persona.username} receives the exact route matrix`, async ({
    page
  }) => {
    await loginWithRealOidc(page, persona.username, "/overview");

    for (const route of routes) {
      const response = await page.request.get(route);
      const expectedStatus = persona.allowed.has(route) ? 200 : 403;
      expect(response.status(), `${persona.username} GET ${route}`).toBe(
        expectedStatus
      );

      const body = await response.text();
      expect(body).not.toMatch(
        /access_token|refresh_token|Bearer\s|eyJ[a-zA-Z0-9_-]+\./i
      );
      if (expectedStatus === 403) {
        expect(body).toContain("Truy cập");
        expect(body).not.toMatch(/subject|employeeId|warehouseIds/i);
      }
    }
  });
}
