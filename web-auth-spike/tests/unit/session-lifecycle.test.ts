import assert from "node:assert/strict";
import test from "node:test";

import { AuthError } from "../../src/auth-errors.ts";
import { hashOpaqueToken } from "../../src/token-crypto.ts";
import { createSession, createTestRuntime } from "../support/test-runtime.ts";

test("concurrent tabs cause one provider refresh and one atomic rotation", async () => {
  const runtime = createTestRuntime();
  runtime.provider.tokens = {
    ...runtime.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  const { sessionToken } = await createSession(runtime);
  const sessions = await Promise.all(
    Array.from({ length: 12 }, () => runtime.auth.requireSession(sessionToken)),
  );
  assert.equal(runtime.provider.refreshCalls, 1);
  assert.equal(new Set(sessions.map((session) => session.sessionVersion)).size, 1);
  assert.equal(sessions[0]?.sessionVersion, 2);
});

test("refresh waiters tolerate provider latency longer than the old 1.5 second window", async () => {
  const runtime = createTestRuntime();
  runtime.provider.refreshDelayMs = 1800;
  runtime.provider.tokens = {
    ...runtime.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  const { sessionToken } = await createSession(runtime);

  const sessions = await Promise.all([
    runtime.auth.requireSession(sessionToken),
    runtime.auth.requireSession(sessionToken),
  ]);

  assert.equal(runtime.provider.refreshCalls, 1);
  assert.deepEqual(sessions.map((session) => session.sessionVersion), [2, 2]);
});

test("invalid_grant revokes locally and transient failure never returns expired access", async () => {
  const invalid = createTestRuntime();
  invalid.provider.tokens = {
    ...invalid.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  invalid.provider.refreshBehavior = "invalid-grant";
  const invalidSession = await createSession(invalid);
  await assert.rejects(
    invalid.auth.requireSession(invalidSession.sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
  assert.ok(invalid.store.sessions.get(hashOpaqueToken(invalidSession.sessionToken).toString("hex"))?.revokedAt);

  const transient = createTestRuntime();
  transient.provider.tokens = {
    ...transient.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  transient.provider.refreshBehavior = "transient";
  const transientSession = await createSession(transient);
  await assert.rejects(
    transient.auth.requireSession(transientSession.sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "issuer_unavailable",
  );
  assert.equal(transient.provider.refreshCalls, 1);
});

test("local logout remains successful while issuer revocation is unavailable", async () => {
  const runtime = createTestRuntime();
  runtime.provider.revokeFails = true;
  const { sessionToken } = await createSession(runtime);
  const endSession = await runtime.auth.logout(sessionToken);
  assert.equal(endSession?.pathname, "/logout");
  await assert.rejects(
    runtime.auth.requireSession(sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
});

test("issuer drift revokes the persisted session before returning authority", async () => {
  const runtime = createTestRuntime();
  const { sessionToken } = await createSession(runtime);
  const tokenHash = hashOpaqueToken(sessionToken).toString("hex");
  const stored = runtime.store.sessions.get(tokenHash)!;
  runtime.store.sessions.set(tokenHash, {
    ...stored,
    issuer: "http://localhost:58080/realms/previous",
  });

  await assert.rejects(
    runtime.auth.requireSession(sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
  assert.ok(runtime.store.sessions.get(tokenHash)?.revokedAt);
});
