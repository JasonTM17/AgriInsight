import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { NextRequest } from "next/server.js";

import {
  SECURE_COOKIE_OPTIONS,
  SESSION_COOKIE_NAME,
} from "../../src/cookie-policy.ts";
import { assertTrustedRequest } from "../../src/env.ts";
import { proxy } from "../../src/proxy.ts";
import { allowlistedReturnPath } from "../../src/request-policy.ts";
import { TokenCipher, randomOpaqueToken } from "../../src/token-crypto.ts";
import { testEnvironment, TEST_KEY } from "../support/test-runtime.ts";

test("cookie contract is opaque __Host-, secure, HttpOnly, SameSite Lax", () => {
  assert.match(SESSION_COOKIE_NAME, /^__Host-/);
  assert.deepEqual(SECURE_COOKIE_OPTIONS, {
    httpOnly: true,
    path: "/",
    sameSite: "lax",
    secure: true,
  });
  assert.ok(randomOpaqueToken().length >= 43);
});

test("AES-256-GCM envelope does not contain plaintext markers", () => {
  const cipher = new TokenCipher("test-key-2026-07", TEST_KEY);
  const marker = "fixture-provider-token-plaintext-marker";
  const encrypted = cipher.seal(marker, "session:access");
  assert.equal(encrypted.includes(Buffer.from(marker)), false);
  assert.equal(cipher.open(encrypted, "session:access"), marker);
  assert.throws(() => cipher.open(encrypted, "session:refresh"));
});

test("host/callback boundary ignores forwarded metadata and rejects direct-host spoofing", () => {
  const env = testEnvironment();
  assert.throws(
    () => assertTrustedRequest(new Request("http://evil.example/api/auth/session", { headers: { host: "evil.example" } }), env),
  );
  assert.doesNotThrow(() => assertTrustedRequest(
    new Request("http://localhost:3100/api/auth/session", {
      headers: { host: "localhost:3100", "x-forwarded-host": "evil.example" },
    }),
    env,
  ));
  assert.throws(() => assertTrustedRequest(
    new Request("http://evil.example/api/auth/session", {
      headers: { host: "evil.example", "x-forwarded-host": "localhost:3100" },
    }),
    env,
  ));
  assert.equal(allowlistedReturnPath("//evil.example"), "/protected");
  assert.equal(allowlistedReturnPath("/protected?tab=session"), "/protected?tab=session");
});

test("proxy redirects optimistically but rejects untrusted host metadata", () => {
  const missing = proxy(new NextRequest("http://localhost:3100/protected", {
    headers: { host: "localhost:3100" },
  }));
  assert.equal(missing.status, 307);
  assert.equal(new URL(missing.headers.get("location")!).pathname, "/api/auth/login");

  const present = proxy(new NextRequest("http://localhost:3100/protected", {
    headers: { cookie: `${SESSION_COOKIE_NAME}=opaque`, host: "localhost:3100" },
  }));
  assert.equal(present.headers.get("x-middleware-next"), "1");

  const forwarded = proxy(new NextRequest("http://localhost:3100/protected", {
    headers: {
      cookie: `${SESSION_COOKIE_NAME}=opaque`,
      host: "localhost:3100",
      "x-forwarded-host": "evil.example",
    },
  }));
  assert.equal(forwarded.headers.get("x-middleware-next"), "1");
});

test("rendered application sources contain no browser token persistence", async () => {
  const files = [
    "src/app/page.tsx",
    "src/app/protected/page.tsx",
    "src/app/api/protected/route.ts",
  ];
  const content = (await Promise.all(files.map((file) => readFile(file, "utf8")))).join("\n");
  assert.doesNotMatch(content, /localStorage|sessionStorage|accessToken|refreshToken|Bearer\s/i);
});
