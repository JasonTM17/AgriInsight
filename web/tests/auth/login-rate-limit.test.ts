import { describe, expect, it } from "vitest";

import { LoginRateLimiter } from "@/server/auth/login-rate-limit";
import type { WebEnvironment } from "@/server/config/environment";

describe("login rate limiter", () => {
  it("rejects a client before it can create unbounded pre-auth rows", () => {
    const limiter = new LoginRateLimiter();
    const env = { trustForwardedHeaders: false } as WebEnvironment;
    const request = new Request("https://app.example/api/auth/login");
    for (let index = 0; index < 20; index += 1) {
      limiter.assertAllowed(request, env, 1_000);
    }
    expect(() => limiter.assertAllowed(request, env, 1_000)).toThrow(
      expect.objectContaining({ code: "rate_limited", status: 429 })
    );
    expect(() => limiter.assertAllowed(request, env, 61_001)).not.toThrow();
  });

  it("uses only the attested proxy client address as a rate key", () => {
    const limiter = new LoginRateLimiter();
    const env = { trustForwardedHeaders: true } as WebEnvironment;
    const requestFor = (address: string) =>
      new Request("https://app.example/api/auth/login", {
        headers: { "X-Forwarded-For": `${address}, 10.0.0.1` }
      });
    for (let index = 0; index < 20; index += 1) {
      limiter.assertAllowed(requestFor("203.0.113.10"), env, 2_000);
    }
    expect(() =>
      limiter.assertAllowed(requestFor("203.0.113.10"), env, 2_000)
    ).toThrow();
    expect(() =>
      limiter.assertAllowed(requestFor("203.0.113.11"), env, 2_000)
    ).not.toThrow();
  });
});
