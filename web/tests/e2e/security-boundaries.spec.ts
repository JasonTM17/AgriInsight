import { expect, test } from "@playwright/test";

import { loginWithRealOidc } from "./helpers/real-oidc-login";

const secretPattern =
  /access_token|refresh_token|Bearer\s|eyJ[a-zA-Z0-9_-]+\.|client_secret/i;

test("@security mutation and session boundaries fail closed without leaks", async ({
  context,
  page
}) => {
  await loginWithRealOidc(page, "tenant-admin", "/admin");

  const session = await page.request.get("/api/auth/session");
  expect(session.status()).toBe(200);
  expect(session.headers()["cache-control"]).toContain("no-store");
  expect(await session.text()).not.toMatch(secretPattern);

  const crossOrigin = await page.request.post(
    "/api/administration/mutations",
    {
      data: {},
      headers: { Origin: "https://attacker.invalid" }
    }
  );
  expect(crossOrigin.status()).toBe(403);
  expect(crossOrigin.headers()["cache-control"]).toContain("no-store");
  expect(await crossOrigin.text()).not.toMatch(secretPattern);

  const missingCsrf = await page.request.post(
    "/api/administration/mutations",
    {
      data: {},
      headers: { Origin: "http://localhost:3100" }
    }
  );
  expect(missingCsrf.status()).toBe(403);
  expect(missingCsrf.headers()["cache-control"]).toContain("no-store");
  expect(await missingCsrf.text()).not.toMatch(secretPattern);

  const cookies = await context.cookies();
  expect(
    cookies.find((cookie) => cookie.name === "__Host-agriinsight-session")
  ).toBeDefined();
  expect(
    cookies
      .filter((cookie) => cookie.name === "__Host-agriinsight-session")
      .every((cookie) => cookie.httpOnly && cookie.secure)
  ).toBe(true);
  expect(
    cookies.some((cookie) => /access|refresh|bearer/i.test(cookie.name))
  ).toBe(false);
});
