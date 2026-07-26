import { createDecipheriv, createHash } from "node:crypto";

import { expect, test } from "@playwright/test";
import { Pool } from "pg";

const SESSION_COOKIE_NAME = "__Host-agriinsight-session";
const CSRF_COOKIE_NAME = "__Host-agriinsight-csrf";
const EXECUTIVE_SUBJECT = "10000000-0000-4000-8000-000000000002";

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Real platform E2E requires ${name}`);
  return value;
}

function openToken(
  envelope: Buffer,
  keyId: string,
  key: Buffer,
  purpose: string
): string {
  const tagStart = 13;
  const ciphertextStart = 29;
  if (envelope.length <= ciphertextStart || envelope[0] !== 1) {
    throw new Error("Invalid encrypted token envelope");
  }
  const decipher = createDecipheriv(
    "aes-256-gcm",
    key,
    envelope.subarray(1, tagStart)
  );
  decipher.setAAD(Buffer.from(`${keyId}:${purpose}`, "utf8"));
  decipher.setAuthTag(envelope.subarray(tagStart, ciphertextStart));
  return Buffer.concat([
    decipher.update(envelope.subarray(ciphertextStart)),
    decipher.final()
  ]).toString("utf8");
}

function decodeJwtPayload(token: string): Record<string, unknown> {
  const payload = token.split(".")[1];
  if (!payload) throw new Error("Access token is not a JWT");
  return JSON.parse(
    Buffer.from(payload, "base64url").toString("utf8")
  ) as Record<string, unknown>;
}

test("real Keycloak, Spring /me and PostgreSQL keep browser auth opaque", async ({
  context,
  page
}) => {
  const username = required("AGRIINSIGHT_WEB_E2E_USERNAME");
  const password = required("AGRIINSIGHT_WEB_E2E_PASSWORD");
  const databaseUrl = required("AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL");
  const encryptionKey = Buffer.from(
    required("AGRIINSIGHT_WEB_SESSION_ENCRYPTION_KEY_BASE64"),
    "base64"
  );
  const keyId = required("AGRIINSIGHT_WEB_TOKEN_KEY_ID");
  const pool = new Pool({ connectionString: databaseUrl, max: 1 });
  let callbackUrl: string | undefined;

  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/auth/callback") {
      callbackUrl = request.url();
    }
  });

  try {
    await page.goto("/api/auth/login?returnTo=/protected");
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(password);
    await page.locator("#kc-login").click();
    await expect(page).toHaveURL("http://localhost:3100/protected");
    await expect(
      page.getByRole("heading", { name: "Chào Demo Executive" })
    ).toBeVisible();
    await expect(page.getByText("AGRIINSIGHT_DEMO")).toBeVisible();

    const cookies = await context.cookies();
    const sessionCookie = cookies.find(
      (cookie) => cookie.name === SESSION_COOKIE_NAME
    );
    expect(sessionCookie).toBeDefined();
    expect(sessionCookie?.httpOnly).toBe(true);
    expect(sessionCookie?.secure).toBe(true);
    expect(sessionCookie?.sameSite).toBe("Lax");
    expect(sessionCookie?.path).toBe("/");
    const sessionTokenHash = createHash("sha256")
      .update(sessionCookie!.value, "utf8")
      .digest();
    expect(
      cookies.some((cookie) => /access|refresh|bearer/i.test(cookie.name))
    ).toBe(false);

    const browserStorage = await page.evaluate(async () => ({
      cache: await caches.keys(),
      indexedDb: (await indexedDB.databases()).map((database) => database.name),
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage)
    }));
    expect(browserStorage).toEqual({
      cache: [],
      indexedDb: [],
      local: [],
      session: []
    });
    expect(await page.content()).not.toMatch(
      /access_token|refresh_token|Bearer\s|eyJ[a-zA-Z0-9_-]+\./i
    );

    const identityResponse = await page.request.get("/api/auth/session");
    expect(identityResponse.status()).toBe(200);
    const identityText = await identityResponse.text();
    expect(identityText).toContain("Demo Executive");
    expect(identityText).not.toMatch(
      /access_token|refresh_token|Bearer\s|eyJ[a-zA-Z0-9_-]+\./i
    );

    const before = await pool.query<{
      access_token_ciphertext: Buffer;
      refresh_token_ciphertext: Buffer;
      refresh_version: string;
      session_token_hash: Buffer;
      session_version: string;
    }>(
      `SELECT access_token_ciphertext, refresh_token_ciphertext,
              refresh_version, session_token_hash, session_version
       FROM agriinsight_web.sessions
       WHERE session_token_hash = $1 AND revoked_at IS NULL`,
      [sessionTokenHash]
    );
    expect(before.rowCount).toBe(1);
    const initial = before.rows[0]!;
    expect(
      initial.session_token_hash.includes(Buffer.from(sessionCookie!.value))
    ).toBe(false);
    expect(initial.access_token_ciphertext.includes(Buffer.from("eyJ"))).toBe(false);
    expect(initial.refresh_token_ciphertext.includes(Buffer.from("eyJ"))).toBe(false);

    const accessToken = openToken(
      initial.access_token_ciphertext,
      keyId,
      encryptionKey,
      `session:${initial.session_token_hash.toString("base64url")}:access`
    );
    const claims = decodeJwtPayload(accessToken);
    const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    expect(audiences).toContain("agriinsight-api");
    expect(claims.token_use).toBe("access");

    expect(callbackUrl).toBeDefined();
    const replay = await page.request.get(callbackUrl!, { maxRedirects: 0 });
    expect(replay.status()).toBe(400);
    const afterReplay = await pool.query<{ active_count: string; total_count: string }>(
      `SELECT count(*)::text AS total_count,
              count(*) FILTER (WHERE revoked_at IS NULL)::text AS active_count
       FROM agriinsight_web.sessions`
    );
    expect(afterReplay.rows[0]).toEqual({
      active_count: "1",
      total_count: "1"
    });

    const nonceStart = await page.request.get(
      "/api/auth/login?returnTo=/protected",
      { maxRedirects: 0 }
    );
    expect(nonceStart.status()).toBe(302);
    const tamperedAuthorization = new URL(nonceStart.headers().location!);
    expect(tamperedAuthorization.searchParams.has("nonce")).toBe(true);
    tamperedAuthorization.searchParams.set(
      "nonce",
      "tampered-signed-id-token-nonce"
    );
    const nonceFailure = await page.goto(tamperedAuthorization.href);
    expect(nonceFailure?.status()).toBe(400);
    await page.goto("/protected");
    await expect(
      page.getByRole("heading", { name: "Chào Demo Executive" })
    ).toBeVisible();

    const secondPage = await context.newPage();
    await secondPage.goto("/protected");
    await pool.query(
      `UPDATE agriinsight_web.sessions
       SET access_token_expires_at = now() - interval '1 second'
       WHERE session_token_hash = $1 AND revoked_at IS NULL`,
      [sessionTokenHash]
    );
    const requestBurst = (target: typeof page) =>
      target.evaluate(async () => {
        const responses = await Promise.all(
          Array.from({ length: 6 }, () =>
            fetch("/api/auth/session", { cache: "no-store" })
          )
        );
        return responses.map((response) => response.status);
      });
    const statuses = (
      await Promise.all([requestBurst(page), requestBurst(secondPage)])
    ).flat();
    expect(statuses.every((status) => status === 200)).toBe(true);
    await secondPage.close();

    const rotated = await pool.query<{
      refresh_lease_id: string | null;
      refresh_token_ciphertext: Buffer;
      refresh_version: string;
      session_version: string;
    }>(
      `SELECT refresh_lease_id, refresh_token_ciphertext,
              refresh_version, session_version
       FROM agriinsight_web.sessions
       WHERE session_token_hash = $1 AND revoked_at IS NULL`,
      [sessionTokenHash]
    );
    expect(rotated.rows[0]?.refresh_lease_id).toBeNull();
    expect(rotated.rows[0]?.refresh_version).toBe("1");
    expect(rotated.rows[0]?.session_version).toBe("2");
    expect(
      rotated.rows[0]?.refresh_token_ciphertext.equals(
        initial.refresh_token_ciphertext
      )
    ).toBe(false);

    await pool.query(
      `UPDATE external_identities
       SET active = false, version = version + 1, updated_at = now()
       WHERE subject = $1`,
      [EXECUTIVE_SUBJECT]
    );
    const denied = await page.request.get("/api/auth/session");
    expect([401, 403]).toContain(denied.status());
    await pool.query(
      `UPDATE external_identities
       SET active = true, version = version + 1, updated_at = now()
       WHERE subject = $1`,
      [EXECUTIVE_SUBJECT]
    );
    const restored = await page.request.get("/api/auth/session");
    expect(restored.status()).toBe(200);

    const csrfCookie = (await context.cookies()).find(
      (cookie) => cookie.name === CSRF_COOKIE_NAME
    );
    expect(csrfCookie).toBeDefined();
    const logout = await page.request.post("/api/auth/logout", {
      headers: {
        Origin: "http://localhost:3100",
        "x-agriinsight-csrf": csrfCookie!.value
      },
      maxRedirects: 0
    });
    expect(logout.status()).toBe(303);
    const providerLogout = new URL(logout.headers().location!);
    expect(providerLogout.searchParams.has("id_token_hint")).toBe(true);
    expect(providerLogout.searchParams.get("post_logout_redirect_uri")).toBe(
      "http://localhost:3100/"
    );
    expect(
      (await context.cookies()).some(
        (cookie) => cookie.name === SESSION_COOKIE_NAME
      )
    ).toBe(false);
    const revoked = await pool.query<{ revoked_at: Date | null }>(
      `SELECT revoked_at FROM agriinsight_web.sessions
       WHERE session_token_hash = $1`,
      [sessionTokenHash]
    );
    expect(revoked.rows[0]?.revoked_at).not.toBeNull();
  } finally {
    await pool.query(
      `UPDATE external_identities
       SET active = true, updated_at = now()
       WHERE subject = $1`,
      [EXECUTIVE_SUBJECT]
    ).catch(() => undefined);
    await pool.end();
  }
});
