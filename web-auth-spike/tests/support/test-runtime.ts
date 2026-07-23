import { AuthService } from "../../src/auth-service.ts";
import type { AuthEnvironment } from "../../src/env.ts";
import { TokenCipher } from "../../src/token-crypto.ts";
import { FakeProvider, InMemorySessionStore } from "./fakes.ts";

export const TEST_KEY = Buffer.alloc(32, 7);

export function testEnvironment(): AuthEnvironment {
  return {
    allowedHost: "localhost:3100",
    baseUrl: new URL("http://localhost:3100"),
    callbackUrl: new URL("http://localhost:3100/api/auth/callback/keycloak"),
    clientId: "agriinsight-web",
    clientSecret: "placeholder-only",
    databaseUrl: "postgresql://placeholder",
    encryptionKey: TEST_KEY,
    issuer: new URL("http://localhost:58080/realms/agriinsight-demo"),
    keyId: "test-key-2026-07",
    sessionLifetimeSeconds: 3600,
  };
}

export function createTestRuntime() {
  const env = testEnvironment();
  const store = new InMemorySessionStore();
  const provider = new FakeProvider();
  const cipher = new TokenCipher(env.keyId, env.encryptionKey);
  return {
    auth: new AuthService(env, store, cipher, provider),
    cipher,
    env,
    provider,
    store,
  };
}

export async function createSession(
  runtime: ReturnType<typeof createTestRuntime>,
  now = new Date(),
) {
  const login = await runtime.auth.beginLogin("/protected", now);
  const callback = new URL(runtime.env.callbackUrl);
  callback.searchParams.set("code", "fixture-code");
  callback.searchParams.set("state", runtime.provider.lastState!);
  const result = await runtime.auth.completeCallback(callback, login.browserBinding, now);
  return { ...result, callback, login };
}
