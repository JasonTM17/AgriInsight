import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";

import { Pool } from "pg";

import { AuthService } from "../../src/auth-service.ts";
import { AuthError } from "../../src/auth-errors.ts";
import { PostgresSessionStore } from "../../src/postgres-session-store.ts";
import type { SessionStore } from "../../src/session-contracts.ts";
import { TokenCipher, hashOpaqueToken } from "../../src/token-crypto.ts";
import { FakeProvider } from "../support/fakes.ts";
import { testEnvironment } from "../support/test-runtime.ts";

const enabled = process.env.AUTH_SPIKE_RUN_POSTGRES_TESTS === "1" &&
  Boolean(process.env.AUTH_SPIKE_DATABASE_URL);
const skipReason = enabled ? false : "AUTH_SPIKE_RUN_POSTGRES_TESTS=1 and database URL are required";
const pool = enabled
  ? new Pool({ connectionString: process.env.AUTH_SPIKE_DATABASE_URL, max: 12 })
  : null;
const store = pool ? new PostgresSessionStore(pool) : null;

beforeEach(async () => {
  if (!pool || !store) return;
  await store.migrate();
  await pool.query("TRUNCATE web_auth_spike.sessions, web_auth_spike.preauth_requests");
});

after(async () => {
  await pool?.end();
});

function harness(sessionStore?: SessionStore) {
  if (!store) throw new Error("Integration store is unavailable");
  const selectedStore = sessionStore ?? store;
  const env = {
    ...testEnvironment(),
    databaseUrl: process.env.AUTH_SPIKE_DATABASE_URL!,
  };
  const cipher = new TokenCipher(env.keyId, env.encryptionKey);
  const provider = new FakeProvider();
  const auth = new AuthService(env, selectedStore, cipher, provider);
  return { auth, cipher, env, provider };
}

async function login(runtime: ReturnType<typeof harness>) {
  const start = await runtime.auth.beginLogin("/protected");
  const callback = new URL(runtime.env.callbackUrl);
  callback.searchParams.set("code", "fixture-code");
  callback.searchParams.set("state", runtime.provider.lastState!);
  return runtime.auth.completeCallback(callback, start.browserBinding);
}

test("PostgreSQL owns opaque state/session persistence and ciphertext only", { skip: skipReason }, async () => {
  const runtime = harness();
  const session = await login(runtime);
  const result = await pool!.query<{
    access_token_ciphertext: Buffer;
    refresh_token_ciphertext: Buffer;
    session_token_hash: Buffer;
    token_key_id: string;
  }>(
    `SELECT access_token_ciphertext, refresh_token_ciphertext,
            session_token_hash, token_key_id
     FROM web_auth_spike.sessions`,
  );
  assert.equal(result.rowCount, 1);
  const row = result.rows[0]!;
  assert.equal(row.session_token_hash.equals(hashOpaqueToken(session.sessionToken)), true);
  assert.equal(row.session_token_hash.includes(Buffer.from(session.sessionToken)), false);
  assert.equal(row.access_token_ciphertext.includes(Buffer.from("fixture-access-marker")), false);
  assert.equal(row.refresh_token_ciphertext.includes(Buffer.from("fixture-refresh-marker")), false);
  assert.equal(runtime.cipher.open(row.access_token_ciphertext, "session:access"), "fixture-access-marker");
  assert.equal(row.token_key_id, runtime.env.keyId);

  const columns = await pool!.query<{ column_name: string }>(
    `SELECT column_name FROM information_schema.columns
     WHERE table_schema = 'web_auth_spike' AND table_name = 'sessions'`,
  );
  const names = new Set(columns.rows.map((item) => item.column_name));
  assert.equal(names.has("tenant_id"), false);
  assert.equal(names.has("roles"), false);
});

test("PostgreSQL lease fences concurrent refresh and atomically replaces rotation", { skip: skipReason }, async () => {
  const runtime = harness();
  runtime.provider.tokens = {
    ...runtime.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  const session = await login(runtime);
  const before = await pool!.query<{ refresh_token_ciphertext: Buffer }>(
    "SELECT refresh_token_ciphertext FROM web_auth_spike.sessions",
  );
  const resolved = await Promise.all(
    Array.from({ length: 16 }, () => runtime.auth.requireSession(session.sessionToken)),
  );
  assert.equal(runtime.provider.refreshCalls, 1);
  assert.equal(resolved.every((item) => item.sessionVersion === 2), true);

  const afterRotation = await pool!.query<{
    refresh_lease_id: string | null;
    refresh_token_ciphertext: Buffer;
    refresh_version: string;
    session_version: string;
  }>(
    `SELECT refresh_lease_id, refresh_token_ciphertext, refresh_version, session_version
     FROM web_auth_spike.sessions`,
  );
  const row = afterRotation.rows[0]!;
  assert.equal(row.refresh_lease_id, null);
  assert.equal(row.refresh_version, "1");
  assert.equal(row.session_version, "2");
  assert.equal(row.refresh_token_ciphertext.equals(before.rows[0]!.refresh_token_ciphertext), false);
  assert.equal(runtime.cipher.open(row.refresh_token_ciphertext, "session:refresh"), "rotated-refresh-marker");
});

test("invalid_grant revokes and transient issuer errors preserve fail-closed expiry", { skip: skipReason }, async () => {
  const invalid = harness();
  invalid.provider.tokens = {
    ...invalid.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  invalid.provider.refreshBehavior = "invalid-grant";
  const invalidSession = await login(invalid);
  await assert.rejects(
    invalid.auth.requireSession(invalidSession.sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
  const revoked = await pool!.query<{ revoked_at: Date | null }>(
    "SELECT revoked_at FROM web_auth_spike.sessions",
  );
  assert.ok(revoked.rows[0]?.revoked_at);

  await pool!.query("TRUNCATE web_auth_spike.sessions, web_auth_spike.preauth_requests");
  const transient = harness();
  transient.provider.tokens = {
    ...transient.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  transient.provider.refreshBehavior = "transient";
  const transientSession = await login(transient);
  await assert.rejects(
    transient.auth.requireSession(transientSession.sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "issuer_unavailable",
  );
  const failed = await pool!.query<{
    access_token_expires_at: Date;
    refresh_lease_id: string | null;
    session_version: string;
  }>(
    "SELECT access_token_expires_at, refresh_lease_id, session_version FROM web_auth_spike.sessions",
  );
  assert.ok(failed.rows[0]!.access_token_expires_at < new Date());
  assert.equal(failed.rows[0]!.refresh_lease_id, null);
  assert.equal(failed.rows[0]!.session_version, "2");
});

test("local logout commits while issuer revocation is unavailable", { skip: skipReason }, async () => {
  const runtime = harness();
  runtime.provider.revokeFails = true;
  const session = await login(runtime);
  await runtime.auth.logout(session.sessionToken);
  const result = await pool!.query<{ revoked_at: Date | null }>(
    "SELECT revoked_at FROM web_auth_spike.sessions",
  );
  assert.ok(result.rows[0]?.revoked_at);
});

test("logout wins a refresh race and revokes the discarded rotated token", { skip: skipReason }, async () => {
  const runtime = harness();
  runtime.provider.refreshDelayMs = 250;
  runtime.provider.tokens = {
    ...runtime.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  const session = await login(runtime);
  const refreshing = runtime.auth.requireSession(session.sessionToken);
  while (runtime.provider.refreshCalls === 0) {
    await new Promise((resolve) => setTimeout(resolve, 5));
  }

  await runtime.auth.logout(session.sessionToken);
  await assert.rejects(
    refreshing,
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
  assert.equal(runtime.provider.revokedTokens.includes("fixture-refresh-marker"), true);
  assert.equal(runtime.provider.revokedTokens.includes("rotated-refresh-marker"), true);
  const result = await pool!.query<{
    refresh_version: string;
    revoked_at: Date | null;
  }>("SELECT refresh_version, revoked_at FROM web_auth_spike.sessions");
  assert.equal(result.rows[0]?.refresh_version, "0");
  assert.ok(result.rows[0]?.revoked_at);
});

test("refresh finishing first cannot hide its rotated token from logout", { skip: skipReason }, async () => {
  let allowRevoke!: () => void;
  let observeRevoke!: () => void;
  const revokeGate = new Promise<void>((resolve) => {
    allowRevoke = resolve;
  });
  const revokeStarted = new Promise<void>((resolve) => {
    observeRevoke = resolve;
  });
  const delayedRevokeStore = new Proxy(store!, {
    get(target, property, receiver) {
      if (property === "revokeSession") {
        return async (sessionTokenHash: Buffer, now: Date) => {
          observeRevoke();
          await revokeGate;
          return target.revokeSession(sessionTokenHash, now);
        };
      }
      const value = Reflect.get(target, property, receiver);
      return typeof value === "function" ? value.bind(target) : value;
    },
  }) as SessionStore;
  const runtime = harness(delayedRevokeStore);
  runtime.provider.refreshDelayMs = 100;
  runtime.provider.tokens = {
    ...runtime.provider.tokens,
    accessTokenExpiresAt: new Date(Date.now() - 1000),
  };
  const session = await login(runtime);
  const refreshing = runtime.auth.requireSession(session.sessionToken);
  while (runtime.provider.refreshCalls === 0) {
    await new Promise((resolve) => setTimeout(resolve, 5));
  }

  const loggingOut = runtime.auth.logout(session.sessionToken);
  await revokeStarted;
  const refreshed = await refreshing;
  assert.equal(refreshed.sessionVersion, 2);
  allowRevoke();
  await loggingOut;

  assert.equal(runtime.provider.revokedTokens.includes("rotated-refresh-marker"), true);
  const result = await pool!.query<{
    refresh_version: string;
    revoked_at: Date | null;
  }>("SELECT refresh_version, revoked_at FROM web_auth_spike.sessions");
  assert.equal(result.rows[0]?.refresh_version, "1");
  assert.ok(result.rows[0]?.revoked_at);
});

test("issuer configuration drift revokes persisted PostgreSQL sessions", { skip: skipReason }, async () => {
  const runtime = harness();
  const session = await login(runtime);
  await pool!.query(
    "UPDATE web_auth_spike.sessions SET provider_issuer = 'http://localhost:58080/realms/previous'",
  );

  await assert.rejects(
    runtime.auth.requireSession(session.sessionToken),
    (error: unknown) => error instanceof AuthError && error.code === "invalid_session",
  );
  const result = await pool!.query<{ revoked_at: Date | null }>(
    "SELECT revoked_at FROM web_auth_spike.sessions",
  );
  assert.ok(result.rows[0]?.revoked_at);
});
