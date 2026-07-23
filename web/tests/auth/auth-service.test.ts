import { describe, expect, it } from "vitest";

import { AuthService } from "@/server/auth/auth-service";
import { TokenCipher } from "@/server/auth/token-crypto";
import type { WebEnvironment } from "@/server/config/environment";
import {
  FakeProvider,
  MemorySessionStore
} from "../support/auth-fakes";

const now = new Date("2026-07-23T10:00:00Z");
const env = {
  baseUrl: new URL("https://localhost:3100"),
  callbackUrl: new URL("https://localhost:3100/api/auth/callback"),
  issuer: new URL("https://issuer.example/"),
  keyId: "test-v1",
  sessionLifetimeSeconds: 28_800
} as WebEnvironment;

describe("OIDC auth service", () => {
  it("consumes state once and stores only encrypted provider tokens", async () => {
    const store = new MemorySessionStore();
    const provider = new FakeProvider();
    const auth = new AuthService(
      env,
      store,
      new TokenCipher("test-v1", Buffer.alloc(32, 3)),
      provider
    );
    const login = await auth.beginLogin("/inventory", now);
    const callback = new URL("https://localhost:3100/api/auth/callback");
    callback.searchParams.set("code", "authorization-code");
    callback.searchParams.set(
      "state",
      login.redirectUrl.searchParams.get("state") ?? ""
    );
    const result = await auth.completeCallback(
      callback,
      login.browserBinding,
      now
    );

    expect(result.returnPath).toBe("/inventory");
    expect(store.session?.accessTokenCiphertext.toString("utf8")).not.toContain(
      "provider-access-secret"
    );
    expect(store.session?.refreshTokenCiphertext?.toString("utf8")).not.toContain(
      "provider-refresh-secret"
    );
    await expect(
      auth.completeCallback(callback, login.browserBinding, now)
    ).rejects.toMatchObject({ code: "invalid_state", status: 400 });
  });

  it("rejects a callback that is not bound to the login browser", async () => {
    const auth = new AuthService(
      env,
      new MemorySessionStore(),
      new TokenCipher("test-v1", Buffer.alloc(32, 3)),
      new FakeProvider()
    );
    const login = await auth.beginLogin("/", now);
    const callback = new URL("https://localhost:3100/api/auth/callback");
    callback.searchParams.set("code", "authorization-code");
    callback.searchParams.set(
      "state",
      login.redirectUrl.searchParams.get("state") ?? ""
    );
    await expect(
      auth.completeCallback(callback, "wrong-browser-binding", now)
    ).rejects.toMatchObject({ code: "invalid_state" });
  });
});
