import { Buffer } from "node:buffer";

import { expect, test } from "@playwright/test";
import { Pool } from "pg";

import { SESSION_COOKIE_NAME } from "../../src/cookie-policy.ts";
import { TokenCipher } from "../../src/token-crypto.ts";

function decodePayload(token: string): Record<string, unknown> {
  const payload = token.split(".")[1];
  if (!payload) throw new Error("Access token is not a JWT");
  return JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as Record<string, unknown>;
}

test("real Keycloak code+PKCE login stays opaque, refreshes once, and logs out locally", async ({
  context,
  page,
}) => {
  const username = process.env.AUTH_SPIKE_TEST_USERNAME;
  const password = process.env.AUTH_SPIKE_TEST_PASSWORD;
  const databaseUrl = process.env.AUTH_SPIKE_DATABASE_URL;
  const encryptionKey = process.env.AUTH_SPIKE_SESSION_ENCRYPTION_KEY_BASE64;
  const keyId = process.env.AUTH_SPIKE_TOKEN_KEY_ID;
  if (!username || !password || !databaseUrl || !encryptionKey || !keyId) {
    throw new Error("Real OIDC Playwright environment is incomplete");
  }

  let callbackUrl: string | undefined;
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/auth/callback/keycloak") {
      callbackUrl = request.url();
    }
  });
  await page.goto("/api/auth/login?returnTo=/protected");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL("http://localhost:3100/protected");
  await expect(page.getByTestId("session-state")).toContainText("authoritative database lookup");

  const cookies = await context.cookies();
  const sessionCookie = cookies.find((cookie) => cookie.name === SESSION_COOKIE_NAME);
  expect(sessionCookie).toBeDefined();
  expect(sessionCookie?.httpOnly).toBe(true);
  expect(sessionCookie?.secure).toBe(true);
  expect(sessionCookie?.sameSite).toBe("Lax");
  expect(sessionCookie?.path).toBe("/");
  expect(cookies.some((cookie) => /access|refresh|bearer/i.test(cookie.name))).toBe(false);
  const browserStorage = await page.evaluate(async () => ({
    cache: await caches.keys(),
    indexedDb: (await indexedDB.databases()).map((database) => database.name),
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));
  expect(browserStorage).toEqual({ cache: [], indexedDb: [], local: [], session: [] });
  expect(await page.content()).not.toMatch(/eyJ|access_token|refresh_token|Bearer\s/i);
  const sessionPayload = await page.request.get("/api/auth/session");
  expect(sessionPayload.status()).toBe(200);
  expect(await sessionPayload.text()).not.toMatch(/eyJ|access_token|refresh_token|Bearer\s/i);

  const pool = new Pool({ connectionString: databaseUrl });
  try {
    const before = await pool.query<{
      access_token_ciphertext: Buffer;
      refresh_token_ciphertext: Buffer;
      refresh_version: string;
      session_token_hash: Buffer;
      session_version: string;
    }>(
      `SELECT access_token_ciphertext, refresh_token_ciphertext, refresh_version,
              session_token_hash, session_version
       FROM web_auth_spike.sessions WHERE revoked_at IS NULL`,
    );
    expect(before.rowCount).toBe(1);
    const initial = before.rows[0]!;
    expect(initial.session_token_hash.includes(Buffer.from(sessionCookie!.value))).toBe(false);
    expect(initial.access_token_ciphertext.includes(Buffer.from("eyJ"))).toBe(false);
    expect(initial.refresh_token_ciphertext.includes(Buffer.from("eyJ"))).toBe(false);

    const cipher = new TokenCipher(keyId, Buffer.from(encryptionKey, "base64"));
    const claims = decodePayload(cipher.open(initial.access_token_ciphertext, "session:access"));
    const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    expect(audiences).toContain("agriinsight-api");
    expect(claims.token_use).toBe("access");

    const beforeReplay = await pool.query<{ count: string }>(
      "SELECT count(*) FROM web_auth_spike.sessions",
    );
    expect(callbackUrl).toBeDefined();
    const replay = await page.request.get(callbackUrl!, { maxRedirects: 0 });
    expect(replay.status()).toBe(400);
    const replayCount = await pool.query<{ active_count: string; total_count: string }>(
      `SELECT count(*)::text AS total_count,
              count(*) FILTER (WHERE revoked_at IS NULL)::text AS active_count
       FROM web_auth_spike.sessions`,
    );
    expect(replayCount.rows[0]?.total_count).toBe(beforeReplay.rows[0]?.count);
    expect(replayCount.rows[0]?.active_count).toBe("1");

    const nonceStart = await page.request.get(
      "/api/auth/login?returnTo=/protected",
      { maxRedirects: 0 },
    );
    expect(nonceStart.status()).toBe(302);
    const tamperedAuthorization = new URL(nonceStart.headers().location!);
    expect(tamperedAuthorization.searchParams.has("nonce")).toBe(true);
    tamperedAuthorization.searchParams.set("nonce", "tampered-signed-id-token-nonce");
    const nonceFailure = await page.goto(tamperedAuthorization.href);
    expect(nonceFailure?.status()).toBe(400);
    const nonceCount = await pool.query<{ count: string }>(
      "SELECT count(*) FROM web_auth_spike.sessions",
    );
    expect(nonceCount.rows[0]?.count).toBe(beforeReplay.rows[0]?.count);
    await page.goto("/protected");
    await expect(page.getByTestId("session-state")).toBeVisible();

    const discovery = await fetch(
      `${process.env.AUTH_SPIKE_OIDC_ISSUER}/.well-known/openid-configuration`,
    ).then((response) => response.json()) as Record<string, unknown>;
    expect(typeof discovery.revocation_endpoint).toBe("string");
    expect(typeof discovery.end_session_endpoint).toBe("string");

    const secondPage = await context.newPage();
    await secondPage.goto("/protected");
    await expect(secondPage.getByTestId("session-state")).toBeVisible();
    await pool.query(
      "UPDATE web_auth_spike.sessions SET access_token_expires_at = now() - interval '1 second' WHERE revoked_at IS NULL",
    );
    const requestBurst = async (target: typeof page) => target.evaluate(async () => {
      const responses = await Promise.all(
        Array.from({ length: 6 }, () => fetch("/api/protected", { cache: "no-store" })),
      );
      return responses.map((response) => response.status);
    });
    const statuses = (await Promise.all([
      requestBurst(page),
      requestBurst(secondPage),
    ])).flat();
    expect(statuses.every((status) => status === 200)).toBe(true);

    const rotated = await pool.query<{
      refresh_lease_id: string | null;
      refresh_token_ciphertext: Buffer;
      refresh_version: string;
      session_version: string;
    }>(
      `SELECT refresh_lease_id, refresh_token_ciphertext, refresh_version, session_version
       FROM web_auth_spike.sessions WHERE revoked_at IS NULL`,
    );
    expect(rotated.rows[0]!.refresh_lease_id).toBeNull();
    expect(rotated.rows[0]!.refresh_version).toBe("1");
    expect(rotated.rows[0]!.session_version).toBe("2");
    expect(rotated.rows[0]!.refresh_token_ciphertext.equals(initial.refresh_token_ciphertext)).toBe(false);
    await secondPage.close();

    const endSessionEndpoint = String(discovery.end_session_endpoint);
    const endSessionRequest = page.waitForRequest((request) =>
      request.url().startsWith(endSessionEndpoint),
    );
    await page.getByRole("button", { name: "Sign out locally" }).click();
    const providerLogout = new URL((await endSessionRequest).url());
    expect(providerLogout.searchParams.has("id_token_hint")).toBe(true);
    expect(providerLogout.searchParams.get("post_logout_redirect_uri"))
      .toBe("http://localhost:3100/");
    await expect(page).toHaveURL("http://localhost:3100/");
    expect((await context.cookies()).some((cookie) => cookie.name === SESSION_COOKIE_NAME)).toBe(false);
    const revoked = await pool.query<{ revoked_at: Date | null }>(
      "SELECT revoked_at FROM web_auth_spike.sessions",
    );
    expect(revoked.rows[0]?.revoked_at).not.toBeNull();
  } finally {
    await pool.end();
  }
});
