import assert from "node:assert/strict";
import test from "node:test";

import { AuthError } from "../../src/auth-errors.ts";
import { createSession, createTestRuntime } from "../support/test-runtime.ts";

test("callback rejects missing state, code, or browser binding", async () => {
  const runtime = createTestRuntime();
  await assert.rejects(
    runtime.auth.completeCallback(runtime.env.callbackUrl, undefined),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_request",
  );
});

test("state mismatch and expired state are rejected", async () => {
  const now = new Date("2026-07-23T00:00:00Z");
  const runtime = createTestRuntime();
  const login = await runtime.auth.beginLogin("/protected", now);
  const mismatch = new URL(runtime.env.callbackUrl);
  mismatch.searchParams.set("code", "fixture-code");
  mismatch.searchParams.set("state", "wrong-state");
  await assert.rejects(
    runtime.auth.completeCallback(mismatch, login.browserBinding, now),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_state",
  );

  const actual = new URL(runtime.env.callbackUrl);
  actual.searchParams.set("code", "fixture-code");
  actual.searchParams.set("state", runtime.provider.lastState!);
  await assert.rejects(
    runtime.auth.completeCallback(actual, login.browserBinding, new Date(now.getTime() + 301_000)),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_state",
  );
});

test("state is one use and nonce failure creates no session", async () => {
  const runtime = createTestRuntime();
  const created = await createSession(runtime);
  await assert.rejects(
    runtime.auth.completeCallback(created.callback, created.login.browserBinding),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_state",
  );

  const nonceRuntime = createTestRuntime();
  nonceRuntime.provider.exchangeFailure = "nonce";
  const login = await nonceRuntime.auth.beginLogin();
  const callback = new URL(nonceRuntime.env.callbackUrl);
  callback.searchParams.set("code", "fixture-code");
  callback.searchParams.set("state", nonceRuntime.provider.lastState!);
  await assert.rejects(
    nonceRuntime.auth.completeCallback(callback, login.browserBinding),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_nonce",
  );
  assert.equal(nonceRuntime.store.sessions.size, 0);
});

test("return path is allowlisted rather than accepted as an open redirect", async () => {
  const runtime = createTestRuntime();
  const login = await runtime.auth.beginLogin("https://attacker.example/collect");
  const callback = new URL(runtime.env.callbackUrl);
  callback.searchParams.set("code", "fixture-code");
  callback.searchParams.set("state", runtime.provider.lastState!);
  const result = await runtime.auth.completeCallback(callback, login.browserBinding);
  assert.equal(result.returnPath, "/protected");
});
